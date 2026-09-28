package com.argiintelligence.backend.weather;

import com.argiintelligence.backend.TestcontainersConfiguration;
import com.argiintelligence.backend.auth.AuthTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The real application context: no WeatherProvider is registered, so weather must be reported as unavailable. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WeatherControllerIntegrationTest {

    static final String AUTH = "Authorization";
    static final String FARM = """
            { "name": "Weather Farm", "area": 2, "areaUnit": "HECTARE", "irrigationType": "DRIP", "season": "RABI",
              "location": { "latitude": 12.72, "longitude": 77.28, "state": "Karnataka", "district": "Ramanagara" } }
            """;

    @Autowired
    MockMvc mvc;

    private String bearer;

    @BeforeEach
    void signIn() throws Exception {
        bearer = AuthTestSupport.newUserBearer(mvc);
    }

    @Test
    void pointWeatherIsUnavailableWithoutAProvider() throws Exception {
        mvc.perform(get("/api/weather").header(AUTH, bearer).param("latitude", "12.72").param("longitude", "77.28"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("WEATHER_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("No weather data source is configured"))
                .andExpect(jsonPath("$.current").doesNotExist())
                .andExpect(jsonPath("$.daily").doesNotExist());
    }

    @Test
    void farmWeatherIsUnavailableWithoutAProvider() throws Exception {
        String farmId = createFarm(mvc, bearer);
        mvc.perform(get("/api/weather/farms/{id}", farmId).header(AUTH, bearer))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("WEATHER_UNAVAILABLE"));
    }

    @Test
    void ownershipIsCheckedBeforeAvailability() throws Exception {
        String othersFarm = createFarm(mvc, AuthTestSupport.newUserBearer(mvc));
        mvc.perform(get("/api/weather/farms/{id}", othersFarm).header(AUTH, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_NOT_FOUND"));
        mvc.perform(get("/api/weather/farms/{id}", UUID.randomUUID()).header(AUTH, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_NOT_FOUND"));
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
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("temperature"))));
    }

    static String createFarm(MockMvc mvc, String auth) throws Exception {
        String body = mvc.perform(post("/api/farms").header(AUTH, auth).contentType(MediaType.APPLICATION_JSON).content(FARM))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
