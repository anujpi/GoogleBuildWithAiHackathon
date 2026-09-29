package com.argiintelligence.backend.common.api;

import java.time.Instant;
import java.util.List;

/**
 * Shared provenance DTO (MASTER_SPEC §12). Optional fields are null when unknown, never invented or defaulted.
 *
 * @param retrievedAt when the backend obtained the value
 * @param generatedAt when the upstream (e.g. the ML service) produced it
 * @param dataThrough latest period the underlying data covers ("YYYY", "YYYY-MM" or "YYYY-MM-DD")
 */
public record Provenance(
        String source,
        DataClassification dataClassification,
        Instant retrievedAt,
        Instant generatedAt,
        String datasetVersion,
        String modelName,
        String modelVersion,
        String featureVersion,
        String dataThrough,
        List<String> notes) {
}
