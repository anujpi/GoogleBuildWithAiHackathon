package com.argiintelligence.backend.farm.entity;

import java.util.EnumSet;
import java.util.Set;

/** Where soil values came from, and which classifications that origin can honestly carry. */
public enum SoilDataSource {
    SOIL_HEALTH_CARD(EnumSet.of(SoilDataClassification.OBSERVED)),
    LAB_REPORT(EnumSet.of(SoilDataClassification.OBSERVED)),
    MANUAL(EnumSet.allOf(SoilDataClassification.class)),
    REGIONAL_ESTIMATE(EnumSet.of(SoilDataClassification.ESTIMATED, SoilDataClassification.SYNTHETIC)),
    OTHER(EnumSet.allOf(SoilDataClassification.class));

    private final Set<SoilDataClassification> allowed;

    SoilDataSource(Set<SoilDataClassification> allowed) {
        this.allowed = allowed;
    }

    public boolean permits(SoilDataClassification classification) {
        return allowed.contains(classification);
    }
}
