package com.argiintelligence.backend.intelligence;

import com.argiintelligence.backend.TestcontainersConfiguration;
import com.argiintelligence.backend.auth.AuthTestSupport;
import com.argiintelligence.backend.common.api.DataClassification;
import com.argiintelligence.backend.ml.MlClient;
import com.argiintelligence.backend.ml.MlServiceException;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse;
import com.argiintelligence.backend.reference.ReferenceTestSupport;
import com.argiintelligence.backend.reference.service.ReferenceSyncService;
import com.argiintelligence.backend.weather.provider.WeatherProvider;
import com.argiintelligence.backend.weather.provider.WeatherProviderException;
import com.jayway.jsonpath.JsonPath;
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
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
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
 * Crop evidence (MASTER_SPEC §10) and risk (§11) end to end with ML and the weather provider mocked. The crop
 * requirement values written in {@link #fixtureRequirements()} are TEST FIXTURES chosen to exercise each rule, not
 * FAO EcoCrop data; they are reset to NULL after every test (production keeps them NULL until B2 is resolved).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CropEvidenceRiskIntegrationTest {

    private static final String AUTH = "Authorization";
    private static final AtomicInteger LATITUDE = new AtomicInteger(0);

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
        when(weather.name()).thenReturn("OPEN_METEO");
        when(mlClient.predictSupply(any())).thenReturn(golden());
        fixtureRequirements();
    }

    @AfterEach
    void resetRequirements() {
        jdbc.update("update crop_requirement set ph_abs_min = null, ph_opt_min = null, ph_opt_max = null, "
                + "ph_abs_max = null, temp_abs_min_c = null, temp_opt_min_c = null, temp_opt_max_c = null, "
                + "temp_abs_max_c = null");
    }

    /** Wheat: pH 6.5 optimal. Potato: pH 6.5 only tolerable. Temperatures bracket a 15-28 °C forecast. */
    private void fixtureRequirements() {
        jdbc.update("update crop_requirement set ph_abs_min = 5.0, ph_opt_min = 6.0, ph_opt_max = 7.5, "
                + "ph_abs_max = 8.5, temp_abs_min_c = 0, temp_opt_min_c = 10, temp_opt_max_c = 30, "
                + "temp_abs_max_c = 40 where crop_id = 'wheat'");
        jdbc.update("update crop_requirement set ph_abs_min = 4.5, ph_opt_min = 5.0, ph_opt_max = 6.0, "
                + "ph_abs_max = 8.0, temp_abs_min_c = 0, temp_opt_min_c = 10, temp_opt_max_c = 30, "
                + "temp_abs_max_c = 40 where crop_id = 'potato'");
    }

    @Test
    void cropsAreTieredAndOrderedWithReasonsAndNoSingleScore() throws Exception {
        double lat = forecast(15.0, 28.0, 1.0);
        String farm = farm(bearer, lat, "\"up-agra\"", "RABI", true);

        mvc.perform(get("/api/farms/{id}/crop-evidence", farm).header(AUTH, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rankingRule").value("crop-evidence-v1"))
                .andExpect(jsonPath("$.districtId").value("up-agra"))
                .andExpect(jsonPath("$.season").value("RABI"))
                .andExpect(jsonPath("$.candidates[0].cropId").value("wheat"))
                .andExpect(jsonPath("$.candidates[0].rank").value(1))
                .andExpect(jsonPath("$.candidates[0].tier").value("SUITABLE"))
                .andExpect(jsonPath("$.candidates[0].evidence.soilCompatibility.status").value("OPTIMAL"))
                .andExpect(jsonPath("$.candidates[0].evidence.soilCompatibility.provenance.dataClassification")
                        .value("OBSERVED"))
                .andExpect(jsonPath("$.candidates[0].evidence.weatherSuitability.status").value("WITHIN_OPTIMAL"))
                .andExpect(jsonPath("$.candidates[0].evidence.weatherSuitability.provenance.dataClassification")
                        .value("FORECAST"))
                .andExpect(jsonPath("$.candidates[0].evidence.productionEvidence.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.candidates[0].evidence.productionEvidence.cropYear").value(2015))
                .andExpect(jsonPath("$.candidates[0].evidence.productionEvidence.dataThrough").value("2014"))
                .andExpect(jsonPath("$.candidates[0].evidence.marketContext.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.candidates[0].evidence.productionRisk").value("LOW"))
                .andExpect(jsonPath("$.candidates[0].reasons[*].code", hasItem("SOIL_PH_OPTIMAL")))
                .andExpect(jsonPath("$.candidates[0].unavailable", hasItem("MARKET_CONTEXT")))
                .andExpect(jsonPath("$.candidates[0].limitations", hasItem(
                        "Weather suitability reflects the next 7 days of forecast, not the whole season.")))
                .andExpect(jsonPath("$.candidates[0].score").doesNotExist())
                .andExpect(jsonPath("$.candidates[1].cropId").value("potato"))
                .andExpect(jsonPath("$.candidates[1].rank").value(2))
                .andExpect(jsonPath("$.candidates[1].tier").value("SUITABLE_WITH_CAUTION"))
                .andExpect(jsonPath("$.candidates[1].evidence.soilCompatibility.status").value("TOLERABLE"))
                // maize has only a KHARIF series, onion none: listed, not ranked.
                .andExpect(jsonPath("$.candidates[2].tier").value("NOT_SUPPORTED"))
                .andExpect(jsonPath("$.candidates[2].rank").value(nullValue()))
                .andExpect(jsonPath("$.candidates[2].reasons[0].code").value("SERIES_NOT_SUPPORTED"))
                .andExpect(jsonPath("$.candidates[3].tier").value("NOT_SUPPORTED"));
    }

    @Test
    void criticalWeatherMakesACropUnsuitable() throws Exception {
        double lat = forecast(15.0, 28.0, 120.0); // IMD "very heavy" rain on every day
        String farm = farm(bearer, lat, "\"up-agra\"", "RABI", true);
        mvc.perform(get("/api/farms/{id}/crop-evidence", farm).header(AUTH, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[0].tier").value("UNSUITABLE"))
                .andExpect(jsonPath("$.candidates[0].evidence.productionRisk").value("CRITICAL"));
    }

    @Test
    void missingRequirementsAreUnavailableNeverInvented() throws Exception {
        resetRequirements(); // the production state while blocker B2 is open
        double lat = forecast(15.0, 28.0, 1.0);
        String farm = farm(bearer, lat, "\"up-agra\"", "RABI", true);
        mvc.perform(get("/api/farms/{id}/crop-evidence", farm).header(AUTH, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[0].evidence.soilCompatibility.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.candidates[0].evidence.soilCompatibility.value").value(nullValue()))
                .andExpect(jsonPath("$.candidates[0].evidence.weatherSuitability.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.candidates[0].unavailable",
                        hasItems("SOIL_COMPATIBILITY", "WEATHER_SUITABILITY", "MARKET_CONTEXT")));

        mvc.perform(get("/api/farms/{id}/risk", farm).header(AUTH, bearer).param("cropId", "wheat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productionRisk.unavailableFactors", hasItems("TEMPERATURE_STRESS", "SOIL_PH")))
                .andExpect(jsonPath("$.productionRisk.factors[*].code", not(hasItem("SOIL_PH"))));
    }

    @Test
    void upstreamFailuresDegradeEvidenceInsteadOfFailingTheRequest() throws Exception {
        double lat = nextLatitude();
        when(weather.fetch(eq(lat), anyDouble(), anyInt())).thenThrow(WeatherProviderException.unavailable("Open-Meteo"));
        when(mlClient.predictSupply(any())).thenThrow(MlServiceException.predictionUnavailable());
        String farm = farm(bearer, lat, "\"up-agra\"", "RABI", true);

        mvc.perform(get("/api/farms/{id}/crop-evidence", farm).header(AUTH, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[0].evidence.productionEvidence.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.candidates[0].evidence.weatherSuitability.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.candidates[0].unavailable",
                        hasItems("WEATHER_SUITABILITY", "PRODUCTION_EVIDENCE")))
                .andExpect(jsonPath("$.candidates[0].reasons[*].text",
                        hasItem("Production evidence is unavailable (ML_PREDICTION_UNAVAILABLE).")));

        mvc.perform(get("/api/farms/{id}/risk", farm).header(AUTH, bearer).param("cropId", "wheat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productionRisk.unavailableFactors",
                        hasItems("HEAVY_RAINFALL", "YIELD_VARIABILITY", "DOWNSIDE_FREQUENCY")))
                .andExpect(jsonPath("$.productionRisk.limitations",
                        hasItem("Weather forecast unavailable (UPSTREAM_UNAVAILABLE).")));
    }

    @Test
    void riskKeepsProductionAndMarketSeparate() throws Exception {
        double lat = forecast(15.0, 28.0, 70.0); // IMD "heavy" rain → HIGH
        String farm = farm(bearer, lat, "\"up-agra\"", "RABI", true);
        mvc.perform(get("/api/farms/{id}/risk", farm).header(AUTH, bearer).param("cropId", "wheat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleSet").value("risk-rules-v1"))
                .andExpect(jsonPath("$.season").value("RABI"))
                .andExpect(jsonPath("$.cropYear").value(2015))
                .andExpect(jsonPath("$.productionRisk.level").value("HIGH"))
                .andExpect(jsonPath("$.productionRisk.assessedFactors").value(5))
                .andExpect(jsonPath("$.productionRisk.factors[?(@.code == 'HEAVY_RAINFALL')].level").value("HIGH"))
                .andExpect(jsonPath("$.productionRisk.factors[?(@.code == 'HEAVY_RAINFALL')].dataClassification")
                        .value("FORECAST"))
                .andExpect(jsonPath("$.productionRisk.factors[?(@.code == 'YIELD_VARIABILITY')].level").value("LOW"))
                .andExpect(jsonPath("$.productionRisk.factors[?(@.code == 'YIELD_VARIABILITY')].dataClassification")
                        .value(DataClassification.OBSERVED.name()))
                .andExpect(jsonPath("$.productionRisk.factors[?(@.code == 'SOIL_PH')].level").value("LOW"))
                .andExpect(jsonPath("$.marketRisk.level").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.marketRisk.factors").isEmpty())
                .andExpect(jsonPath("$.marketRisk.unavailableFactors", hasItems("PRICE_ANOMALY", "SUPPLY_PRESSURE")))
                .andExpect(jsonPath("$.marketRisk.limitations[0]").value("Market data is not connected (milestone M4)"))
                .andExpect(jsonPath("$.score").doesNotExist());
    }

    @Test
    void outOfScopeFarmsAndSeasonsAreRejected() throws Exception {
        String noDistrict = farm(bearer, forecast(15.0, 28.0, 1.0), "null", "RABI", true);
        mvc.perform(get("/api/farms/{id}/crop-evidence", noDistrict).header(AUTH, bearer))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_INPUT"))
                .andExpect(jsonPath("$.details[0].field").value("districtId"));

        String otherSeason = farm(bearer, forecast(15.0, 28.0, 1.0), "\"up-agra\"", "OTHER", true);
        mvc.perform(get("/api/farms/{id}/risk", otherSeason).header(AUTH, bearer).param("cropId", "wheat"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.details[0].field").value("season"));
        mvc.perform(get("/api/farms/{id}/risk", otherSeason).header(AUTH, bearer).param("cropId", "wheat")
                        .param("season", "RABI"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/farms/{id}/risk", otherSeason).header(AUTH, bearer).param("cropId", "tomato")
                        .param("season", "RABI"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.details[0].field").value("cropId"));
        mvc.perform(get("/api/farms/{id}/crop-evidence", otherSeason).header(AUTH, bearer).param("season", "MONSOON"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void anotherUsersFarmIsNotFoundAndNoUpstreamIsCalled() throws Exception {
        String theirs = farm(AuthTestSupport.newUserBearer(mvc), nextLatitude(), "\"up-agra\"", "RABI", true);
        mvc.perform(get("/api/farms/{id}/crop-evidence", theirs).header(AUTH, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_NOT_FOUND"));
        mvc.perform(get("/api/farms/{id}/risk", theirs).header(AUTH, bearer).param("cropId", "wheat"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/farms/{id}/risk", UUID.randomUUID()).header(AUTH, bearer).param("cropId", "wheat"))
                .andExpect(status().isNotFound());
        verify(weather, never()).fetch(anyDouble(), anyDouble(), anyInt());
        verify(mlClient, never()).predictSupply(any());
    }

    // ---- fixtures ----

    /** Stubs a 7-day forecast (fixture values) for a fresh latitude and returns that latitude. */
    private double forecast(double min, double max, double rainMm) {
        double lat = nextLatitude();
        List<WeatherProvider.Day> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            days.add(new WeatherProvider.Day(LocalDate.parse("2026-09-29").plusDays(i), min, max, rainMm, null, null));
        }
        when(weather.fetch(eq(lat), anyDouble(), eq(7))).thenReturn(new WeatherProvider.Report("OPEN_METEO",
                new WeatherProvider.Current(null, null, null, null, null), DataClassification.ESTIMATED, List.of(),
                days, DataClassification.FORECAST));
        return lat;
    }

    /** A unique latitude per farm, so the shared weather cache never serves another test's forecast. */
    private static double nextLatitude() {
        return 20.0 + LATITUDE.incrementAndGet() / 100.0;
    }

    private String farm(String auth, double latitude, String districtJson, String season, boolean withSoil)
            throws Exception {
        String soil = withSoil
                ? ", \"soilProfile\": { \"ph\": 6.5, \"source\": \"LAB_REPORT\", \"dataClassification\": \"OBSERVED\" }"
                : "";
        String body = """
                { "name": "Evidence Farm", "area": 2, "areaUnit": "HECTARE", "irrigationType": "CANAL",
                  "season": "%s", "districtId": %s,
                  "location": { "latitude": %s, "longitude": 77.28, "state": "Uttar Pradesh", "district": "Agra" }%s }
                """.formatted(season, districtJson, latitude, soil);
        String created = mvc.perform(post("/api/farms").header(AUTH, auth).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.id");
    }

    /** The golden ML response as a real MODEL-served artifact returns it. */
    private static SupplyPredictionResponse golden() throws IOException {
        try (InputStream in = CropEvidenceRiskIntegrationTest.class
                .getResourceAsStream("/contracts/ml/supply-response.json")) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\"SYNTHETIC\"", "\"OBSERVED\"")
                    .replace("\"BASELINE\"", "\"MODEL\"").replace("\"ESTIMATED\"", "\"MODEL_PREDICTION\"");
            return JsonMapper.builder().build().readValue(json, SupplyPredictionResponse.class);
        }
    }
}
