package com.argiintelligence.backend.farm.dto;

import com.argiintelligence.backend.farm.entity.AreaUnit;
import com.argiintelligence.backend.farm.entity.Season;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Read-only list row for regional and admin views (MASTER_SPEC §6.6). Carries no owner PII. */
public record FarmSummary(UUID id, String name, String districtId, String districtLabel, BigDecimal area,
                          AreaUnit areaUnit, String currentCrop, Season season, boolean soilDataAvailable,
                          Instant updatedAt) {
}
