package com.argiintelligence.backend.intelligence.service;

import com.argiintelligence.backend.common.exception.ApiException;
import com.argiintelligence.backend.farm.dto.FarmResponse;
import com.argiintelligence.backend.intelligence.dto.SupplyEstimateResponse;
import com.argiintelligence.backend.reference.service.ReferenceService;
import com.argiintelligence.backend.weather.dto.WeatherResponse;
import com.argiintelligence.backend.weather.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Inputs shared by crop evidence and risk. An upstream failure (ML, weather) is captured as its §13 error code, so
 * the dependent evidence becomes UNAVAILABLE instead of failing the whole response (MASTER_SPEC §10 "partial
 * failures"). Only an invalid farm or scope fails the request.
 */
@Component
@RequiredArgsConstructor
class IntelligenceInputs {

    private final WeatherService weather;
    private final SupplyService supply;
    private final ReferenceService reference;

    /** The farm's district, which must be set and in the served scope (422 otherwise). */
    String requireInScopeDistrict(FarmResponse farm) {
        if (farm.districtId() == null) {
            throw ApiException.unsupportedInput("districtId",
                    "The farm has no district; set one of the supported districts to get intelligence");
        }
        if (!reference.districtInScope(farm.districtId())) {
            throw ApiException.unsupportedInput("districtId",
                    "The farm's district " + farm.districtId() + " has no supported production series");
        }
        return farm.districtId();
    }

    WeatherFetch weather(FarmResponse farm) {
        try {
            return new WeatherFetch(weather.forFarm(farm, RiskRules.WEATHER_DAYS), null);
        } catch (ApiException ex) {
            return new WeatherFetch(null, ex.getCode());
        }
    }

    SupplyFetch supply(String districtId, String cropId, String season, int cropYear) {
        try {
            return new SupplyFetch(supply.supply(districtId, cropId, season, cropYear, null), null, cropYear);
        } catch (ApiException ex) {
            return new SupplyFetch(null, ex.getCode(), cropYear);
        }
    }

    record WeatherFetch(WeatherResponse response, String errorCode) {
    }

    /** {@code cropYear} null = no series, so nothing was requested. */
    record SupplyFetch(SupplyEstimateResponse response, String errorCode, Integer cropYear) {

        static SupplyFetch noSeries(String reason) {
            return new SupplyFetch(null, reason, null);
        }
    }
}
