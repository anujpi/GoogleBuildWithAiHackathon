package com.argiintelligence.backend.intelligence.service;

import com.argiintelligence.backend.common.exception.ApiException;
import com.argiintelligence.backend.farm.dto.FarmResponse;
import com.argiintelligence.backend.intelligence.dto.RiskAssessmentResponse;
import com.argiintelligence.backend.intelligence.dto.RiskAssessmentResponse.Risk;
import com.argiintelligence.backend.intelligence.service.IntelligenceInputs.SupplyFetch;
import com.argiintelligence.backend.intelligence.service.IntelligenceInputs.WeatherFetch;
import com.argiintelligence.backend.reference.entity.CropRequirement;
import com.argiintelligence.backend.reference.entity.RefCrop;
import com.argiintelligence.backend.reference.service.ReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Farm x crop risk (MASTER_SPEC §11). Production and market risk stay separate; market is UNAVAILABLE until M4. */
@Service
@RequiredArgsConstructor
public class RiskService {

    private final IntelligenceInputs inputs;
    private final ReferenceService reference;

    public RiskAssessmentResponse assess(FarmResponse farm, String cropId, String explicitSeason) {
        String districtId = inputs.requireInScopeDistrict(farm);
        String season = CanonicalSeason.resolve(explicitSeason, farm.season());
        RefCrop crop = reference.crop(cropId)
                .orElseThrow(() -> ApiException.unsupportedInput("cropId", "Crop " + cropId + " is not supported"));
        WeatherFetch weather = inputs.weather(farm);
        // ML-derived factors use the latest estimable crop year of the series (§11).
        SupplyFetch supply = reference.series(districtId, cropId, season)
                .map(s -> inputs.supply(districtId, cropId, season, s.latestEstimableYear()))
                .orElse(SupplyFetch.noSeries("NO_SERIES_FOR_SEASON"));
        return new RiskAssessmentResponse(farm.id(), cropId, season, supply.cropYear(), RiskRules.RULE_SET,
                productionRisk(farm, crop, weather, supply), RiskRules.marketUnavailable(), Instant.now());
    }

    /** Also used by crop evidence, so both endpoints apply exactly the same rules. */
    Risk productionRisk(FarmResponse farm, RefCrop crop, WeatherFetch weather, SupplyFetch supply) {
        CropRequirement req = reference.requirement(crop.getCropId()).orElse(null);
        List<String> notes = new ArrayList<>();
        if (weather.response() != null) {
            notes.add("Weather factors reflect the next " + RiskRules.WEATHER_DAYS
                    + " days of forecast, not the whole season.");
        }
        if (supply.response() != null) {
            notes.add("Yield factors use district-level reported data through crop year "
                    + supply.response().historyProvenance().dataThrough() + ", not this farm's own history.");
        }
        return RiskRules.combine(List.of(
                RiskRules.heavyRainfall(weather.response(), weather.errorCode()),
                RiskRules.temperatureStress(weather.response(), weather.errorCode(), req, crop.getLabel()),
                RiskRules.yieldVariability(supply.response(), supply.errorCode()),
                RiskRules.downsideFrequency(supply.response(), supply.errorCode()),
                RiskRules.soilPh(farm.soilProfile(), req, crop.getLabel())), notes);
    }
}
