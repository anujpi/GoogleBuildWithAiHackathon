package com.argiintelligence.backend.intelligence.dto;

import com.argiintelligence.backend.common.api.Provenance;
import com.argiintelligence.backend.common.api.RiskLevel;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse.Quantity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * GET /api/farms/{id}/crop-evidence (MASTER_SPEC §10). Structured evidence per crop, grouped into tiers and ordered
 * by the published rule {@code crop-evidence-v1}. There is no single score and no winner; unavailable evidence is
 * named in {@code unavailable} and never shown as a value.
 */
public record CropEvidenceResponse(UUID farmId, String districtId, String season, String rankingRule,
                                   List<Candidate> candidates, Instant generatedAt) {

    public enum Tier { SUITABLE, SUITABLE_WITH_CAUTION, UNSUITABLE, NOT_SUPPORTED }

    /** {@code rank} is null for NOT_SUPPORTED crops, which are listed but not ranked. */
    public record Candidate(Integer rank, String cropId, String cropLabel, Tier tier, Evidence evidence,
                            List<Reason> reasons, List<String> unavailable, List<String> limitations) {
    }

    public record Evidence(boolean seriesSupported, Item soilCompatibility, Item weatherSuitability,
                           ProductionEvidence productionEvidence, Item marketContext, RiskLevel productionRisk) {
    }

    /**
     * One evidence item. {@code status} is the §10.1 vocabulary (e.g. OPTIMAL, WITHIN_ABSOLUTE, UNAVAILABLE);
     * {@code basis} states the rule or limits compared against.
     */
    public record Item(String status, Double value, String unit, String basis, Provenance provenance) {
    }

    /** District production reliability from ML (§10.1). Fields are null when unavailable. */
    public record ProductionEvidence(String status, Integer cropYear, Double coefficientOfVariation,
                                     Double downsideYearShare, Quantity meanYield, String servedMethod,
                                     String dataThrough, Provenance provenance) {
    }

    public record Reason(String code, String text) {
    }
}
