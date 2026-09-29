package com.argiintelligence.backend.intelligence.dto;

import com.argiintelligence.backend.common.api.DataClassification;
import com.argiintelligence.backend.common.api.RiskLevel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * GET /api/farms/{id}/risk (MASTER_SPEC §11). Production and market risk are separate objects; each level is the
 * worst assessed factor, never a weighted score. {@code cropYear} is the crop year of the ML-derived factors, null
 * when the farm's district has no series for this crop and season.
 */
public record RiskAssessmentResponse(UUID farmId, String cropId, String season, Integer cropYear, String ruleSet,
                                     Risk productionRisk, Risk marketRisk, Instant generatedAt) {

    /** {@code factors} holds only assessed factors; the unavailable ones are named in {@code unavailableFactors}. */
    public record Risk(RiskLevel level, List<Factor> factors, int assessedFactors, List<String> unavailableFactors,
                       List<String> limitations) {
    }

    /**
     * One assessed factor. {@code threshold} is the rule applied, with its citation or "product rule risk-rules-v1";
     * {@code observedOrForecastFor} is the period the input describes.
     */
    public record Factor(String code, RiskLevel level, Double value, String unit, String threshold, String source,
                         DataClassification dataClassification, String observedOrForecastFor, String reason) {
    }
}
