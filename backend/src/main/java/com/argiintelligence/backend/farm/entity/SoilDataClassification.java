package com.argiintelligence.backend.farm.entity;

/** Whether soil values were measured, estimated, or synthetic. Synthetic data must never be labelled OBSERVED. */
public enum SoilDataClassification {
    OBSERVED,
    ESTIMATED,
    SYNTHETIC
}
