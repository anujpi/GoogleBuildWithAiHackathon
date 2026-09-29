package com.argiintelligence.backend.intelligence.service;

import com.argiintelligence.backend.common.api.DataClassification;
import com.argiintelligence.backend.common.api.Provenance;
import com.argiintelligence.backend.common.api.RiskLevel;
import com.argiintelligence.backend.farm.dto.FarmResponse;
import com.argiintelligence.backend.intelligence.dto.CropEvidenceResponse;
import com.argiintelligence.backend.intelligence.dto.CropEvidenceResponse.Candidate;
import com.argiintelligence.backend.intelligence.dto.CropEvidenceResponse.Evidence;
import com.argiintelligence.backend.intelligence.dto.CropEvidenceResponse.Item;
import com.argiintelligence.backend.intelligence.dto.CropEvidenceResponse.ProductionEvidence;
import com.argiintelligence.backend.intelligence.dto.CropEvidenceResponse.Reason;
import com.argiintelligence.backend.intelligence.dto.CropEvidenceResponse.Tier;
import com.argiintelligence.backend.intelligence.dto.RiskAssessmentResponse.Factor;
import com.argiintelligence.backend.intelligence.dto.RiskAssessmentResponse.Risk;
import com.argiintelligence.backend.intelligence.dto.SupplyEstimateResponse;
import com.argiintelligence.backend.intelligence.service.IntelligenceInputs.SupplyFetch;
import com.argiintelligence.backend.intelligence.service.IntelligenceInputs.WeatherFetch;
import com.argiintelligence.backend.intelligence.service.RiskRules.PhFit;
import com.argiintelligence.backend.reference.entity.CropRequirement;
import com.argiintelligence.backend.reference.entity.RefCrop;
import com.argiintelligence.backend.reference.entity.RefSupplySeries;
import com.argiintelligence.backend.reference.service.ReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Crop evidence (MASTER_SPEC §10), rule set {@code crop-evidence-v1}. ML only supplies production evidence; this
 * service filters, applies the rules, groups crops into tiers, orders them and writes the reasons from fixed
 * templates. Yields of different crops are never compared, and nothing is scored into a single number.
 */
@Service
@RequiredArgsConstructor
public class CropEvidenceService {

    public static final String RANKING_RULE = "crop-evidence-v1";
    static final String WEATHER_WINDOW_NOTE = "Weather suitability reflects the next " + RiskRules.WEATHER_DAYS
            + " days of forecast, not the whole season.";
    static final String MARKET_NOTE = "Market data is not connected (milestone M4)";

    private final IntelligenceInputs inputs;
    private final ReferenceService reference;
    private final RiskService riskService;

    public CropEvidenceResponse evidence(FarmResponse farm, String explicitSeason, Integer explicitCropYear) {
        String districtId = inputs.requireInScopeDistrict(farm);
        String season = CanonicalSeason.resolve(explicitSeason, farm.season());
        WeatherFetch weather = inputs.weather(farm); // one forecast for every candidate

        List<Assessed> assessed = reference.allCrops().stream()
                .map(crop -> assess(farm, districtId, season, explicitCropYear, crop, weather)).toList();

        List<Assessed> ranked = assessed.stream().filter(a -> a.tier() != Tier.NOT_SUPPORTED)
                .sorted(ORDER).toList();
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            candidates.add(ranked.get(i).toCandidate(i + 1));
        }
        assessed.stream().filter(a -> a.tier() == Tier.NOT_SUPPORTED).forEach(a -> candidates.add(a.toCandidate(null)));
        return new CropEvidenceResponse(farm.id(), districtId, season, RANKING_RULE, List.copyOf(candidates),
                Instant.now());
    }

    /** Within a tier (§10.2): risk level lower first, fewer unavailable items first, lower yield CV (null last), id. */
    private static final Comparator<Assessed> ORDER = Comparator
            .comparing((Assessed a) -> a.tier().ordinal())
            .thenComparing(a -> a.risk().level().ordinal())
            .thenComparing(a -> a.unavailable().size())
            .thenComparing(Assessed::yieldCv, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(a -> a.crop().getCropId());

    private Assessed assess(FarmResponse farm, String districtId, String season, Integer explicitCropYear,
                            RefCrop crop, WeatherFetch weather) {
        Optional<RefSupplySeries> series = reference.series(districtId, crop.getCropId(), season);
        SupplyFetch supply = series.map(s -> {
            if (explicitCropYear == null) {
                return inputs.supply(districtId, crop.getCropId(), season, s.latestEstimableYear());
            }
            return s.isEstimable(explicitCropYear)
                    ? inputs.supply(districtId, crop.getCropId(), season, explicitCropYear)
                    : new SupplyFetch(null, "CROP_YEAR_NOT_ESTIMABLE", explicitCropYear);
        }).orElse(SupplyFetch.noSeries("NO_SERIES_FOR_SEASON"));

        CropRequirement req = reference.requirement(crop.getCropId()).orElse(null);
        Risk risk = riskService.productionRisk(farm, crop, weather, supply);
        PhFit phFit = RiskRules.phFit(farm.soilProfile(), req);
        String weatherStatus = weatherSuitability(risk);

        List<String> unavailable = new ArrayList<>();
        if (phFit == PhFit.UNAVAILABLE) {
            unavailable.add("SOIL_COMPATIBILITY");
        }
        if ("UNAVAILABLE".equals(weatherStatus)) {
            unavailable.add("WEATHER_SUITABILITY");
        }
        if (supply.response() == null) {
            unavailable.add("PRODUCTION_EVIDENCE");
        }
        unavailable.add("MARKET_CONTEXT");

        Tier tier = tier(series.isPresent(), phFit, weatherStatus, risk.level());
        Evidence evidence = new Evidence(series.isPresent(), soilItem(farm, req, phFit),
                weatherItem(weather, weatherStatus, risk), productionEvidence(supply),
                new Item("UNAVAILABLE", null, null, MARKET_NOTE, null), risk.level());

        List<String> limitations = new ArrayList<>(risk.limitations());
        limitations.add(WEATHER_WINDOW_NOTE);
        limitations.add(MARKET_NOTE);
        return new Assessed(crop, tier, evidence, reasons(crop, season, series.isPresent(), phFit, farm, req,
                weatherStatus, supply, risk), List.copyOf(unavailable), List.copyOf(limitations), risk,
                supply.response() == null ? null : supply.response().historicalYieldStats().coefficientOfVariation());
    }

    /** §10.2 tiers. Weather outside its absolute range is a caution, never on its own a reason for UNSUITABLE. */
    static Tier tier(boolean seriesSupported, PhFit phFit, String weatherStatus, RiskLevel productionRisk) {
        if (!seriesSupported) {
            return Tier.NOT_SUPPORTED;
        }
        if (phFit == PhFit.OUTSIDE_ABSOLUTE_RANGE || productionRisk == RiskLevel.CRITICAL) {
            return Tier.UNSUITABLE;
        }
        if (phFit == PhFit.TOLERABLE || "WITHIN_ABSOLUTE".equals(weatherStatus)
                || "OUTSIDE_ABSOLUTE".equals(weatherStatus) || productionRisk == RiskLevel.HIGH) {
            return Tier.SUITABLE_WITH_CAUTION;
        }
        return Tier.SUITABLE;
    }

    /** §10.1: derived from the TEMPERATURE_STRESS risk factor. */
    static String weatherSuitability(Risk risk) {
        return risk.factors().stream().filter(f -> "TEMPERATURE_STRESS".equals(f.code())).findFirst()
                .map(f -> switch (f.level()) {
                    case LOW -> "WITHIN_OPTIMAL";
                    case MODERATE -> "WITHIN_ABSOLUTE";
                    case HIGH, CRITICAL -> "OUTSIDE_ABSOLUTE";
                    case UNAVAILABLE -> "UNAVAILABLE";
                }).orElse("UNAVAILABLE");
    }

    private static Item soilItem(FarmResponse farm, CropRequirement req, PhFit fit) {
        FarmResponse.Soil soil = farm.soilProfile();
        if (fit == PhFit.UNAVAILABLE) {
            String why = soil == null || soil.ph() == null ? "The farm has no soil pH recorded."
                    : "FAO EcoCrop pH limits are not available yet.";
            return new Item("UNAVAILABLE", null, null, why, null);
        }
        return new Item(fit.name(), soil.ph().doubleValue(), "pH",
                "FAO EcoCrop: optimal " + req.getPhOptMin() + "–" + req.getPhOptMax() + ", absolute "
                        + req.getPhAbsMin() + "–" + req.getPhAbsMax(),
                new Provenance(soil.source().name(), DataClassification.valueOf(soil.dataClassification().name()),
                        null, null, null, null, null, null,
                        soil.measuredAt() == null ? null : soil.measuredAt().toString(), List.of()));
    }

    private static Item weatherItem(WeatherFetch weather, String status, Risk risk) {
        if ("UNAVAILABLE".equals(status)) {
            String why = weather.response() == null ? "Weather forecast unavailable (" + weather.errorCode() + ")."
                    : "FAO EcoCrop temperature limits are not available yet.";
            return new Item("UNAVAILABLE", null, null, why, null);
        }
        Factor f = risk.factors().stream().filter(x -> "TEMPERATURE_STRESS".equals(x.code())).findFirst().orElseThrow();
        return new Item(status, f.value(), f.unit(), f.threshold(), weather.response().dailyProvenance());
    }

    private static ProductionEvidence productionEvidence(SupplyFetch supply) {
        SupplyEstimateResponse s = supply.response();
        if (s == null) {
            return new ProductionEvidence("UNAVAILABLE", supply.cropYear(), null, null, null, null, null, null);
        }
        var stats = s.historicalYieldStats();
        return new ProductionEvidence("AVAILABLE", s.target().cropYear(), stats.coefficientOfVariation(),
                stats.downsideYearShare(), stats.meanYield(), s.estimate().servedMethod(),
                s.provenance().dataThrough(), s.provenance());
    }

    private static List<Reason> reasons(RefCrop crop, String season, boolean seriesSupported, PhFit phFit,
                                        FarmResponse farm, CropRequirement req, String weatherStatus,
                                        SupplyFetch supply, Risk risk) {
        String label = crop.getLabel();
        List<Reason> reasons = new ArrayList<>();
        if (!seriesSupported) {
            reasons.add(new Reason("SERIES_NOT_SUPPORTED", "No reported " + label
                    + " production series exists for this district in season " + season + "."));
            return List.copyOf(reasons);
        }
        switch (phFit) {
            case OPTIMAL -> reasons.add(new Reason("SOIL_PH_OPTIMAL", "Soil pH " + farm.soilProfile().ph()
                    + " is within " + label + "'s optimal range " + req.getPhOptMin() + "–" + req.getPhOptMax()
                    + " (FAO EcoCrop)."));
            case TOLERABLE -> reasons.add(new Reason("SOIL_PH_TOLERABLE", "Soil pH " + farm.soilProfile().ph()
                    + " is outside " + label + "'s optimal range but within its absolute range "
                    + req.getPhAbsMin() + "–" + req.getPhAbsMax() + " (FAO EcoCrop)."));
            case OUTSIDE_ABSOLUTE_RANGE -> reasons.add(new Reason("SOIL_PH_OUTSIDE_RANGE", "Soil pH "
                    + farm.soilProfile().ph() + " is outside " + label + "'s absolute range " + req.getPhAbsMin()
                    + "–" + req.getPhAbsMax() + " (FAO EcoCrop)."));
            case UNAVAILABLE -> reasons.add(new Reason("SOIL_PH_UNAVAILABLE",
                    "Soil compatibility could not be assessed."));
        }
        reasons.add(switch (weatherStatus) {
            case "WITHIN_OPTIMAL" -> new Reason("WEATHER_WITHIN_OPTIMAL",
                    "The 7-day temperature forecast stays within " + label + "'s optimal range.");
            case "WITHIN_ABSOLUTE" -> new Reason("WEATHER_WITHIN_ABSOLUTE",
                    "Some forecast days leave " + label + "'s optimal temperature range but stay within its limits.");
            case "OUTSIDE_ABSOLUTE" -> new Reason("WEATHER_OUTSIDE_ABSOLUTE",
                    "Some forecast days fall outside " + label + "'s absolute temperature limits.");
            default -> new Reason("WEATHER_UNAVAILABLE", "Weather suitability could not be assessed.");
        });
        SupplyEstimateResponse s = supply.response();
        if (s != null && s.historicalYieldStats().coefficientOfVariation() != null) {
            reasons.add(new Reason("PRODUCTION_RELIABILITY", "Reported " + label + " yield in this district varies "
                    + "with a coefficient of variation of " + s.historicalYieldStats().coefficientOfVariation()
                    + " (data through crop year " + s.provenance().dataThrough() + ")."));
        } else {
            reasons.add(new Reason("PRODUCTION_EVIDENCE_UNAVAILABLE", "Production evidence is unavailable ("
                    + supply.errorCode() + ")."));
        }
        reasons.add(new Reason("PRODUCTION_RISK_" + risk.level().name(), "Production risk is "
                + risk.level().name() + " (" + risk.assessedFactors() + " factors assessed)."));
        return List.copyOf(reasons);
    }

    private record Assessed(RefCrop crop, Tier tier, Evidence evidence, List<Reason> reasons,
                            List<String> unavailable, List<String> limitations, Risk risk, Double yieldCv) {

        Candidate toCandidate(Integer rank) {
            return new Candidate(rank, crop.getCropId(), crop.getLabel(), tier, evidence, reasons, unavailable,
                    limitations);
        }
    }
}
