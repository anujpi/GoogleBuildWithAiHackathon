package com.argiintelligence.backend.ml.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * 200 body of ML {@code POST /v1/predict/supply} (ml-service/docs/ml-contracts/supply.md).
 * {@code prediction.interval} and the two provenance fields added in v1 are nullable; everything else is required,
 * which {@link com.argiintelligence.backend.ml.MlClient} checks before returning.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MlSupplyResponse(
        String modelName,
        String modelVersion,
        String dataClassification,
        Prediction prediction,
        Evidence evidence,
        Provenance provenance,
        String generatedAt) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Prediction(BigDecimal value, String unit, String period, Interval interval) {
    }

    /** An 80% prediction interval from validation residuals. Not a confidence score. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Interval(BigDecimal lower, BigDecimal upper, BigDecimal nominalCoverage, String method,
                           BigDecimal testEmpiricalCoverage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Evidence(BigDecimal baselineValue, String baselineMethod, Integer historyYearsUsed) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Provenance(String datasetVersion, String featureVersion, String trainedAt, String trainingPeriod,
                             String evaluationPeriod, String trainingDataSource, String spatialGranularity) {
    }
}
