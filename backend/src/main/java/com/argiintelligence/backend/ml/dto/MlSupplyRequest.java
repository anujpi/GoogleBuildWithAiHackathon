package com.argiintelligence.backend.ml.dto;

/**
 * ML {@code POST /v1/predict/supply} request (MASTER_SPEC §9.3). {@code areaHectares} null = ML uses the
 * reported area. The backend sends ids only, never history.
 */
public record MlSupplyRequest(String districtId, String cropId, String season, int cropYear, Double areaHectares) {
}
