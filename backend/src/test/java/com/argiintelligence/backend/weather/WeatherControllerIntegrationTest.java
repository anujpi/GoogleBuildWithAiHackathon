package com.argiintelligence.backend.weather;

import com.argiintelligence.backend.TestcontainersConfiguration;
import com.argiintelligence.backend.auth.AuthTestSupport;
import com.argiintelligence.backend.common.api.DataClassification;
import com.argiintelligence.backend.weather.provider.WeatherProvider;
import com.argiintelligence.backend.weather.provider.WeatherProviderException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Weather endpoints (MASTER_SPEC §6.2, §6.4, §7) with the provider mocked; the real Open-Meteo client is covered by
 * OpenMeteoWeatherProviderTest. Each test uses its own coordinates because the service cache is shared.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WeatherControllerIntegrationTest {

    private static final String AUTH = "Authorization";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    WeatherProvider provider;

    private String bearer;

    @BeforeEach
    void signIn() throws Exception {
        bearer = AuthTestSupport.newUserBearer(mvc);
        when(provider.name()).thenReturn("OPEN_METEO");
    }

    /** A fixture in the provider's report format; never served outside tests. */
    static WeatherProvider.Report report() {
        return new WeatherProvider.Report("OPEN_METEO",
                new WeatherProvider.Current(OffsetDateTime.of(2026, 9, 29, 14, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)),
                        31.2, 64.0, 0.0, null),
                DataClassification.ESTIMATED, List.of("Model analysis for the grid cell, not a station observation."),
                List.of(new WeatherProvider.Day(LocalDate.parse("2026-09-29"), 24.1, 33.0, 2.4, 40.0, 71.0)),
                DataClassification.FORECAST);
    }

    @Test
    void pointWeatherHasSplitProvenanceAndKeepsNulls() throws Exception {
        when(provider.fetch(10.01, 77.01, 1)).thenReturn(report());
        mvc.perform(get("/api/weather").header(AUTH, bearer)
                        .param("latitude", "10.01").param("longitude", "77.01").param("days", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(nullValue()))
                .andExpect(jsonPath("$.current.time").value("2026-09-29T14:00:00+05:30"))
                .andExpect(jsonPath("$.current.temperatureC").value(31.2))
                .andExpect(jsonPath("$.current.windSpeedKmh").value(nullValue()))
                .andExpect(jsonPath("$.current.provenance.source").value("OPEN_METEO"))
                .andExpect(jsonPath("$.current.provenance.dataClassification").value("ESTIMATED"))
                .andExpect(jsonPath("$.current.provenance.notes[0]").value(
                        "Model analysis for the grid cell, not a station observation."))
                .andExpect(jsonPath("$.current.provenance.retrievedAt").isNotEmpty())
                .andExpect(jsonPath("$.daily", hasSize(1)))
                .andExpect(jsonPath("$.daily[0].precipitationMm").value(2.4))
                .andExpect(jsonPath("$.daily[0].precipitationProbabilityPct").value(40.0))
                .andExpect(jsonPath("$.dailyProvenance.dataClassification").value("FORECAST"))
                .andExpect(jsonPath("$.provenance").doesNotExist());
    }

    @Test
    void providerFailuresKeepTheirCodesAndCarryNoWeatherValues() throws Exception {
        when(provider.fetch(eq(10.02), anyDouble(), anyInt())).thenThrow(WeatherProviderException.unavailable("Open-Meteo"));
        mvc.perform(get("/api/weather").header(AUTH, bearer).param("latitude", "10.02").param("longitude", "77"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UPSTREAM_UNAVAILABLE"))
                .andExpect(jsonPath("$.current").doesNotExist());

        when(provider.fetch(eq(10.03), anyDouble(), anyInt())).thenThrow(WeatherProviderException.rateLimited("Open-Meteo"));
        mvc.perform(get("/api/weather").header(AUTH, bearer).param("latitude", "10.03").param("longitude", "77"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("UPSTREAM_RATE_LIMITED"));

        when(provider.fetch(eq(10.04), anyDouble(), anyInt())).thenThrow(WeatherProviderException.invalidResponse("Open-Meteo"));
        mvc.perform(get("/api/weather").header(AUTH, bearer).param("latitude", "10.04").param("longitude", "77"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_INVALID_RESPONSE"));
    }

    @Test
    void syntheticReportsAreRefused() throws Exception {
        WeatherProvider.Report r = report();
        when(provider.fetch(eq(10.05), anyDouble(), anyInt())).thenReturn(new WeatherProvider.Report(r.source(),
                r.current(), DataClassification.SYNTHETIC, r.currentNotes(), r.daily(), r.dailyClassification()));
        mvc.perform(get("/api/weather").header(AUTH, bearer).param("latitude", "10.05").param("longitude", "77"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_INVALID_RESPONSE"))
                .andExpect(jsonPath("$.current").doesNotExist());
    }

    @Test
    void invalidParametersAreRejectedBeforeTheProvider() throws Exception {
        mvc.perform(get("/api/weather").header(AUTH, bearer)
                        .param("latitude", "91").param("longitude", "77.28").param("days", "15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[*].field", hasItem("latitude")))
                .andExpect(jsonPath("$.details[*].field", hasItem("days")));
        mvc.perform(get("/api/weather").header(AUTH, bearer).param("latitude", "12.72"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("longitude"));
        mvc.perform(get("/api/weather").header(AUTH, bearer).param("latitude", "abc").param("longitude", "77"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verify(provider, never()).fetch(anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/weather").param("latitude", "12.72").param("longitude", "77.28"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void farmWeatherUsesTheFarmLocation() throws Exception {
        String farmId = createFarm(bearer, "10.06");
        when(provider.fetch(10.06, 77.28, 3)).thenReturn(report());
        mvc.perform(get("/api/farms/{id}/weather", farmId).header(AUTH, bearer).param("days", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(farmId))
                .andExpect(jsonPath("$.latitude").value(10.06))
                .andExpect(jsonPath("$.dailyProvenance.dataClassification").value("FORECAST"));
    }

    @Test
    void farmOwnershipIsCheckedBeforeTheProvider() throws Exception {
        String othersFarm = createFarm(AuthTestSupport.newUserBearer(mvc), "10.07");
        mvc.perform(get("/api/farms/{id}/weather", othersFarm).header(AUTH, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_NOT_FOUND"));
        mvc.perform(get("/api/farms/{id}/weather", UUID.randomUUID()).header(AUTH, bearer))
                .andExpect(status().isNotFound());
        verify(provider, never()).fetch(anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void theOldFarmWeatherPathIsGone() throws Exception {
        String farmId = createFarm(bearer, "10.08");
        mvc.perform(get("/api/weather/farms/{id}", farmId).header(AUTH, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private String createFarm(String auth, String latitude) throws Exception {
        String farm = """
                { "name": "Weather Farm", "area": 2, "areaUnit": "HECTARE", "irrigationType": "DRIP", "season": "RABI",
                  "location": { "latitude": %s, "longitude": 77.28, "state": "Karnataka", "district": "Ramanagara" } }
                """.formatted(latitude);
        String body = mvc.perform(post("/api/farms").header(AUTH, auth).contentType(MediaType.APPLICATION_JSON)
                        .content(farm))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
