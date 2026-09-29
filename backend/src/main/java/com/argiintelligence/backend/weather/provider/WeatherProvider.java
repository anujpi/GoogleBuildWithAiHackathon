package com.argiintelligence.backend.weather.provider;

import com.argiintelligence.backend.common.api.DataClassification;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Source of real weather data (MASTER_SPEC §7). Business logic depends on this, never on a concrete API.
 * The only production implementation is {@link OpenMeteoWeatherProvider}; mock providers exist in tests only.
 * Implementations label their data truthfully (WeatherService refuses SYNTHETIC) and throw
 * {@link WeatherProviderException} on any failure rather than filling gaps.
 */
public interface WeatherProvider {

    /** @param days number of daily forecast entries, starting today (1..14) */
    Report fetch(double latitude, double longitude, int days);

    /** Stable provider name used as the provenance source, e.g. "OPEN_METEO". */
    String name();

    /** Values are nullable: null = the provider did not report it. */
    record Report(
            String source,
            Current current,
            DataClassification currentClassification,
            List<String> currentNotes,
            List<Day> daily,
            DataClassification dailyClassification) {
    }

    record Current(OffsetDateTime time, Double temperatureC, Double relativeHumidityPct, Double precipitationMm,
                   Double windSpeedKmh) {
    }

    record Day(LocalDate date, Double minTemperatureC, Double maxTemperatureC, Double precipitationMm,
               Double precipitationProbabilityPct, Double relativeHumidityPct) {
    }
}
