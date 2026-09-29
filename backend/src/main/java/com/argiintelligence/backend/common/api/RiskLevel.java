package com.argiintelligence.backend.common.api;

/**
 * Shared risk vocabulary (MASTER_SPEC §11). Declared in severity order, so {@code compareTo} ranks them;
 * UNAVAILABLE is not a severity and is never the "worst" of assessed factors.
 */
public enum RiskLevel {
    LOW,
    MODERATE,
    HIGH,
    CRITICAL,
    UNAVAILABLE;

    public boolean isAssessed() {
        return this != UNAVAILABLE;
    }
}
