package com.argiintelligence.backend.ml.dto;

import java.math.BigDecimal;

/**
 * Supply prediction as returned by the ML service. Provisional: adjust to the ML contract once it is final.
 * There is deliberately no confidence field; add one only when the model actually reports uncertainty.
 * Unknown extra fields from the ML service are ignored.
 */
public record SupplyPredictionResponse(String modelVersion, Prediction prediction, Provenance provenance) {

    public record Prediction(BigDecimal value, String unit, String period) {
    }

    /** trainedAt is kept as the ML service's raw string until its format is agreed. */
    public record Provenance(String datasetVersion, String featureVersion, String trainedAt) {
    }
}
