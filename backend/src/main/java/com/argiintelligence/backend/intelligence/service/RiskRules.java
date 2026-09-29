package com.argiintelligence.backend.intelligence.service;

import com.argiintelligence.backend.common.api.DataClassification;
import com.argiintelligence.backend.common.api.RiskLevel;
import com.argiintelligence.backend.farm.dto.FarmResponse;
import com.argiintelligence.backend.intelligence.dto.RiskAssessmentResponse.Factor;
import com.argiintelligence.backend.intelligence.dto.RiskAssessmentResponse.Risk;
import com.argiintelligence.backend.intelligence.dto.SupplyEstimateResponse;
import com.argiintelligence.backend.reference.entity.CropRequirement;
import com.argiintelligence.backend.weather.dto.WeatherResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Production-risk rule set {@code risk-rules-v1} (MASTER_SPEC §11.1). Pure functions: each factor is either
 * assessed from real inputs or reported unavailable with the reason. Thresholds are the cited IMD categories,
 * the FAO EcoCrop limits from {@code crop_requirement}, or declared product rules. Nothing is estimated.
 */
public final class RiskRules {

    public static final String RULE_SET = "risk-rules-v1";
    static final String PRODUCT_RULE = "product rule " + RULE_SET;
    static final int WEATHER_DAYS = 7;

    // IMD rainfall categories (mm/day): heavy 64.5-115.5, very heavy 115.6-204.4, extremely heavy >= 204.5.
    static final double IMD_HEAVY_MM = 64.5;
    static final double IMD_VERY_HEAVY_MM = 115.6;

    private RiskRules() {
    }

    /** An assessed factor, or the reason it could not be assessed. */
    public record Outcome(Factor factor, String unavailableCode, String unavailableReason) {

        static Outcome of(Factor factor) {
            return new Outcome(factor, null, null);
        }

        static Outcome unavailable(String code, String reason) {
            return new Outcome(null, code, reason);
        }

        public RiskLevel level() {
            return factor == null ? RiskLevel.UNAVAILABLE : factor.level();
        }
    }

    /** Soil pH against the crop's EcoCrop range, as §10.1 soilCompatibility and the SOIL_PH factor share it. */
    public enum PhFit { OPTIMAL, TOLERABLE, OUTSIDE_ABSOLUTE_RANGE, UNAVAILABLE }

    public static PhFit phFit(FarmResponse.Soil soil, CropRequirement req) {
        if (soil == null || soil.ph() == null || req == null || !req.hasPhLimits()) {
            return PhFit.UNAVAILABLE;
        }
        BigDecimal ph = soil.ph();
        if (ph.compareTo(req.getPhOptMin()) >= 0 && ph.compareTo(req.getPhOptMax()) <= 0) {
            return PhFit.OPTIMAL;
        }
        if (ph.compareTo(req.getPhAbsMin()) >= 0 && ph.compareTo(req.getPhAbsMax()) <= 0) {
            return PhFit.TOLERABLE;
        }
        return PhFit.OUTSIDE_ABSOLUTE_RANGE;
    }

    static Outcome heavyRainfall(WeatherResponse weather, String weatherError) {
        if (weather == null) {
            return Outcome.unavailable("HEAVY_RAINFALL", "Weather forecast unavailable (" + weatherError + ").");
        }
        List<WeatherResponse.Daily> days = firstDays(weather);
        Double max = days.stream().map(WeatherResponse.Daily::precipitationMm).filter(Objects::nonNull)
                .max(Double::compare).orElse(null);
        if (max == null) {
            return Outcome.unavailable("HEAVY_RAINFALL", "The forecast reports no daily precipitation.");
        }
        RiskLevel level = max < IMD_HEAVY_MM ? RiskLevel.LOW
                : max < IMD_VERY_HEAVY_MM ? RiskLevel.HIGH : RiskLevel.CRITICAL;
        String category = max < IMD_HEAVY_MM ? "below IMD \"heavy\""
                : max < IMD_VERY_HEAVY_MM ? "IMD \"heavy\"" : max < 204.5 ? "IMD \"very heavy\"" : "IMD \"extremely heavy\"";
        return Outcome.of(new Factor("HEAVY_RAINFALL", level, max, "mm/day",
                "IMD rainfall categories: heavy >= 64.5 mm/day (HIGH), very heavy >= 115.6 mm/day (CRITICAL)",
                weather.dailyProvenance().source(), weather.dailyProvenance().dataClassification(), period(days),
                "Maximum forecast daily precipitation in the next " + days.size() + " days is " + max
                        + " mm (" + category + ")."));
    }

    static Outcome temperatureStress(WeatherResponse weather, String weatherError, CropRequirement req,
                                     String cropLabel) {
        if (req == null || !req.hasTemperatureLimits()) {
            return Outcome.unavailable("TEMPERATURE_STRESS",
                    "FAO EcoCrop temperature limits for " + cropLabel + " are not available yet.");
        }
        if (weather == null) {
            return Outcome.unavailable("TEMPERATURE_STRESS", "Weather forecast unavailable (" + weatherError + ").");
        }
        List<WeatherResponse.Daily> days = firstDays(weather);
        int assessedDays = 0;
        int outsideAbsolute = 0;
        boolean outsideOptimal = false;
        for (WeatherResponse.Daily d : days) {
            Double min = d.minTemperatureC();
            Double max = d.maxTemperatureC();
            if (min == null && max == null) {
                continue;
            }
            assessedDays++;
            if (below(min, req.getTempAbsMinC()) || above(max, req.getTempAbsMaxC())) {
                outsideAbsolute++;
            } else if (below(min, req.getTempOptMinC()) || above(max, req.getTempOptMaxC())) {
                outsideOptimal = true;
            }
        }
        if (assessedDays == 0) {
            return Outcome.unavailable("TEMPERATURE_STRESS", "The forecast reports no daily temperatures.");
        }
        RiskLevel level = outsideAbsolute >= 3 ? RiskLevel.CRITICAL
                : outsideAbsolute >= 1 ? RiskLevel.HIGH : outsideOptimal ? RiskLevel.MODERATE : RiskLevel.LOW;
        return Outcome.of(new Factor("TEMPERATURE_STRESS", level, (double) outsideAbsolute, "days",
                "FAO EcoCrop " + cropLabel + ": optimal " + req.getTempOptMinC() + "–" + req.getTempOptMaxC()
                        + " °C, absolute " + req.getTempAbsMinC() + "–" + req.getTempAbsMaxC()
                        + " °C; outside absolute on 1–2 days HIGH, on 3+ days CRITICAL",
                weather.dailyProvenance().source(), weather.dailyProvenance().dataClassification(), period(days),
                outsideAbsolute + " of " + assessedDays + " forecast days fall outside " + cropLabel
                        + "'s absolute temperature range" + (level == RiskLevel.MODERATE
                        ? "; some fall outside its optimal range." : ".")));
    }

    static Outcome yieldVariability(SupplyEstimateResponse supply, String supplyError) {
        if (supply == null) {
            return Outcome.unavailable("YIELD_VARIABILITY", "Production history unavailable (" + supplyError + ").");
        }
        Double cv = supply.historicalYieldStats().coefficientOfVariation();
        if (cv == null) {
            return Outcome.unavailable("YIELD_VARIABILITY", "The yield coefficient of variation is not available.");
        }
        RiskLevel level = cv < 0.15 ? RiskLevel.LOW : cv <= 0.30 ? RiskLevel.MODERATE : RiskLevel.HIGH;
        return Outcome.of(new Factor("YIELD_VARIABILITY", level, cv, "ratio",
                PRODUCT_RULE + ": CV < 0.15 LOW, 0.15–0.30 MODERATE, > 0.30 HIGH",
                supply.historyProvenance().source(), DataClassification.OBSERVED, reportedPeriod(supply),
                "The district's reported yield varies with a coefficient of variation of " + cv + " over "
                        + supply.historicalYieldStats().yearsObserved() + " years."));
    }

    static Outcome downsideFrequency(SupplyEstimateResponse supply, String supplyError) {
        if (supply == null) {
            return Outcome.unavailable("DOWNSIDE_FREQUENCY", "Production history unavailable (" + supplyError + ").");
        }
        Double share = supply.historicalYieldStats().downsideYearShare();
        if (share == null) {
            return Outcome.unavailable("DOWNSIDE_FREQUENCY", "The downside-year share is not available.");
        }
        RiskLevel level = share < 0.2 ? RiskLevel.LOW : share <= 0.4 ? RiskLevel.MODERATE : RiskLevel.HIGH;
        return Outcome.of(new Factor("DOWNSIDE_FREQUENCY", level, share, "share of years",
                PRODUCT_RULE + ": share < 0.2 LOW, 0.2–0.4 MODERATE, > 0.4 HIGH",
                supply.historyProvenance().source(), DataClassification.OBSERVED, reportedPeriod(supply),
                "Downside years (" + supply.historicalYieldStats().downsideDefinition() + ") make up " + share
                        + " of " + supply.historicalYieldStats().yearsAssessedForDownside() + " assessed years."));
    }

    static Outcome soilPh(FarmResponse.Soil soil, CropRequirement req, String cropLabel) {
        if (soil == null || soil.ph() == null) {
            return Outcome.unavailable("SOIL_PH", "The farm has no soil pH recorded.");
        }
        PhFit fit = phFit(soil, req);
        if (fit == PhFit.UNAVAILABLE) {
            return Outcome.unavailable("SOIL_PH", "FAO EcoCrop pH limits for " + cropLabel + " are not available yet.");
        }
        RiskLevel level = switch (fit) {
            case OPTIMAL -> RiskLevel.LOW;
            case TOLERABLE -> RiskLevel.MODERATE;
            default -> RiskLevel.HIGH;
        };
        return Outcome.of(new Factor("SOIL_PH", level, soil.ph().doubleValue(), "pH",
                "FAO EcoCrop " + cropLabel + ": optimal " + req.getPhOptMin() + "–" + req.getPhOptMax()
                        + ", absolute " + req.getPhAbsMin() + "–" + req.getPhAbsMax(),
                soil.source().name(), DataClassification.valueOf(soil.dataClassification().name()),
                soil.measuredAt() == null ? null : soil.measuredAt().toString(),
                "Soil pH " + soil.ph() + " is " + switch (fit) {
                    case OPTIMAL -> "within";
                    case TOLERABLE -> "outside the optimal but within the absolute";
                    default -> "outside the absolute";
                } + " range for " + cropLabel + "."));
    }

    /** Level = the worst assessed factor (§11); UNAVAILABLE when nothing could be assessed. */
    static Risk combine(List<Outcome> outcomes, List<String> extraLimitations) {
        List<Factor> factors = outcomes.stream().map(Outcome::factor).filter(Objects::nonNull).toList();
        List<String> unavailable = outcomes.stream().map(Outcome::unavailableCode).filter(Objects::nonNull).toList();
        List<String> limitations = new ArrayList<>();
        outcomes.stream().map(Outcome::unavailableReason).filter(Objects::nonNull).forEach(limitations::add);
        limitations.addAll(extraLimitations);
        RiskLevel level = factors.stream().map(Factor::level).max(Enum::compareTo).orElse(RiskLevel.UNAVAILABLE);
        return new Risk(level, factors, factors.size(), unavailable, List.copyOf(limitations));
    }

    /** Market risk until milestone M4 (§11.2): nothing assessed, never invented. */
    static Risk marketUnavailable() {
        return new Risk(RiskLevel.UNAVAILABLE, List.of(), 0, List.of("PRICE_ANOMALY", "SUPPLY_PRESSURE"),
                List.of("Market data is not connected (milestone M4)"));
    }

    private static List<WeatherResponse.Daily> firstDays(WeatherResponse weather) {
        return weather.daily().stream().limit(WEATHER_DAYS).toList();
    }

    private static String period(List<WeatherResponse.Daily> days) {
        return days.isEmpty() ? null : days.getFirst().date() + "/" + days.getLast().date();
    }

    private static String reportedPeriod(SupplyEstimateResponse supply) {
        String through = supply.historyProvenance().dataThrough();
        return through == null ? null : "reported crop years through " + through;
    }

    private static boolean below(Double value, BigDecimal limit) {
        return value != null && BigDecimal.valueOf(value).compareTo(limit) < 0;
    }

    private static boolean above(Double value, BigDecimal limit) {
        return value != null && BigDecimal.valueOf(value).compareTo(limit) > 0;
    }
}
