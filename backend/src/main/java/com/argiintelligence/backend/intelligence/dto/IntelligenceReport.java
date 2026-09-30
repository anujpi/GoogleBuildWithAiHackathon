package com.argiintelligence.backend.intelligence.dto;

import com.argiintelligence.backend.supply.dto.SupplyForecastResponse;
import com.argiintelligence.backend.weather.WeatherSnapshot;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Everything the platform knows about one farm, crop and season, with each value's origin. ML sections are passed
 * through unchanged (JsonNode); gap, risk and decision are computed here by documented backend rules.
 */
public record IntelligenceReport(
        FarmContext farm,
        String focusCrop,
        String focusCropReason,
        WeatherSnapshot weather,
        Section<JsonNode> suitability,
        Section<SupplyForecastResponse> supply,
        Section<JsonNode> demand,
        Section<JsonNode> demandOutlook,
        Section<Gap> gap,
        List<AnomalyCheck> anomalies,
        Risk risk,
        Decision decision,
        List<String> dataNotices,
        String generatedAt) {

    public record FarmContext(UUID id, String name, String state, String district, BigDecimal latitude,
                              BigDecimal longitude, String season, String sourceSeasonLabel, String irrigationType,
                              String currentCrop, BigDecimal soilPh, String soilDataClassification,
                              BigDecimal areaHectares) {
    }

    /** Supply minus demand for the same state and year. Positive = surplus. */
    public record Gap(int year, BigDecimal supplyTonnes, String supplyClassification, BigDecimal demandTonnes,
                      String demandClassification, BigDecimal gapTonnes, BigDecimal gapPctOfDemand, String status,
                      String method, List<String> caveats) {
    }

    public record AnomalyCheck(String metric, String label, String source, Section<JsonNode> result) {
    }

    public record Risk(String overall, List<RiskFactor> factors, String method) {
    }

    public record RiskFactor(String category, String level, String reason, String evidenceSource) {
    }

    public record Decision(String status, String headline, List<String> reasons, List<String> alternatives,
                           String decidedBy) {
    }
}
