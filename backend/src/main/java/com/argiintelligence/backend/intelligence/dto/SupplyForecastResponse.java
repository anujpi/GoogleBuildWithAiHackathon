package com.argiintelligence.backend.intelligence.dto;

import com.argiintelligence.backend.ml.dto.MlProvenance;

import java.math.BigDecimal;

/**
 * UNUSED since GET /api/intelligence/supply replaced supply-forecast (see SupplyResponse). Kept only until it
 * is deleted together with IntelligenceProvenance.
 *
 * Supply forecast for one region and crop. {@code unit} and {@code forecastPeriod} are passed through from the
 * model unchanged (never converted). {@code modelProvenance} is null when the ML service sends none.
 */
public record SupplyForecastResponse(
        String regionId,
        String cropId,
        int horizonMonths,
        BigDecimal predictedSupply,
        String unit,
        String forecastPeriod,
        IntelligenceProvenance provenance,
        MlProvenance modelProvenance) {
}
