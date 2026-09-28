package com.argiintelligence.backend.intelligence;

import com.argiintelligence.backend.TestcontainersConfiguration;
import com.argiintelligence.backend.auth.AuthTestSupport;
import com.argiintelligence.backend.ml.MlClient;
import com.argiintelligence.backend.ml.MlServiceException;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse;
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

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The ML HTTP layer is mocked at MlClient; its own HTTP handling is covered by MlClientTest. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IntelligenceControllerIntegrationTest {

    private static final String AUTH = "Authorization";
    private static final String FARM = """
            { "name": "Intel Farm", "area": 2, "areaUnit": "HECTARE", "irrigationType": "DRIP", "season": "RABI",
              "location": { "latitude": 12.72, "longitude": 77.28, "state": "Karnataka", "district": "Ramanagara" } }
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    MlClient mlClient;

    private String bearer;

    @BeforeEach
    void signIn() throws Exception {
        bearer = AuthTestSupport.newUserBearer(mvc);
    }

    // ---------- supply forecast (ML-backed) ----------

    @Test
    void supplyForecastPassesThroughModelPrediction() throws Exception {
        when(mlClient.predictSupply(Map.of("regionId", "ka", "cropId", "tomato", "horizonMonths", 6)))
                .thenReturn(new SupplyPredictionResponse("supply-v0.1",
                        new SupplyPredictionResponse.Prediction(new BigDecimal("812.4"), "tonnes", "2026-10/2027-03"),
                        new SupplyPredictionResponse.Provenance("ds-1", "f-1", "2026-09-20T10:00:00Z")));

        mvc.perform(get("/api/intelligence/supply-forecast").header(AUTH, bearer)
                        .param("regionId", "ka").param("cropId", "tomato").param("horizonMonths", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regionId").value("ka"))
                .andExpect(jsonPath("$.cropId").value("tomato"))
                .andExpect(jsonPath("$.horizonMonths").value(6))
                .andExpect(jsonPath("$.predictedSupply").value(812.4))
                .andExpect(jsonPath("$.unit").value("tonnes"))
                .andExpect(jsonPath("$.forecastPeriod").value("2026-10/2027-03"))
                .andExpect(jsonPath("$.provenance.source").value("ML_SERVICE"))
                .andExpect(jsonPath("$.provenance.dataClassification").value("MODEL_PREDICTION"))
                .andExpect(jsonPath("$.provenance.generatedAt").isNotEmpty())
                .andExpect(jsonPath("$.provenance.modelVersion").value("supply-v0.1"))
                .andExpect(jsonPath("$.provenance.confidence").value(nullValue()))
                .andExpect(jsonPath("$.provenance.limitations").isArray())
                .andExpect(jsonPath("$.modelProvenance.datasetVersion").value("ds-1"))
                .andExpect(jsonPath("$.modelProvenance.featureVersion").value("f-1"))
                .andExpect(jsonPath("$.modelProvenance.trainedAt").value("2026-09-20T10:00:00Z"));
    }

    @Test
    void supplyForecastWhenMlUnavailableIs503() throws Exception {
        when(mlClient.predictSupply(any())).thenThrow(MlServiceException.unavailable());
        mvc.perform(get("/api/intelligence/supply-forecast").header(AUTH, bearer).param("regionId", "ka").param("cropId", "tomato"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ML_SERVICE_UNAVAILABLE"));
    }

    @Test
    void supplyForecastWhenMlMalformedIs502() throws Exception {
        when(mlClient.predictSupply(any())).thenThrow(MlServiceException.badResponse());
        mvc.perform(get("/api/intelligence/supply-forecast").header(AUTH, bearer).param("regionId", "ka").param("cropId", "tomato"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ML_SERVICE_ERROR"));
    }

    @Test
    void supplyForecastValidatesParameters() throws Exception {
        mvc.perform(get("/api/intelligence/supply-forecast").header(AUTH, bearer)
                        .param("regionId", "KA!").param("cropId", "tomato").param("horizonMonths", "13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[*].field", hasItem("regionId")))
                .andExpect(jsonPath("$.details[*].field", hasItem("horizonMonths")));
        verify(mlClient, never()).predictSupply(any());
    }

    @Test
    void missingCropIsRejected() throws Exception {
        mvc.perform(get("/api/intelligence/supply-forecast").header(AUTH, bearer).param("regionId", "ka"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("cropId"));
    }

    // ---------- endpoints without a prediction source ----------

    @Test
    void unconnectedEndpointsReturnPredictionUnavailable() throws Exception {
        for (String path : new String[]{"demand-forecast", "supply-demand", "agricultural-risk"}) {
            mvc.perform(get("/api/intelligence/" + path).header(AUTH, bearer).param("regionId", "ka").param("cropId", "onion"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("PREDICTION_UNAVAILABLE"))
                    .andExpect(jsonPath("$.path").value("/api/intelligence/" + path));
        }
    }

    @Test
    void unconnectedEndpointsStillValidate() throws Exception {
        mvc.perform(get("/api/intelligence/demand-forecast").header(AUTH, bearer).param("regionId", "ka").param("cropId", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void cropRecommendationsForOwnFarmIsUnavailable() throws Exception {
        String farmId = createFarm(bearer);
        mvc.perform(get("/api/intelligence/crop-recommendations").header(AUTH, bearer).param("farmId", farmId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PREDICTION_UNAVAILABLE"));
    }

    @Test
    void cropRecommendationsForOtherUsersFarmIsNotFound() throws Exception {
        String farmId = createFarm(AuthTestSupport.newUserBearer(mvc));
        mvc.perform(get("/api/intelligence/crop-recommendations").header(AUTH, bearer).param("farmId", farmId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FARM_NOT_FOUND"));
    }

    @Test
    void cropRecommendationsForUnknownOrMalformedFarm() throws Exception {
        mvc.perform(get("/api/intelligence/crop-recommendations").header(AUTH, bearer).param("farmId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/intelligence/crop-recommendations").header(AUTH, bearer).param("farmId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void everyEndpointRequiresAuthentication() throws Exception {
        for (String path : new String[]{"supply-forecast", "demand-forecast", "supply-demand", "agricultural-risk", "crop-recommendations"}) {
            mvc.perform(get("/api/intelligence/" + path).param("regionId", "ka").param("cropId", "onion"))
                    .andExpect(status().isUnauthorized());
        }
        verify(mlClient, never()).predictSupply(any());
    }

    private String createFarm(String auth) throws Exception {
        String body = mvc.perform(post("/api/farms").header(AUTH, auth).contentType(MediaType.APPLICATION_JSON).content(FARM))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
