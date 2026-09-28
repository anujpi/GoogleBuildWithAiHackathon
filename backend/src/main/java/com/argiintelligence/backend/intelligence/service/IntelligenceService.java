package com.argiintelligence.backend.intelligence.service;

import com.argiintelligence.backend.common.api.DataClassification;
import com.argiintelligence.backend.common.exception.ApiException;
import com.argiintelligence.backend.farm.service.FarmService;
import com.argiintelligence.backend.intelligence.dto.IntelligenceProvenance;
import com.argiintelligence.backend.intelligence.dto.SupplyForecastResponse;
import com.argiintelligence.backend.ml.MlClient;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates intelligence results. ML predicts, this service decides what may be returned.
 * Nothing here computes or invents a prediction: without a real source the result is PREDICTION_UNAVAILABLE.
 */
@Service
@RequiredArgsConstructor
public class IntelligenceService {

    public static final String ML_SOURCE = "ML_SERVICE";

    private final MlClient mlClient;
    private final FarmService farmService;

    public SupplyForecastResponse supplyForecast(String regionId, String cropId, int horizonMonths) {
        // Provisional ML request body; replace with a typed request once the ML contract is final.
        SupplyPredictionResponse ml = mlClient.predictSupply(
                Map.of("regionId", regionId, "cropId", cropId, "horizonMonths", horizonMonths));
        IntelligenceProvenance provenance = new IntelligenceProvenance(ML_SOURCE, DataClassification.MODEL_PREDICTION,
                Instant.now(), ml.modelVersion(), null,
                List.of("The model reports no uncertainty, so confidence is not available."));
        return new SupplyForecastResponse(regionId, cropId, horizonMonths, ml.prediction().value(),
                ml.prediction().unit(), ml.prediction().period(), provenance, ml.provenance());
    }

    public void demandForecast(String regionId, String cropId, int horizonMonths) {
        throw unavailable("Demand forecast");
    }

    public void supplyDemand(String regionId, String cropId, int horizonMonths) {
        throw unavailable("Supply and demand");
    }

    /** Ownership is checked first: another user's farm is a 404, exactly like the farm API. */
    public void cropRecommendations(UUID ownerId, UUID farmId) {
        farmService.get(ownerId, farmId);
        throw unavailable("Crop recommendation");
    }

    public void agriculturalRisk(String regionId, String cropId) {
        throw unavailable("Agricultural risk");
    }

    private static ApiException unavailable(String what) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PREDICTION_UNAVAILABLE",
                what + " is not available yet: no prediction source is connected.");
    }
}
