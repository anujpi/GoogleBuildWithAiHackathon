package com.argiintelligence.backend.weather;

import com.argiintelligence.backend.TestcontainersConfiguration;
import com.argiintelligence.backend.auth.AuthTestSupport;
import com.argiintelligence.backend.common.api.DataClassification;
import com.argiintelligence.backend.weather.dto.WeatherResponse;
import com.argiintelligence.backend.weather.provider.WeatherProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.argiintelligence.backend.weather.WeatherControllerIntegrationTest.AUTH;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The provider boundary, with a test double standing in for a future real source. The fixture values below
 * exist only in this test; production has no provider and returns 503.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WeatherProviderIntegrationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    WeatherProvider provider;

    private String bearer;

    @BeforeEach
    void signIn() throws Exception {
        bearer = AuthTestSupport.newUserBearer(mvc);
    }

    @Test
    void providerReportIsServedWithItsOwnProvenance() throws Exception {
        when(provider.fetch(anyDouble(), anyDouble(), anyInt())).thenReturn(report(DataClassification.FORECAST, 0.8));
        mvc.perform(get("/api/weather").header(AUTH, bearer).param("latitude", "12.72").param("longitude", "77.28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(nullValue()))
                .andExpect(jsonPath("$.latitude").value(12.72))
                .andExpect(jsonPath("$.provenance.source").value("TEST_SOURCE"))
                .andExpect(jsonPath("$.provenance.dataClassification").value("FORECAST"))
                .andExpect(jsonPath("$.provenance.confidence").value(0.8))
                .andExpect(jsonPath("$.provenance.retrievedAt").isNotEmpty())
                .andExpect(jsonPath("$.current.temperatureC").value(27.0))
                .andExpect(jsonPath("$.daily", hasSize(1)));
    }

    @Test
    void farmWeatherQueriesTheProviderAtTheFarmLocation() throws Exception {
        when(provider.fetch(eq(12.72), eq(77.28), eq(3))).thenReturn(report(DataClassification.FORECAST, null));
        String farmId = WeatherControllerIntegrationTest.createFarm(mvc, bearer);
        mvc.perform(get("/api/weather/farms/{id}", farmId).header(AUTH, bearer).param("days", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(farmId))
                .andExpect(jsonPath("$.provenance.confidence").value(nullValue()));
    }

    @Test
    void providerFailureIsUnavailableNotFilledIn() throws Exception {
        when(provider.fetch(anyDouble(), anyDouble(), anyInt())).thenThrow(new IllegalStateException("upstream down"));
        mvc.perform(get("/api/weather").header(AUTH, bearer).param("latitude", "12.72").param("longitude", "77.28"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("WEATHER_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Weather data is currently unavailable"));
    }

    @Test
    void syntheticReportIsRefused() throws Exception {
        when(provider.fetch(anyDouble(), anyDouble(), anyInt())).thenReturn(report(DataClassification.SYNTHETIC, null));
        mvc.perform(get("/api/weather").header(AUTH, bearer).param("latitude", "12.72").param("longitude", "77.28"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("WEATHER_UNAVAILABLE"))
                .andExpect(jsonPath("$.current").doesNotExist());
    }

    private static WeatherProvider.Report report(DataClassification classification, Double confidence) {
        return new WeatherProvider.Report("TEST_SOURCE", classification, confidence,
                new WeatherResponse.Current(Instant.parse("2026-09-28T06:00:00Z"), 27.0, 60.0, 0.0, 10.0),
                List.of(new WeatherResponse.Daily(LocalDate.parse("2026-09-28"), 19.0, 30.0, 0.0, 20.0, 55.0)));
    }
}
