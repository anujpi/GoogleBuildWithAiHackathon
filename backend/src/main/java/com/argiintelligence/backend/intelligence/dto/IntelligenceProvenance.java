package com.argiintelligence.backend.intelligence.dto;

import com.argiintelligence.backend.common.api.DataClassification;

import java.time.Instant;
import java.util.List;

/**
 * Provenance carried by every intelligence result.
 * modelVersion is null when no model produced the result; confidence is null unless the source states one.
 */
public record IntelligenceProvenance(
        String source,
        DataClassification dataClassification,
        Instant generatedAt,
        String modelVersion,
        Double confidence,
        List<String> limitations) {
}
