package com.argiintelligence.backend.supply.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Query parameters of {@code GET /api/supply/forecast}. A regional (state-level) forecast, not a farm one.
 * {@code areaHectares} is the whole state's planned or sown area for the target season, supplied by the caller
 * as an explicit assumption; it is never taken from a farm.
 */
public record SupplyForecastRequest(
        @NotBlank @Size(max = 100) String state,
        @NotBlank @Size(max = 100) String crop,
        @NotBlank String season,
        @NotNull @Min(1901) @Max(2100) Integer cropYear,
        @NotNull @Positive BigDecimal areaHectares) {
}
