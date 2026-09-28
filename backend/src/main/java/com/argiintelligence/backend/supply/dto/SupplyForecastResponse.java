package com.argiintelligence.backend.supply.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A state-level supply forecast. Every forecast value comes from the ML service unchanged; history comes from
 * production_history unchanged. Field meanings, units and nullability: docs/supply-api.md.
 */
public record SupplyForecastResponse(
        String state,
        String crop,
        String season,
        String sourceSeasonLabel,
        int cropYear,
        BigDecimal targetAreaHectares,
        String targetAreaSource,
        Forecast forecast,
        Baseline baseline,
        List<HistoryPoint> history,
        Model model,
        List<String> limitations) {

    /** {@code predictionInterval} is null when the ML service returns none; it is never filled in. */
    public record Forecast(BigDecimal expectedProductionTonnes, String period, String dataClassification,
                           PredictionInterval predictionInterval, String generatedAt) {
    }

    /** An 80% prediction interval from the model's validation residuals. Not a confidence score. */
    public record PredictionInterval(BigDecimal lowerTonnes, BigDecimal upperTonnes, BigDecimal nominalCoverage,
                                     BigDecimal testEmpiricalCoverage, String method) {
    }

    public record Baseline(BigDecimal productionTonnes, String method, int historyYearsUsed) {
    }

    public record HistoryPoint(int cropYear, BigDecimal areaHectares, BigDecimal productionTonnes, String source,
                               String dataClassification, String datasetVersion) {
    }

    public record Model(String modelName, String modelVersion, String featureVersion, String datasetVersion,
                        String trainedAt, String trainingPeriod, String evaluationPeriod, String trainingDataSource,
                        String spatialGranularity) {
    }
}
