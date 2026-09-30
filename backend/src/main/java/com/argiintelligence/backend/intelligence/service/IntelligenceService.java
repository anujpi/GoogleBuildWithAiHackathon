package com.argiintelligence.backend.intelligence.service;

import com.argiintelligence.backend.common.exception.ApiException;
import com.argiintelligence.backend.farm.dto.FarmResponse;
import com.argiintelligence.backend.farm.entity.AreaUnit;
import com.argiintelligence.backend.farm.entity.IrrigationType;
import com.argiintelligence.backend.farm.entity.Season;
import com.argiintelligence.backend.farm.service.FarmService;
import com.argiintelligence.backend.intelligence.dto.IntelligenceReport;
import com.argiintelligence.backend.intelligence.dto.IntelligenceReport.AnomalyCheck;
import com.argiintelligence.backend.intelligence.dto.IntelligenceReport.Decision;
import com.argiintelligence.backend.intelligence.dto.IntelligenceReport.FarmContext;
import com.argiintelligence.backend.intelligence.dto.IntelligenceReport.Gap;
import com.argiintelligence.backend.intelligence.dto.IntelligenceReport.Risk;
import com.argiintelligence.backend.intelligence.dto.IntelligenceReport.RiskFactor;
import com.argiintelligence.backend.intelligence.dto.Section;
import com.argiintelligence.backend.ml.MlClient;
import com.argiintelligence.backend.supply.dto.SupplyForecastRequest;
import com.argiintelligence.backend.supply.dto.SupplyForecastResponse;
import com.argiintelligence.backend.supply.entity.ProductionHistory;
import com.argiintelligence.backend.supply.repository.ProductionHistoryRepository;
import com.argiintelligence.backend.supply.service.SupplyForecastService;
import com.argiintelligence.backend.weather.WeatherService;
import com.argiintelligence.backend.weather.WeatherSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Orchestrates one farm's intelligence report. ML predicts (suitability, supply, demand, anomaly); this class decides
 * (gap status, risk levels, the crop decision) with the documented rules below; the LLM only explains the result
 * (see AdvisoryService). Each section is computed independently so one unavailable source cannot hide the rest.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntelligenceService {

    static final String RULES_VERSION = "backend-decision-rules-v1";
    static final Map<Season, String> SEASON_LABEL = Map.of(Season.KHARIF, "Kharif", Season.RABI, "Rabi",
            Season.ZAID, "Summer", Season.OTHER, "Whole Year");
    static final BigDecimal ACRE_TO_HECTARE = new BigDecimal("0.40468564224");
    /** |gap| within this share of demand counts as balanced. */
    static final double BALANCED_BAND = 0.10;
    static final double MARKET_HIGH_BAND = 0.25;
    static final double SUITABLE_SCORE = 0.60;
    static final double NOT_RECOMMENDED_SCORE = 0.45;

    private final FarmService farmService;
    private final WeatherService weatherService;
    private final MlClient mlClient;
    private final SupplyForecastService supplyForecastService;
    private final ProductionHistoryRepository productionHistory;

    public IntelligenceReport report(UUID ownerId, UUID farmId, String requestedCrop) {
        FarmResponse farm = farmService.get(ownerId, farmId);
        FarmContext ctx = context(farm);
        List<String> notices = new ArrayList<>();

        WeatherSnapshot weather = weatherService.forecast(ctx.latitude().doubleValue(), ctx.longitude().doubleValue());

        Section<JsonNode> suitability = section(() -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("state", ctx.state());
            body.put("season", ctx.sourceSeasonLabel());
            body.put("soilPh", ctx.soilPh());
            body.put("irrigated", farm.irrigationType() == null ? null
                    : farm.irrigationType() != IrrigationType.RAIN_FED);
            body.put("topN", 10);
            return MlClient.requireFields(mlClient.postJson(MlClient.SUITABILITY_PATH, body), "candidates",
                    "provenance");
        });

        String[] focus = focusCrop(requestedCrop, farm.currentCrop(), suitability);
        String crop = focus[0];

        List<ProductionHistory> series = crop == null ? List.of()
                : productionHistory.findFullSeries(ctx.state(), crop, ctx.sourceSeasonLabel());
        Section<SupplyForecastResponse> supply = crop == null
                ? Section.unavailable("NO_FOCUS_CROP", "No crop selected and no suitability candidates available")
                : section(() -> supplyForecast(ctx, crop, series));
        if (supply.isOk()) {
            notices.add("Supply forecast is for crop year " + supply.data().cropYear()
                    + ", the latest year the historical data (1997-2019) supports; it is not a current-season "
                    + "forecast.");
        }

        Integer gapYear = supply.isOk() ? supply.data().cropYear() : null;
        Section<JsonNode> demand = crop == null || gapYear == null
                ? Section.unavailable("NO_SUPPLY_YEAR", "Demand for the gap is computed for the supply forecast year")
                : section(() -> demand(crop, ctx.state(), gapYear));
        int outlookYear = Year.now().getValue();
        Section<JsonNode> demandOutlook = crop == null
                ? Section.unavailable("NO_FOCUS_CROP", "No crop selected")
                : section(() -> demand(crop, ctx.state(), outlookYear));

        Section<Gap> gap = supply.isOk() && demand.isOk() ? Section.ok(gap(supply.data(), demand.data()))
                : Section.unavailable("GAP_INPUTS_UNAVAILABLE", "Supply and demand for the same year are both "
                + "needed to compute the gap");

        List<AnomalyCheck> anomalies = anomalies(crop, ctx, series, demandOutlook);
        Risk risk = risk(weather, suitability, crop, gap, anomalies);
        Decision decision = decide(crop, suitability, gap, risk);
        notices.add("Suitability scores are an evidence index for ranking, not probabilities.");
        notices.add("Demand is a FAOSTAT consumption proxy (national, apportioned by Census 2011 population), not "
                + "observed market demand.");
        if (!"OK".equals(weather.status())) {
            notices.add("Live weather is unavailable; weather-based risk is UNKNOWN rather than estimated.");
        }
        return new IntelligenceReport(ctx, crop, focus[1], weather, suitability, supply, demand, demandOutlook, gap,
                anomalies, risk, decision, notices, Instant.now().toString());
    }

    static FarmContext context(FarmResponse farm) {
        BigDecimal ph = farm.soilProfile() == null ? null : farm.soilProfile().ph();
        String soilClass = farm.soilProfile() == null ? null : farm.soilProfile().dataClassification().name();
        BigDecimal ha = farm.areaUnit() == AreaUnit.ACRE
                ? farm.area().multiply(ACRE_TO_HECTARE).setScale(3, RoundingMode.HALF_UP) : farm.area();
        return new FarmContext(farm.id(), farm.name(), farm.location().state(), farm.location().district(),
                farm.location().latitude(), farm.location().longitude(), farm.season().name(),
                SEASON_LABEL.get(farm.season()), farm.irrigationType() == null ? null : farm.irrigationType().name(),
                farm.currentCrop(), ph, soilClass, ha);
    }

    /** Returns {crop, reason}. */
    static String[] focusCrop(String requested, String current, Section<JsonNode> suitability) {
        if (requested != null && !requested.isBlank()) {
            return new String[]{canonical(requested.strip(), suitability), "selected by the user"};
        }
        if (current != null && !current.isBlank()) {
            return new String[]{canonical(current.strip(), suitability), "the farm's current crop"};
        }
        if (suitability.isOk() && !suitability.data().get("candidates").isEmpty()) {
            return new String[]{suitability.data().get("candidates").get(0).get("crop").asString(),
                    "highest suitability index for this state and season"};
        }
        return new String[]{null, "no crop could be selected"};
    }

    /** Uses the dataset's own spelling when the crop is among the candidates (e.g. "potato" -> "Potato"). */
    private static String canonical(String crop, Section<JsonNode> suitability) {
        if (suitability.isOk()) {
            for (JsonNode c : suitability.data().get("candidates")) {
                if (c.get("crop").asString().equalsIgnoreCase(crop)) {
                    return c.get("crop").asString();
                }
            }
        }
        return crop;
    }

    private SupplyForecastResponse supplyForecast(FarmContext ctx, String crop, List<ProductionHistory> series) {
        if (series.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "NO_HISTORY",
                    "No production history for " + crop + " in " + ctx.state() + " (" + ctx.sourceSeasonLabel()
                            + ")");
        }
        ProductionHistory last = series.getLast();
        // Assumption, labelled in the response as REQUEST_INPUT: the state sows the same area as the last observed year.
        return supplyForecastService.forecast(new SupplyForecastRequest(ctx.state(), crop, ctx.season(),
                last.getCropYear() + 1, last.getAreaHectares()));
    }

    private JsonNode demand(String crop, String state, int year) {
        Map<String, Object> body = Map.of("crop", crop, "state", state, "targetYear", year);
        return MlClient.requireFields(mlClient.postJson(MlClient.DEMAND_PATH, body), "forecast", "unit",
                "dataClassification", "provenance");
    }

    static Gap gap(SupplyForecastResponse supply, JsonNode demand) {
        BigDecimal s = supply.forecast().expectedProductionTonnes();
        BigDecimal d = demand.get("forecast").decimalValue();
        BigDecimal g = s.subtract(d);
        double pct = d.signum() == 0 ? 0 : g.doubleValue() / d.doubleValue();
        String status = Math.abs(pct) <= BALANCED_BAND ? "BALANCED" : pct > 0 ? "SURPLUS" : "DEFICIT";
        return new Gap(supply.cropYear(), s.setScale(0, RoundingMode.HALF_UP), supply.forecast().dataClassification(),
                d.setScale(0, RoundingMode.HALF_UP), demand.get("dataClassification").asString(),
                g.setScale(0, RoundingMode.HALF_UP), BigDecimal.valueOf(100 * pct).setScale(1, RoundingMode.HALF_UP),
                status,
                "gap = state supply forecast (ML) - state demand proxy (FAOSTAT x Census 2011 population share); "
                        + "balanced within +/-" + (int) (BALANCED_BAND * 100) + "% of demand",
                List.of("Supply covers the " + supply.sourceSeasonLabel() + " season only; demand is annual.",
                        "Demand assumes uniform per-capita use across states; a surplus state typically ships to "
                                + "other states, so SURPLUS/DEFICIT describes the producer/consumer balance, not "
                                + "local prices.",
                        "Both values are for " + supply.cropYear() + ", the latest year both series support."));
    }

    private List<AnomalyCheck> anomalies(String crop, FarmContext ctx, List<ProductionHistory> series,
                                         Section<JsonNode> demandOutlook) {
        List<AnomalyCheck> out = new ArrayList<>();
        if (crop != null && !series.isEmpty()) {
            List<Map<String, Object>> yields = new ArrayList<>();
            List<Map<String, Object>> production = new ArrayList<>();
            for (ProductionHistory h : series) {
                if (h.getAreaHectares().signum() > 0) {
                    yields.add(Map.of("period", String.valueOf(h.getCropYear()), "value",
                            h.getProductionTonnes().divide(h.getAreaHectares(), 4, RoundingMode.HALF_UP)));
                }
                production.add(Map.of("period", String.valueOf(h.getCropYear()), "value", h.getProductionTonnes()));
            }
            String src = "production_history (Kaggle crop_yield, OBSERVED), " + ctx.state() + " "
                    + ctx.sourceSeasonLabel();
            out.add(new AnomalyCheck("yield_t_per_ha", crop + " yield, latest year vs. history", src,
                    section(() -> anomaly("yield_t_per_ha", "t/ha", yields))));
            out.add(new AnomalyCheck("production_tonnes", crop + " production, latest year vs. history", src,
                    section(() -> anomaly("production_tonnes", "tonnes", production))));
        }
        if (demandOutlook.isOk() && demandOutlook.data().get("nationalHistory") != null) {
            List<Map<String, Object>> pts = new ArrayList<>();
            for (JsonNode p : demandOutlook.data().get("nationalHistory")) {
                pts.add(Map.of("period", String.valueOf(p.get("year").asInt()), "value", p.get("value").decimalValue()));
            }
            out.add(new AnomalyCheck("national_demand_proxy_tonnes", crop + " national apparent use, latest year",
                    "FAOSTAT Food Balance Sheets (India)", section(() -> anomaly("national_demand_proxy_tonnes",
                    "tonnes", pts))));
        }
        return out;
    }

    private JsonNode anomaly(String metric, String unit, List<Map<String, Object>> series) {
        Map<String, Object> body = Map.of("metric", metric, "unit", unit, "series", series, "window", 10);
        return MlClient.requireFields(mlClient.postJson(MlClient.ANOMALY_PATH, body), "status", "observedValue");
    }

    static Risk risk(WeatherSnapshot weather, Section<JsonNode> suitability, String crop, Section<Gap> gap,
                     List<AnomalyCheck> anomalies) {
        List<RiskFactor> f = new ArrayList<>();
        if ("OK".equals(weather.status()) && weather.next7Days() != null) {
            double rain = weather.next7Days().totalPrecipitationMm();
            Double tMax = weather.next7Days().maxTemperatureC();
            String lvl = rain > 100 ? "HIGH" : rain > 50 ? "MODERATE" : "LOW";
            f.add(new RiskFactor("WEATHER_RAINFALL", lvl, String.format(Locale.ROOT,
                    "%.0f mm rain forecast over 7 days (rule: >50 moderate, >100 high)", rain), weather.source()));
            if (tMax != null) {
                String h = tMax >= 40 ? "HIGH" : tMax >= 36 ? "MODERATE" : "LOW";
                f.add(new RiskFactor("WEATHER_HEAT", h, String.format(Locale.ROOT,
                        "max temperature %.1f °C in the next 7 days (rule: >=36 moderate, >=40 high)", tMax),
                        weather.source()));
            }
            WeatherSnapshot.Current c = weather.current();
            if (c != null && c.relativeHumidityPct() != null && c.temperatureC() != null) {
                boolean favourable = c.relativeHumidityPct() >= 80 && c.temperatureC() >= 15 && c.temperatureC() <= 30;
                f.add(new RiskFactor("DISEASE_CONDITIONS", favourable ? "MODERATE" : "LOW", String.format(Locale.ROOT,
                        "current humidity %.0f%%, %.1f °C; humid and mild conditions (>=80%%, 15-30 °C) favour many "
                                + "fungal leaf diseases (general heuristic, not a crop-specific model)",
                        c.relativeHumidityPct(), c.temperatureC()), weather.source()));
            }
        } else {
            f.add(new RiskFactor("WEATHER", "UNKNOWN", "live weather unavailable", weather.source()));
        }
        anomalies.stream().filter(a -> a.metric().equals("yield_t_per_ha") && a.result().isOk()).findFirst()
                .ifPresent(a -> {
                    JsonNode r = a.result().data();
                    boolean low = "ANOMALY".equals(r.get("status").asString())
                            && "LOW".equals(r.get("direction").asString());
                    f.add(new RiskFactor("PRODUCTION_YIELD", low ? "HIGH" : "LOW", "latest yield "
                            + r.get("status").asString().toLowerCase(Locale.ROOT) + " vs. the previous "
                            + r.get("windowUsed").asInt() + " years (robust z-score)", a.source()));
                });
        JsonNode cand = candidate(suitability, crop);
        if (cand != null) {
            for (JsonNode comp : cand.get("components")) {
                if ("stability".equals(comp.get("name").asString()) && comp.get("score").isNumber()) {
                    double s = comp.get("score").asDouble();
                    f.add(new RiskFactor("PRODUCTION_VOLATILITY", s < 0.6 ? "MODERATE" : "LOW",
                            comp.get("evidence").asString() + " (rule: stability < 0.6 moderate)",
                            "ML crop-suitability evidence"));
                }
            }
        }
        if (gap.isOk()) {
            Gap g = gap.data();
            double pct = g.gapPctOfDemand().doubleValue() / 100;
            String lvl = "SURPLUS".equals(g.status()) ? (pct > MARKET_HIGH_BAND ? "HIGH" : "MODERATE") : "LOW";
            f.add(new RiskFactor("MARKET_OVERSUPPLY", lvl, "state supply is " + g.gapPctOfDemand().abs()
                    + (g.gapPctOfDemand().signum() >= 0 ? "% above" : "% below") + " the demand proxy ("
                    + g.status() + "; rule: surplus > 25% high)",
                    "ML supply forecast + FAOSTAT demand proxy"));
        } else {
            f.add(new RiskFactor("MARKET", "UNKNOWN", "supply-demand gap unavailable", null));
        }
        String overall = f.stream().map(RiskFactor::level).filter(l -> !l.equals("UNKNOWN"))
                .max((a, b) -> Integer.compare(rank(a), rank(b))).orElse("UNKNOWN");
        return new Risk(overall, f, RULES_VERSION + ": highest level among the rule-based factors; thresholds are "
                + "documented heuristics, not calibrated probabilities");
    }

    private static int rank(String level) {
        return switch (level) {
            case "HIGH" -> 3;
            case "MODERATE" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    public static JsonNode candidate(Section<JsonNode> suitability, String crop) {
        if (!suitability.isOk() || crop == null) {
            return null;
        }
        for (JsonNode c : suitability.data().get("candidates")) {
            if (c.get("crop").asString().equalsIgnoreCase(crop)) {
                return c;
            }
        }
        return null;
    }

    /** Demand is a matching signal, never an override: an unsuitable crop is not recommended however strong demand is. */
    static Decision decide(String crop, Section<JsonNode> suitability, Section<Gap> gap, Risk risk) {
        String by = RULES_VERSION + " (Spring Boot)";
        if (crop == null) {
            return new Decision("INSUFFICIENT_EVIDENCE", "No crop could be assessed", List.of(), List.of(), by);
        }
        List<String> reasons = new ArrayList<>();
        JsonNode cand = candidate(suitability, crop);
        List<String> alternatives = new ArrayList<>();
        if (suitability.isOk()) {
            for (JsonNode c : suitability.data().get("candidates")) {
                String name = c.get("crop").asString();
                if (!name.equalsIgnoreCase(crop) && c.get("suitabilityScore").asDouble() >= SUITABLE_SCORE
                        && alternatives.size() < 3) {
                    alternatives.add(name + " (index " + c.get("suitabilityScore").asString() + ")");
                }
            }
        }
        if (cand == null) {
            reasons.add(suitability.isOk() ? crop + " has too little recent state-level history in this season to "
                    + "be scored" : "crop suitability is unavailable: " + suitability.errorMessage());
            return new Decision("INSUFFICIENT_EVIDENCE", "Not enough evidence to assess " + crop, reasons,
                    alternatives, by);
        }
        double score = cand.get("suitabilityScore").asDouble();
        reasons.add(String.format(Locale.ROOT, "suitability index %.2f (>= %.2f suitable, < %.2f not recommended)",
                score, SUITABLE_SCORE, NOT_RECOMMENDED_SCORE));
        String market = risk.factors().stream().filter(r -> r.category().startsWith("MARKET")).map(RiskFactor::level)
                .findFirst().orElse("UNKNOWN");
        if (gap.isOk()) {
            reasons.add("regional balance: " + gap.data().status() + " (supply " + gap.data().gapPctOfDemand()
                    + "% relative to the demand proxy, " + gap.data().year() + ")");
        }
        reasons.add("overall rule-based risk: " + risk.overall());
        if (score < NOT_RECOMMENDED_SCORE) {
            return new Decision("NOT_RECOMMENDED", crop + " is weakly supported by the land/history evidence here",
                    reasons, alternatives, by);
        }
        if ("HIGH".equals(market)) {
            reasons.add("demand does not override suitability: the crop stays a candidate, but compare alternatives");
            return new Decision("SUITABLE_WITH_MARKET_RISK", crop + " is suitable, but regional supply is well above "
                    + "the demand proxy", reasons, alternatives, by);
        }
        if (score >= SUITABLE_SCORE && !"HIGH".equals(risk.overall())) {
            return new Decision("SUITABLE_CANDIDATE", crop + " is a well-supported candidate for this farm", reasons,
                    alternatives, by);
        }
        return new Decision("REVIEW", crop + " is possible; review the risks before deciding", reasons, alternatives,
                by);
    }

    private static <T> Section<T> section(Supplier<T> call) {
        try {
            return Section.ok(call.get());
        } catch (ApiException ex) {
            String detail = ex.getDetails().isEmpty() ? "" : " " + ex.getDetails().stream()
                    .map(v -> v.message()).toList();
            return Section.unavailable(ex.getCode(), ex.getMessage() + detail);
        } catch (RuntimeException ex) {
            log.warn("Intelligence section failed", ex);
            return Section.unavailable("SECTION_FAILED", "This section could not be computed");
        }
    }
}
