package com.argiintelligence.backend.weather.dto;

import com.argiintelligence.backend.common.api.Provenance;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Weather for one point (MASTER_SPEC §7.2). Units are in the field names. {@code farmId} is null for coordinate
 * lookups. Every value is nullable: a value the provider omits stays null, never zero. Current conditions and
 * the daily forecast carry separate provenance because they are different kinds of data.
 */
public record WeatherResponse(
        UUID farmId,
        double latitude,
        double longitude,
        Current current,
        List<Daily> daily,
        Provenance dailyProvenance) {

    public record Current(
            OffsetDateTime time,
            Double temperatureC,
            Double relativeHumidityPct,
            Double precipitationMm,
            Double windSpeedKmh,
            Provenance provenance) {
    }

    public record Daily(
            LocalDate date,
            Double minTemperatureC,
            Double maxTemperatureC,
            Double precipitationMm,
            Double precipitationProbabilityPct,
            Double relativeHumidityPct) {
    }
}
