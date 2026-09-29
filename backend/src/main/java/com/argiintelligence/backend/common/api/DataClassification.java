package com.argiintelligence.backend.common.api;

/**
 * Platform-wide data provenance classes (MASTER_SPEC D4), identical in ML, backend and frontend.
 * SYNTHETIC must never be shown as measured data. {@code SoilDataSource.REGIONAL_ESTIMATE} is a source, not this.
 */
public enum DataClassification {
    OBSERVED,
    FORECAST,
    MODEL_PREDICTION,
    ESTIMATED,
    SYNTHETIC
}
