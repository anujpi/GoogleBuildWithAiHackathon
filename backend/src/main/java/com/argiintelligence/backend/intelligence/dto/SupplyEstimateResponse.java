package com.argiintelligence.backend.intelligence.dto;

import com.argiintelligence.backend.common.api.Provenance;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse.Area;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse.Baseline;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse.HistoricalYieldStats;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse.HistoryPoint;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse.HistoryUnits;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse.ModelEvaluation;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse.QuantityWithInterval;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse.Reported;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** GET /api/intelligence/supply (MASTER_SPEC §8.2). Values, units and intervals are the ML values unchanged. */
public record SupplyEstimateResponse(
        Target target,
        Estimate estimate,
        Baseline baseline,
        Reported reported,
        History history,
        HistoricalYieldStats historicalYieldStats,
        ModelEvaluation modelEvaluation,
        Provenance provenance,
        Provenance historyProvenance,
        List<String> limitations) {

    public record Target(String districtId, String districtLabel, String cropId, String cropLabel, String season,
                         int cropYear) {
    }

    public record Estimate(QuantityWithInterval production,
                           @JsonProperty("yield") QuantityWithInterval yieldValue,
                           Area area,
                           String servedMethod,
                           List<Integer> historyYearsUsed) {
    }

    public record History(HistoryUnits units, List<HistoryPoint> points) {
    }
}
