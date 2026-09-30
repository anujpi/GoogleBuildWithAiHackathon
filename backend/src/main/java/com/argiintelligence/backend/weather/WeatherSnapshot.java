package com.argiintelligence.backend.weather;

import java.util.List;

/**
 * Current conditions and a 7-day outlook for one point. When the provider cannot be reached, {@code status} is
 * UNAVAILABLE and every value is null: nothing is estimated or filled in.
 */
public record WeatherSnapshot(
        String status,
        String source,
        String dataClassification,
        String fetchedAt,
        Double latitude,
        Double longitude,
        Current current,
        List<Day> daily,
        Summary next7Days,
        String unavailableReason) {

    public record Current(String time, Double temperatureC, Double relativeHumidityPct, Double precipitationMm,
                          Double windSpeedKmh) {
    }

    public record Day(String date, Double temperatureMaxC, Double temperatureMinC, Double precipitationSumMm,
                      Integer precipitationProbabilityMaxPct) {
    }

    public record Summary(Double totalPrecipitationMm, Double maxTemperatureC, Double minTemperatureC) {
    }

    static WeatherSnapshot unavailable(double lat, double lon, String reason, String fetchedAt) {
        return new WeatherSnapshot("UNAVAILABLE", WeatherService.SOURCE, null, fetchedAt, lat, lon, null, List.of(),
                null, reason);
    }
}
