package com.argiintelligence.backend.ml.dto;

import com.argiintelligence.backend.common.api.DataClassification;

import java.time.Instant;

/** Provenance block attached by the ML service to every value block. Null fields do not apply. */
public record MlProvenance(
        String source,
        DataClassification dataClassification,
        String datasetVersion,
        String modelName,
        String modelVersion,
        String featureVersion,
        String dataThrough,
        Instant generatedAt) {
}
