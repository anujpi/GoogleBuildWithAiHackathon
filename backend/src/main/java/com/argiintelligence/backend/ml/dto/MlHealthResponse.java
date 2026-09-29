package com.argiintelligence.backend.ml.dto;

import java.util.List;

/** ML {@code GET /health} (MASTER_SPEC §9.1): the process is UP; each model is READY or NOT_READY. */
public record MlHealthResponse(String status, String service, String version, String environment,
                               List<Model> models) {

    public record Model(String capability, String status, String modelVersion, String datasetVersion,
                        String featureVersion, String reason) {
    }
}
