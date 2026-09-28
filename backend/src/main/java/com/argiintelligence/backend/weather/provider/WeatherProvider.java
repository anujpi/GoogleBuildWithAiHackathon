package com.argiintelligence.backend.weather.provider;

import com.argiintelligence.backend.common.api.DataClassification;
import com.argiintelligence.backend.weather.dto.WeatherResponse;

import java.util.List;

/** Source of weather data. Business logic depends on this, never on a concrete API (CLAUDE.md §11). */
public interface WeatherProvider {

    /** @param days number of daily forecast entries, starting today */
    Report fetch(double latitude, double longitude, int days);

    record Report(String source, DataClassification dataClassification, Double confidence,
                  WeatherResponse.Current current, List<WeatherResponse.Daily> daily) {
    }
}
