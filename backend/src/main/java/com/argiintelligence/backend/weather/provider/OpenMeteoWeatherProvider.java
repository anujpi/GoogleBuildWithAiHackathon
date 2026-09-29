package com.argiintelligence.backend.weather.provider;

import com.argiintelligence.backend.common.api.DataClassification;
import com.argiintelligence.backend.common.web.RequestIdPropagation;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Open-Meteo Forecast API (MASTER_SPEC D6, §7.1). No API key. "Current" is a model analysis for the grid cell,
 * so it is ESTIMATED; the daily values are FORECAST. Any transport, status or shape problem becomes a
 * {@link WeatherProviderException}: nothing is filled in, defaulted or served from a fallback.
 */
@Slf4j
@Component
public class OpenMeteoWeatherProvider implements WeatherProvider {

    public static final String NAME = "OPEN_METEO";
    static final String CURRENT_NOTE = "Model analysis for the grid cell, not a station observation.";
    private static final String DISPLAY = "Open-Meteo";
    private static final String CURRENT = "temperature_2m,relative_humidity_2m,precipitation,wind_speed_10m";
    private static final String DAILY = "temperature_2m_min,temperature_2m_max,precipitation_sum,"
            + "precipitation_probability_max,relative_humidity_2m_mean";

    private final RestClient http;

    @Autowired
    public OpenMeteoWeatherProvider(WeatherProperties props) {
        this(RestClient.builder().baseUrl(props.baseUrl())
                .requestFactory(requestFactory(props))
                .requestInterceptor(RequestIdPropagation.interceptor())
                .build());
    }

    /** For tests: a client bound to a mock server. */
    OpenMeteoWeatherProvider(RestClient http) {
        this.http = http;
    }

    private static JdkClientHttpRequestFactory requestFactory(WeatherProperties props) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build());
        factory.setReadTimeout(props.readTimeout());
        return factory;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Report fetch(double latitude, double longitude, int days) {
        Body body;
        try {
            body = http.get().uri(u -> u.path("/v1/forecast")
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("timezone", "Asia/Kolkata")
                            .queryParam("forecast_days", days)
                            .queryParam("current", CURRENT)
                            .queryParam("daily", DAILY)
                            .build())
                    .retrieve().body(Body.class);
        } catch (ResourceAccessException ex) {
            log.warn("Open-Meteo unreachable: {}", ex.getMessage());
            throw WeatherProviderException.unavailable(DISPLAY);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                log.warn("Open-Meteo rate limit reached");
                throw WeatherProviderException.rateLimited(DISPLAY);
            }
            log.warn("Open-Meteo returned {}", ex.getStatusCode());
            throw WeatherProviderException.invalidResponse(DISPLAY);
        } catch (RestClientException ex) {
            log.warn("Open-Meteo response could not be read", ex);
            throw WeatherProviderException.invalidResponse(DISPLAY);
        }
        return toReport(body, days);
    }

    private static Report toReport(Body body, int days) {
        String problem = shapeViolation(body, days);
        if (problem != null) {
            log.warn("Open-Meteo response rejected: {}", problem);
            throw WeatherProviderException.invalidResponse(DISPLAY);
        }
        try {
            ZoneOffset offset = ZoneOffset.ofTotalSeconds(body.utcOffsetSeconds());
            Body.Current c = body.current();
            Current current = new Current(LocalDateTime.parse(c.time()).atOffset(offset), c.temperature(),
                    c.humidity(), c.precipitation(), c.windSpeed());
            Body.Daily d = body.daily();
            List<Day> daily = new ArrayList<>(days);
            for (int i = 0; i < days; i++) {
                daily.add(new Day(LocalDate.parse(d.time().get(i)), d.minTemperature().get(i),
                        d.maxTemperature().get(i), d.precipitation().get(i), d.precipitationProbability().get(i),
                        d.humidity().get(i)));
            }
            return new Report(NAME, current, DataClassification.ESTIMATED, List.of(CURRENT_NOTE), daily,
                    DataClassification.FORECAST);
        } catch (DateTimeParseException | ArithmeticException | IllegalArgumentException ex) {
            log.warn("Open-Meteo response has an unreadable date or offset", ex);
            throw WeatherProviderException.invalidResponse(DISPLAY);
        }
    }

    /** Null when the body has the §7.3 shape: current and daily present, arrays aligned, no NaN/infinite values. */
    private static String shapeViolation(Body body, int days) {
        if (body == null || body.current() == null || body.daily() == null || body.utcOffsetSeconds() == null) {
            return "missing current, daily or utc_offset_seconds";
        }
        Body.Current c = body.current();
        if (c.time() == null) {
            return "current.time missing";
        }
        Body.Daily d = body.daily();
        List<List<?>> arrays = Arrays.asList(d.time(), d.minTemperature(), d.maxTemperature(), d.precipitation(),
                d.precipitationProbability(), d.humidity());
        if (arrays.stream().anyMatch(a -> a == null || a.size() != days)) {
            return "daily arrays missing or not " + days + " long";
        }
        if (d.time().stream().anyMatch(t -> t == null)) {
            return "daily.time has a null date";
        }
        Stream<Double> values = Stream.of(Stream.of(c.temperature(), c.humidity(), c.precipitation(), c.windSpeed()),
                        d.minTemperature().stream(), d.maxTemperature().stream(), d.precipitation().stream(),
                        d.precipitationProbability().stream(), d.humidity().stream())
                .flatMap(s -> s);
        if (values.anyMatch(v -> v != null && !Double.isFinite(v))) {
            return "non-finite value";
        }
        return null;
    }

    /** The subset of the Open-Meteo response this provider reads. Unknown fields are ignored. */
    record Body(@JsonProperty("utc_offset_seconds") Integer utcOffsetSeconds, Current current, Daily daily) {

        record Current(String time,
                       @JsonProperty("temperature_2m") Double temperature,
                       @JsonProperty("relative_humidity_2m") Double humidity,
                       Double precipitation,
                       @JsonProperty("wind_speed_10m") Double windSpeed) {
        }

        record Daily(List<String> time,
                     @JsonProperty("temperature_2m_min") List<Double> minTemperature,
                     @JsonProperty("temperature_2m_max") List<Double> maxTemperature,
                     @JsonProperty("precipitation_sum") List<Double> precipitation,
                     @JsonProperty("precipitation_probability_max") List<Double> precipitationProbability,
                     @JsonProperty("relative_humidity_2m_mean") List<Double> humidity) {
        }
    }
}
