package com.argiintelligence.backend.weather.provider;

import com.argiintelligence.backend.common.api.DataClassification;
import com.argiintelligence.backend.weather.dto.WeatherResponse;

import java.util.List;

/**
 * Source of real weather data. Business logic depends on this, never on a concrete API (CLAUDE.md §11).
 *
 * <p>No implementation exists yet, so the weather endpoints answer 503 WEATHER_UNAVAILABLE. To connect a
 * source, add one {@code @Component} implementing this interface (e.g. an IMD, Open-Meteo or NASA POWER
 * client). Implementations must return real observed/forecast data and label it truthfully: a report
 * classified {@code SYNTHETIC} is refused by WeatherService. Throw on any failure rather than filling gaps.
 */
public interface WeatherProvider {

    /** @param days number of daily forecast entries, starting today */
    Report fetch(double latitude, double longitude, int days);

    /**
     * @param source             stable provider name, e.g. "OPEN_METEO"
     * @param dataClassification OBSERVED or FORECAST as reported by the source
     * @param confidence         0..1, or null when the source does not state one (never invented)
     */
    record Report(String source, DataClassification dataClassification, Double confidence,
                  WeatherResponse.Current current, List<WeatherResponse.Daily> daily) {
    }
}
