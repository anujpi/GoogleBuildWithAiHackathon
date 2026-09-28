package com.argiintelligence.backend.weather.dto;

import com.argiintelligence.backend.common.api.DataClassification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Weather for one point. Units are in the field names. {@code farmId} is null for coordinate lookups.
 * {@code confidence} (0..1) is null when the provider does not state one.
 */
public record WeatherResponse(
        UUID farmId,
        double latitude,
        double longitude,
        Provenance provenance,
        Current current,
        List<Daily> daily) {

    public record Provenance(
            String source,
            DataClassification dataClassification,
            Instant retrievedAt,
            Double confidence) {
    }

    public record Current(
            Instant observedAt,
            double temperatureC,
            double relativeHumidityPct,
            double rainfallMm,
            double windSpeedKmh) {
    }

    public record Daily(
            LocalDate date,
            double minTemperatureC,
            double maxTemperatureC,
            double rainfallMm,
            double rainProbabilityPct,
            double relativeHumidityPct) {
    }
}
