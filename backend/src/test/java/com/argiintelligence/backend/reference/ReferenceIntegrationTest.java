package com.argiintelligence.backend.reference;

import com.argiintelligence.backend.TestcontainersConfiguration;
import com.argiintelligence.backend.auth.AuthTestSupport;
import com.argiintelligence.backend.ml.MlClient;
import com.argiintelligence.backend.ml.dto.ScopeResponse;
import com.argiintelligence.backend.reference.service.ReferenceSyncService;
import com.argiintelligence.backend.weather.provider.WeatherProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reference scope sync and serving (MASTER_SPEC §6.3) and the farm district field (§6.2). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReferenceIntegrationTest {

    private static final String AUTH = "Authorization";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReferenceSyncService referenceSync;

    @MockitoBean
    MlClient mlClient;

    @MockitoBean
    WeatherProvider weather;

    private String bearer;

    @BeforeEach
    void setUp() throws Exception {
        bearer = AuthTestSupport.newUserBearer(mvc);
        ReferenceTestSupport.sync(mlClient, referenceSync);
    }

    @AfterEach
    void restoreScope() {
        ReferenceTestSupport.sync(mlClient, referenceSync); // other test classes share this database
    }

    @Test
    void scopeIsEmptyUntilASyncHasSucceeded() throws Exception {
        jdbc.update("delete from reference_sync"); // as on a fresh install before the first sync
        mvc.perform(get("/api/reference/scope").header(AUTH, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncedAt").value(nullValue()))
                .andExpect(jsonPath("$.districts").isEmpty())
                .andExpect(jsonPath("$.crops").isEmpty())
                .andExpect(jsonPath("$.supplySeries").isEmpty());
    }

    @Test
    void scopeServesTheSyncedTablesWithEstimableYears() throws Exception {
        mvc.perform(get("/api/reference/scope").header(AUTH, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncedAt").isNotEmpty())
                .andExpect(jsonPath("$.states[0].stateId").value("up"))
                .andExpect(jsonPath("$.districts[*].districtId", hasItem("up-agra")))
                .andExpect(jsonPath("$.crops[*].cropId", hasItem("maize")))
                .andExpect(jsonPath("$.seasons", contains("KHARIF", "RABI")))
                .andExpect(jsonPath("$.supplySeries", hasSize(4)))
                .andExpect(jsonPath("$.supplySeries[?(@.districtId == 'up-agra' && @.cropId == 'potato')]"
                        + ".estimableYears[0]").value(1998))
                .andExpect(jsonPath("$.supplySeries[?(@.districtId == 'up-agra' && @.cropId == 'potato')]"
                        + ".estimableYears[17]").value(2015))
                .andExpect(jsonPath("$.datasets[0].dataThrough").value("2014"));
    }

    @Test
    void resyncReplacesSeriesButKeepsDistricts() throws Exception {
        ScopeResponse smaller = new ScopeResponse(ReferenceTestSupport.SCOPE.states(),
                List.of(new ScopeResponse.District("up-agra", "up", "Agra")), ReferenceTestSupport.SCOPE.crops(),
                List.of("RABI"), List.of(new ScopeResponse.SupplySeries("up-agra", "wheat", "RABI", 2000, 2014, 15)),
                List.of(), ReferenceTestSupport.SCOPE.datasets());
        when(mlClient.scope()).thenReturn(smaller);
        referenceSync.sync(null);

        mvc.perform(get("/api/reference/scope").header(AUTH, bearer))
                .andExpect(jsonPath("$.supplySeries", hasSize(1)))
                .andExpect(jsonPath("$.districts[*].districtId", hasItem("up-lucknow"))); // kept: farms may use it
        // A district without series is no longer "in scope" for intelligence.
        mvc.perform(post("/api/farms").header(AUTH, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(farm("\"up-lucknow\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.districtLabel").value("Lucknow"))
                .andExpect(jsonPath("$.intelligenceSupported").value(false));
    }

    @Test
    void farmDistrictMustBeASyncedDistrict() throws Exception {
        mvc.perform(post("/api/farms").header(AUTH, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(farm("\"up-agra\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.districtId").value("up-agra"))
                .andExpect(jsonPath("$.districtLabel").value("Agra"))
                .andExpect(jsonPath("$.intelligenceSupported").value(true));
        mvc.perform(post("/api/farms").header(AUTH, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(farm("null")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.districtId").value(nullValue()))
                .andExpect(jsonPath("$.intelligenceSupported").value(false));
        mvc.perform(post("/api/farms").header(AUTH, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(farm("\"mh-pune\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("districtId"));
        mvc.perform(post("/api/farms").header(AUTH, bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(farm("\"Up Agra\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("districtId")));
        mvc.perform(get("/api/farms").header(AUTH, bearer))
                .andExpect(jsonPath("$[*].districtId", not(hasItem("mh-pune"))));
    }

    private static String farm(String districtJson) {
        return """
                { "name": "District Farm", "area": 2, "areaUnit": "HECTARE", "irrigationType": "DRIP", "season": "RABI",
                  "districtId": %s,
                  "location": { "latitude": 26.9, "longitude": 80.9, "state": "Uttar Pradesh", "district": "Lucknow" } }
                """.formatted(districtJson);
    }
}
