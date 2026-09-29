package com.argiintelligence.backend.weather.provider;

import com.argiintelligence.backend.common.api.DataClassification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

/**
 * The Open-Meteo client against a mock HTTP server (MASTER_SPEC §7, §16). The JSON below is a hand-written test
 * fixture in the Open-Meteo response format; it is never served by the application.
 */
class OpenMeteoWeatherProviderTest {

    private static final String BODY = """
            { "latitude": 26.875, "longitude": 80.875, "utc_offset_seconds": 19800, "timezone": "Asia/Kolkata",
              "current": { "time": "2026-09-29T14:00", "interval": 900, "temperature_2m": 31.2,
                           "relative_humidity_2m": 64, "precipitation": 0.0, "wind_speed_10m": null },
              "daily": { "time": ["2026-09-29", "2026-09-30"],
                         "temperature_2m_min": [24.1, 23.8], "temperature_2m_max": [33.0, null],
                         "precipitation_sum": [2.4, 70.1], "precipitation_probability_max": [40, 90],
                         "relative_humidity_2m_mean": [71, 80] } }
            """;

    private MockRestServiceServer server;
    private OpenMeteoWeatherProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://weather.test");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new OpenMeteoWeatherProvider(builder.build());
    }

    private void respond(String body) {
        server.expect(requestTo(startsWith("http://weather.test/v1/forecast")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void mapsTheResponseWithSplitClassificationAndKeepsNulls() {
        server.expect(requestTo(startsWith("http://weather.test/v1/forecast")))
                .andExpect(queryParam("latitude", "26.85"))
                .andExpect(queryParam("forecast_days", "2"))
                .andExpect(queryParam("timezone", "Asia/Kolkata"))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

        WeatherProvider.Report r = provider.fetch(26.85, 80.95, 2);

        assertThat(r.source()).isEqualTo("OPEN_METEO");
        assertThat(r.currentClassification()).isEqualTo(DataClassification.ESTIMATED);
        assertThat(r.dailyClassification()).isEqualTo(DataClassification.FORECAST);
        assertThat(r.currentNotes()).containsExactly(OpenMeteoWeatherProvider.CURRENT_NOTE);
        assertThat(r.current().time()).isEqualTo(OffsetDateTime.parse("2026-09-29T14:00+05:30"));
        assertThat(r.current().temperatureC()).isEqualTo(31.2);
        assertThat(r.current().windSpeedKmh()).as("omitted stays null, never zero").isNull();
        assertThat(r.daily()).hasSize(2);
        assertThat(r.daily().get(1).date()).isEqualTo(LocalDate.parse("2026-09-30"));
        assertThat(r.daily().get(1).maxTemperatureC()).isNull();
        assertThat(r.daily().get(1).precipitationMm()).isEqualTo(70.1);
        server.verify();
    }

    @Test
    void timeoutAndConnectionFailureAreUnavailable() {
        server.expect(requestTo(startsWith("http://weather.test"))).andRespond(withException(new SocketTimeoutException()));
        assertCode(() -> provider.fetch(26.85, 80.95, 2), "UPSTREAM_UNAVAILABLE", 503);

        setUp();
        server.expect(requestTo(startsWith("http://weather.test"))).andRespond(withException(new ConnectException()));
        assertCode(() -> provider.fetch(26.85, 80.95, 2), "UPSTREAM_UNAVAILABLE", 503);
    }

    @Test
    void rateLimitIsReportedAsSuch() {
        server.expect(requestTo(startsWith("http://weather.test"))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertCode(() -> provider.fetch(26.85, 80.95, 2), "UPSTREAM_RATE_LIMITED", 503);
    }

    @Test
    void upstreamErrorStatusIsAnInvalidResponse() {
        server.expect(requestTo(startsWith("http://weather.test"))).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertCode(() -> provider.fetch(26.85, 80.95, 2), "UPSTREAM_INVALID_RESPONSE", 502);

        setUp();
        server.expect(requestTo(startsWith("http://weather.test"))).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .body("{\"error\":true,\"reason\":\"bad\"}").contentType(MediaType.APPLICATION_JSON));
        assertCode(() -> provider.fetch(26.85, 80.95, 2), "UPSTREAM_INVALID_RESPONSE", 502);
    }

    @Test
    void malformedJsonIsAnInvalidResponse() {
        respond("{ not json");
        assertCode(() -> provider.fetch(26.85, 80.95, 2), "UPSTREAM_INVALID_RESPONSE", 502);
    }

    @Test
    void missingBlocksAreAnInvalidResponse() {
        respond("{\"utc_offset_seconds\": 19800, \"daily\": {\"time\": [\"2026-09-29\"]}}");
        assertCode(() -> provider.fetch(26.85, 80.95, 1), "UPSTREAM_INVALID_RESPONSE", 502);
    }

    @Test
    void arrayLengthMismatchIsAnInvalidResponse() {
        respond(BODY.replace("\"precipitation_sum\": [2.4, 70.1]", "\"precipitation_sum\": [2.4]"));
        assertCode(() -> provider.fetch(26.85, 80.95, 2), "UPSTREAM_INVALID_RESPONSE", 502);

        setUp();
        respond(BODY); // two days returned, three asked for
        assertCode(() -> provider.fetch(26.85, 80.95, 3), "UPSTREAM_INVALID_RESPONSE", 502);
    }

    @Test
    void missingDailyArrayIsAnInvalidResponse() {
        respond(BODY.replace("\"relative_humidity_2m_mean\": [71, 80]", "\"unrelated\": [1]"));
        assertCode(() -> provider.fetch(26.85, 80.95, 2), "UPSTREAM_INVALID_RESPONSE", 502);
    }

    private static void assertCode(Runnable call, String code, int status) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(WeatherProviderException.class, ex -> {
            assertThat(ex.getCode()).isEqualTo(code);
            assertThat(ex.getStatus().value()).isEqualTo(status);
            assertThat(ex.getMessage()).contains("Open-Meteo").doesNotContain("31.2");
        });
    }
}
