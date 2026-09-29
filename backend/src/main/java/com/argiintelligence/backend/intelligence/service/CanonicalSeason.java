package com.argiintelligence.backend.intelligence.service;

import com.argiintelligence.backend.common.exception.ApiException;
import com.argiintelligence.backend.farm.entity.Season;

import java.util.Set;

/** Canonical ML seasons and the farm-season mapping of MASTER_SPEC §5.3. */
public final class CanonicalSeason {

    public static final Set<String> VALUES = Set.of("KHARIF", "RABI", "SUMMER", "WHOLE_YEAR", "AUTUMN", "WINTER");
    public static final String PATTERN = "KHARIF|RABI|SUMMER|WHOLE_YEAR|AUTUMN|WINTER";
    public static final String MESSAGE = "must be one of KHARIF, RABI, SUMMER, WHOLE_YEAR, AUTUMN, WINTER";

    private CanonicalSeason() {
    }

    /**
     * The explicit season when given, otherwise the farm's mapped season: KHARIF→KHARIF, RABI→RABI, ZAID→SUMMER.
     * A farm in season OTHER has no canonical season, so it needs the explicit parameter (422 otherwise).
     */
    public static String resolve(String explicit, Season farmSeason) {
        if (explicit != null) {
            return explicit;
        }
        return switch (farmSeason) {
            case KHARIF -> "KHARIF";
            case RABI -> "RABI";
            case ZAID -> "SUMMER";
            case OTHER -> throw ApiException.unsupportedInput("season",
                    "The farm's season is OTHER, which has no canonical season; pass the season parameter");
        };
    }
}
