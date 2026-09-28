package com.argiintelligence.backend.supply.entity;

/** Whether production values were observed, estimated, or synthetic. Synthetic data must never be labelled OBSERVED. */
public enum ProductionDataClassification {
    OBSERVED,
    ESTIMATED,
    SYNTHETIC
}
