package com.argiintelligence.backend.common.api;

/** Platform-wide data provenance classes (CLAUDE.md §12). SYNTHETIC must never be shown as measured data. */
public enum DataClassification {
    OBSERVED,
    FORECAST,
    MODEL_PREDICTION,
    REGIONAL_ESTIMATE,
    SYNTHETIC
}
