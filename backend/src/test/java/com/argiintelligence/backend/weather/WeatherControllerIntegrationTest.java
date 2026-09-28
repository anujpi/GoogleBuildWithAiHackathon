package com.argiintelligence.backend.weather;

import com.argiintelligence.backend.TestcontainersConfiguration;
import com.argiintelligence.backend.auth.AuthTestSupport;
import com.argiintelligence.backend.weather.provider.MockWeatherProvider;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WeatherControllerIntegrationTest {

    private static final String AUTH = "Authorization";
    private static final String FARM = """
            { "name": "Weather Farm", "area": 2, "areaUnit": "HECTARE", "irrigationType": "DRIP", "season": "RABI",
              "location": { "latitude": 12.72, "longitude": 77.28, "state": "Karnataka", "district": "Ramanagara" } }
            """;

    @Autowired
    MockMvc mvc;

    @MockitoSpyBean
    MockWeatherProvider provider;

    private String bearer;

    @BeforeEach
    void signIn() throws Exception {
        bearer = AuthTestSupport.newUserBearer(mvc);
    }

    @Test
    void pointWeatherIsLabelledSynthetic() throws Exception {
        mvc.perform(get("/api/weather").header(AUTH, bearer).param("latitude", "12.72").param("longitude", "77.28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(nullValue()))
                .andExpect(jsonPath("$.latitude").value(12.72))
                .andExpect(jsonPath("$.provenance.source").value(MockWeatherProvider.SOURCE))
                .andExpect(jsonPath("$.provenance.dataClassification").value("SYNTHETIC"))
                .andExpect(jsonPath("$.provenance.retrievedAt").isNotEmpty())
                .andExpect(jsonPath("$.provenance.confidence").value(nullValue()))
                .andExpect(jsonPath("$.current.temperatureC").isNumber())
                .andExpect(jsonPath("$.daily", hasSize(7)));
    }

    @Test
    void daysParameterControlsForecastLength() throws Exception {
        mvc.perform(get("/api/weather").header(AUTH, bearer)
                        .param("latitude", "12.72").param("longitude", "77.28").param("days", "14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daily", hasSize(14)));
    }

    @Test
    void sameInputGivesSameValues() throws Exception {
        String a = dailyOf("12.72", "77.28");
        String b = dailyOf("12.72", "77.28");
        org.assertj.core.api.Assertions.assertThat(a).isEqualTo(b);
    }

    @Test
    void outOfRangeParametersAreRejected() throws Exception {
        mvc.perform(get("/api/weather").header(AUTH, bearer)
                        .param("latitude", "91").param("longitude", "77.28").param("days", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[*].field", hasItem("latitude")))
                .andExpect(jsonPath("$.details[*].field", hasItem("days")));
    }

    @Test
    void missingParameterIsRejected() throws Exception {
        mvc.perform(get("/api/weather").header(AUTH, bearer).param("latitude", "12.72"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("longitude"));
    }

    @Test
    void nonNumericParameterIsRejected() throws Exception {
        mvc.perform(get("/api/weather").header(AUTH, bearer).param("latitude", "abc").param("longitude", "77"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/weather").param("latitude", "12.72").param("longitude", "77.28"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void farmWeatherUsesFarmLocation() throws Exception {
        String farmId = createFarm(bearer);
        mvc.perform(get("/api/weather/farms/{id}", farmId).header(AUTH, bearer).param("days", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.farmId").value(farmId))
                .andExpect(jsonPath("$.latitude").value(12.72))
                .andExpect(jsonPath("$.longitude").value(77.28))
                .andExpect(jsonPath("$.provenance.dataClassification").value("SYNTHETIC"))
                .andExpect(jsonPath("$.daily", hasSize(3)));
    }

    @Test
    void otherUsersFarmIsNotFound() throws Exception {
        String farmId = createFarm(AuthTestSupport.newUserBearer(mvc));
        mvc.perform(get("/api/weather/farms/{id}", farmId).header(AUTH, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_NOT_FOUND"));
    }

    @Test
    void unknownFarmIsNotFound() throws Exception {
        mvc.perform(get("/api/weather/farms/{id}", UUID.randomUUID()).header(AUTH, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_NOT_FOUND"));
    }

    @Test
    void providerFailureReturns503() throws Exception {
        doThrow(new IllegalStateException("upstream down")).when(provider).fetch(anyDouble(), anyDouble(), anyInt());
        mvc.perform(get("/api/weather").header(AUTH, bearer).param("latitude", "12.72").param("longitude", "77.28"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("WEATHER_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Weather data is currently unavailable"));
    }

    private String dailyOf(String lat, String lon) throws Exception {
        String body = mvc.perform(get("/api/weather").header(AUTH, bearer).param("latitude", lat).param("longitude", lon))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body).read("$.daily").toString();
    }

    private String createFarm(String auth) throws Exception {
        String body = mvc.perform(post("/api/farms").header(AUTH, auth).contentType(MediaType.APPLICATION_JSON).content(FARM))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
