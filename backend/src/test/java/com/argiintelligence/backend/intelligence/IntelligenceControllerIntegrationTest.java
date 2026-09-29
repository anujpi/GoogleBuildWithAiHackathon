package com.argiintelligence.backend.intelligence;

import com.argiintelligence.backend.TestcontainersConfiguration;
import com.argiintelligence.backend.auth.AuthTestSupport;
import com.argiintelligence.backend.ml.MlClient;
import com.argiintelligence.backend.ml.MlServiceException;
import com.argiintelligence.backend.ml.dto.MlSupplyRequest;
import com.argiintelligence.backend.reference.ReferenceTestSupport;
import com.argiintelligence.backend.reference.service.ReferenceSyncService;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Supply (MASTER_SPEC §8) with MlClient mocked; its HTTP handling is covered by MlClientTest. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IntelligenceControllerIntegrationTest {

    private static final String AUTH = "Authorization";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    MlClient mlClient;

    @Autowired
    ReferenceSyncService referenceSync;

    private String bearer;

    @BeforeEach
    void signIn() throws Exception {
        bearer = AuthTestSupport.newUserBearer(mvc);
        // Scope comes from the synced reference tables, as in production (MASTER_SPEC §6.3).
        ReferenceTestSupport.sync(mlClient, referenceSync);
    }

    /** The golden ML response (src/test/resources/contracts/ml) as a real artifact serves it. */
    private static SupplyPredictionResponse ml(boolean model) throws IOException {
        try (InputStream in = IntelligenceControllerIntegrationTest.class
                .getResourceAsStream("/contracts/ml/supply-response.json")) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\"SYNTHETIC\"", "\"OBSERVED\"");
            if (model) {
                json = json.replace("\"BASELINE\"", "\"MODEL\"").replace("\"ESTIMATED\"", "\"MODEL_PREDICTION\"");
            }
            return JsonMapper.builder().build().readValue(json, SupplyPredictionResponse.class);
        }
    }

    private static MockHttpServletRequestBuilder supply(String cropYear) {
        return get("/api/intelligence/supply").param("districtId", "up-agra").param("cropId", "potato")
                .param("season", "RABI").param("cropYear", cropYear);
    }

    @Test
    void supplyReturnsTheEstimateWithLabelsProvenanceAndVintage() throws Exception {
        when(mlClient.predictSupply(new MlSupplyRequest("up-agra", "potato", "RABI", 2015, null))).thenReturn(ml(true));

        mvc.perform(supply("2015").header(AUTH, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target.districtLabel").value("Agra"))
                .andExpect(jsonPath("$.target.cropLabel").value("Potato"))
                .andExpect(jsonPath("$.target.cropYear").value(2015))
                .andExpect(jsonPath("$.estimate.production.unit").value("TONNES"))
                .andExpect(jsonPath("$.estimate.production.interval.nominalCoverage").value(0.8))
                .andExpect(jsonPath("$.estimate.yield.unit").value("TONNES_PER_HECTARE"))
                .andExpect(jsonPath("$.estimate.servedMethod").value("MODEL"))
                .andExpect(jsonPath("$.estimate.provenance").doesNotExist())
                .andExpect(jsonPath("$.history.points[0].yield").isNumber())
                .andExpect(jsonPath("$.provenance.source").value("ML_SERVICE"))
                .andExpect(jsonPath("$.provenance.dataClassification").value("MODEL_PREDICTION"))
                .andExpect(jsonPath("$.provenance.featureVersion").value("supply-features-v2"))
                .andExpect(jsonPath("$.provenance.dataThrough").value("2014"))
                .andExpect(jsonPath("$.provenance.generatedAt").isNotEmpty())
                .andExpect(jsonPath("$.provenance.retrievedAt").isNotEmpty())
                .andExpect(jsonPath("$.historyProvenance.source").value("DES_S01_DATA_GOV_IN"))
                .andExpect(jsonPath("$.historyProvenance.dataClassification").value("OBSERVED"))
                .andExpect(jsonPath("$.historyProvenance.modelVersion").value(nullValue()))
                .andExpect(jsonPath("$.modelEvaluation.testPeriod").value("2013-2014"))
                .andExpect(jsonPath("$.limitations", hasItem(
                        "Data ends in crop year 2014; this is not a current-season forecast.")));
    }

    @Test
    void servedBaselineIsDisclosed() throws Exception {
        when(mlClient.predictSupply(any())).thenReturn(ml(false));
        mvc.perform(supply("2015").header(AUTH, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provenance.dataClassification").value("ESTIMATED"))
                .andExpect(jsonPath("$.limitations", hasItem(
                        "The trained model did not beat the baseline; the baseline estimate is served.")));
    }

    @Test
    void outOfScopeInputIsRejectedWithoutCallingThePredictor() throws Exception {
        mvc.perform(get("/api/intelligence/supply").header(AUTH, bearer).param("districtId", "ka-ramanagara")
                        .param("cropId", "potato").param("season", "RABI").param("cropYear", "2015"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_INPUT"))
                .andExpect(jsonPath("$.details[0].field").value("districtId"));
        mvc.perform(get("/api/intelligence/supply").header(AUTH, bearer).param("districtId", "up-agra")
                        .param("cropId", "tomato").param("season", "RABI").param("cropYear", "2015"))
                .andExpect(jsonPath("$.details[0].field").value("cropId"));
        mvc.perform(get("/api/intelligence/supply").header(AUTH, bearer).param("districtId", "up-agra")
                        .param("cropId", "potato").param("season", "KHARIF").param("cropYear", "2015"))
                .andExpect(jsonPath("$.details[0].field").value("season"));
        mvc.perform(supply("2016").header(AUTH, bearer))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.details[0].field").value("cropYear"));
        verify(mlClient, never()).predictSupply(any());
    }

    @Test
    void mlFailuresKeepTheirCodes() throws Exception {
        when(mlClient.predictSupply(any())).thenThrow(MlServiceException.predictionUnavailable());
        mvc.perform(supply("2015").header(AUTH, bearer))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ML_PREDICTION_UNAVAILABLE"));

        doThrow(MlServiceException.unavailable()).when(mlClient).predictSupply(any());
        mvc.perform(supply("2015").header(AUTH, bearer))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ML_UNAVAILABLE"));
    }

    @Test
    void supplyValidatesParameters() throws Exception {
        mvc.perform(get("/api/intelligence/supply").header(AUTH, bearer).param("districtId", "UP Agra!")
                        .param("cropId", "potato").param("season", "MONSOON").param("cropYear", "3000")
                        .param("areaHectares", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details", hasSize(4)))
                .andExpect(jsonPath("$.details[*].field", hasItem("districtId")))
                .andExpect(jsonPath("$.details[*].field", hasItem("season")))
                .andExpect(jsonPath("$.details[*].field", hasItem("cropYear")))
                .andExpect(jsonPath("$.details[*].field", hasItem("areaHectares")));
        verify(mlClient, never()).predictSupply(any());
    }

    @Test
    void cropYearIsRequired() throws Exception {
        mvc.perform(get("/api/intelligence/supply").header(AUTH, bearer).param("districtId", "up-agra")
                        .param("cropId", "potato").param("season", "RABI"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("cropYear"));
    }

    @Test
    void removedEndpointsAreGone() throws Exception {
        for (String path : new String[]{"supply-forecast", "demand-forecast", "supply-demand",
                "crop-recommendations", "agricultural-risk"}) {
            mvc.perform(get("/api/intelligence/" + path).header(AUTH, bearer).param("regionId", "ka")
                            .param("cropId", "onion"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void supplyRequiresAuthentication() throws Exception {
        mvc.perform(supply("2015")).andExpect(status().isUnauthorized());
        verify(mlClient, never()).predictSupply(any());
    }
}
