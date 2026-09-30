package com.argiintelligence.backend.weather;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Fetches a live forecast from Open-Meteo. Failures return an UNAVAILABLE snapshot, never invented values. */
@Slf4j
@Service
@EnableConfigurationProperties(WeatherProperties.class)
public class WeatherService {

    static final String SOURCE = "Open-Meteo forecast API (open-meteo.com), model blend; CC BY 4.0";

    private final RestClient restClient;

    public WeatherService(WeatherProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }

    public WeatherSnapshot forecast(double latitude, double longitude) {
        String fetchedAt = Instant.now().toString();
        JsonNode body;
        try {
            body = restClient.get()
                    .uri(u -> u.path("/v1/forecast")
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("current", "temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m")
                            .queryParam("daily", "temperature_2m_max,temperature_2m_min,precipitation_sum,"
                                    + "precipitation_probability_max")
                            .queryParam("timezone", "Asia/Kolkata")
                            .queryParam("forecast_days", 7)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException ex) {
            log.warn("Open-Meteo unavailable: {}", ex.getMessage());
            return WeatherSnapshot.unavailable(latitude, longitude, "Open-Meteo could not be reached", fetchedAt);
        }
        if (body == null || body.get("current") == null || body.get("daily") == null) {
            return WeatherSnapshot.unavailable(latitude, longitude, "Open-Meteo returned an unexpected body",
                    fetchedAt);
        }
        JsonNode c = body.get("current");
        WeatherSnapshot.Current current = new WeatherSnapshot.Current(text(c, "time"), num(c, "temperature_2m"),
                num(c, "relative_humidity_2m"), num(c, "precipitation"), num(c, "wind_speed_10m"));
        JsonNode d = body.get("daily");
        List<WeatherSnapshot.Day> days = new ArrayList<>();
        JsonNode dates = d.get("time");
        double rain = 0;
        Double tMax = null;
        Double tMin = null;
        for (int i = 0; dates != null && i < dates.size(); i++) {
            WeatherSnapshot.Day day = new WeatherSnapshot.Day(dates.get(i).asString(), at(d, "temperature_2m_max", i),
                    at(d, "temperature_2m_min", i), at(d, "precipitation_sum", i),
                    at(d, "precipitation_probability_max", i) == null ? null
                            : (int) Math.round(at(d, "precipitation_probability_max", i)));
            days.add(day);
            if (day.precipitationSumMm() != null) {
                rain += day.precipitationSumMm();
            }
            if (day.temperatureMaxC() != null) {
                tMax = tMax == null ? day.temperatureMaxC() : Math.max(tMax, day.temperatureMaxC());
            }
            if (day.temperatureMinC() != null) {
                tMin = tMin == null ? day.temperatureMinC() : Math.min(tMin, day.temperatureMinC());
            }
        }
        return new WeatherSnapshot("OK", SOURCE, "FORECAST", fetchedAt, latitude, longitude, current, days,
                new WeatherSnapshot.Summary(Math.round(rain * 10) / 10.0, tMax, tMin), null);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asString();
    }

    private static Double num(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || !v.isNumber() ? null : v.asDouble();
    }

    private static Double at(JsonNode n, String field, int i) {
        JsonNode arr = n.get(field);
        if (arr == null || i >= arr.size() || !arr.get(i).isNumber()) {
            return null;
        }
        return arr.get(i).asDouble();
    }
}
