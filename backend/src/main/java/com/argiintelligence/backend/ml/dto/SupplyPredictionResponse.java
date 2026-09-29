package com.argiintelligence.backend.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * ML {@code POST /v1/predict/supply} response. Mirrors ml-service/docs/ml-contracts/supply.md field for field;
 * MlClientTest deserialises the ML contract examples into it. Units and enum-like values stay strings so the
 * backend passes them through unchanged. {@code yield} is a Java restricted identifier, hence the renames.
 */
public record SupplyPredictionResponse(
        Target target,
        Estimate estimate,
        Baseline baseline,
        Reported reported,
        History history,
        HistoricalYieldStats historicalYieldStats,
        ModelEvaluation modelEvaluation,
        List<String> limitations) {

    public record Target(String districtId, String cropId, String season, int cropYear) {
    }

    public record Interval(double lower, double upper, double nominalCoverage, Double empiricalCoverage,
                           String method) {
    }

    public record Quantity(double value, String unit) {
    }

    public record QuantityWithInterval(double value, String unit, Interval interval) {
    }

    public record Area(double value, String unit, String areaSource) {
    }

    public record Estimate(QuantityWithInterval production,
                           @JsonProperty("yield") QuantityWithInterval yieldValue,
                           Area area,
                           String servedMethod,
                           List<Integer> historyYearsUsed,
                           MlProvenance provenance) {
    }

    public record Baseline(String method, Quantity production) {
    }

    public record Reported(Quantity area, Quantity production, @JsonProperty("yield") Quantity yieldValue) {
    }

    public record HistoryUnits(String area, String production, @JsonProperty("yield") String yieldUnit) {
    }

    public record HistoryPoint(int cropYear, double area, double production,
                               @JsonProperty("yield") double yieldValue) {
    }

    public record History(HistoryUnits units, List<HistoryPoint> points, MlProvenance provenance) {
    }

    public record HistoricalYieldStats(int yearsObserved, Quantity meanYield, Double coefficientOfVariation,
                                       Double downsideYearShare, int yearsAssessedForDownside,
                                       String downsideDefinition) {
    }

    public record ModelEvaluation(String trainingPeriod, String validationPeriod, String testPeriod,
                                  String servedMethod, double testWape, String bestBaseline,
                                  double bestBaselineTestWape, Double testIntervalCoverage) {
    }
}
