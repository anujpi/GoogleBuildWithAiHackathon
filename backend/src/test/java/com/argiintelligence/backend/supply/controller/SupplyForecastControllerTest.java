package com.argiintelligence.backend.supply.controller;

import com.argiintelligence.backend.common.api.ApiError.FieldViolation;
import com.argiintelligence.backend.common.exception.GlobalExceptionHandler;
import com.argiintelligence.backend.ml.MlClient;
import com.argiintelligence.backend.ml.dto.MlSupplyResponse;
import com.argiintelligence.backend.ml.exception.MlInvalidResponseException;
import com.argiintelligence.backend.ml.exception.MlRequestRejectedException;
import com.argiintelligence.backend.ml.exception.MlUnavailableException;
import com.argiintelligence.backend.supply.entity.ProductionDataClassification;
import com.argiintelligence.backend.supply.entity.ProductionDataSource;
import com.argiintelligence.backend.supply.entity.ProductionHistory;
import com.argiintelligence.backend.supply.repository.ProductionHistoryRepository;
import com.argiintelligence.backend.supply.service.SupplyForecastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Security filters are off: this tests HTTP mapping only. Authentication is covered by SupplyForecastIntegrationTest.
@WebMvcTest(SupplyForecastController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, SupplyForecastService.class})
class SupplyForecastControllerTest {

    private static final String DATASET = "crop_yield-sha256-ab9bc356b1f8";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProductionHistoryRepository repository;

    @MockitoBean
    MlClient mlClient;

    @BeforeEach
    void realUpWheatHistory() {
        // Real Uttar Pradesh / Wheat / Rabi rows from crop_yield.csv.
        when(repository.findSeries("Uttar Pradesh", "Wheat", "Rabi", 2016, 2018)).thenReturn(List.of(
                row(2018, "9855900", "38039724"), row(2017, "9752941", "35645666"), row(2016, "9884913", "34971381")));
    }

    private static MockHttpServletRequestBuilder forecast(String season, String cropYear, String area) {
        MockHttpServletRequestBuilder req = get("/api/supply/forecast")
                .param("state", "Uttar Pradesh").param("crop", "Wheat").param("season", season);
        if (cropYear != null) {
            req.param("cropYear", cropYear);
        }
        if (area != null) {
            req.param("areaHectares", area);
        }
        return req;
    }

    @Test
    void success() throws Exception {
        when(mlClient.predictSupply(any())).thenReturn(new MlSupplyResponse("supply-production-xgb", "supply-xgb-v1",
                "MODEL_PREDICTION",
                new MlSupplyResponse.Prediction(new BigDecimal("37015561.210610636"), "tonnes",
                        "crop year 2019, Rabi season", new MlSupplyResponse.Interval(new BigDecimal("27285797.492844444"),
                        new BigDecimal("45232845.87415427"), new BigDecimal("0.8"), "empirical quantiles",
                        new BigDecimal("0.8192900681247759"))),
                new MlSupplyResponse.Evidence(new BigDecimal("36297631.55735549"), "area x mean yield", 3),
                new MlSupplyResponse.Provenance(DATASET, "supply-features-v1", "2026-09-28T14:45:37+00:00",
                        "<= 2013", "2017-2019", "Kaggle: Agricultural Crop Yield in Indian States Dataset (akshatgupta7)",
                        "state x crop x season x crop_year"),
                "2026-09-28T14:59:17.116786Z"));

        mvc.perform(forecast("RABI", "2019", "9852504"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("Uttar Pradesh"))
                .andExpect(jsonPath("$.season").value("RABI"))
                .andExpect(jsonPath("$.sourceSeasonLabel").value("Rabi"))
                .andExpect(jsonPath("$.targetAreaHectares").value(9852504))
                .andExpect(jsonPath("$.targetAreaSource").value("REQUEST_INPUT"))
                .andExpect(jsonPath("$.forecast.expectedProductionTonnes").value(37015561.210610636))
                .andExpect(jsonPath("$.forecast.dataClassification").value("MODEL_PREDICTION"))
                .andExpect(jsonPath("$.forecast.predictionInterval.nominalCoverage").value(0.8))
                .andExpect(jsonPath("$.baseline.productionTonnes").value(36297631.55735549))
                .andExpect(jsonPath("$.history", hasSize(3)))
                .andExpect(jsonPath("$.history[0].cropYear").value(2018))
                .andExpect(jsonPath("$.history[0].dataClassification").value("OBSERVED"))
                .andExpect(jsonPath("$.model.modelVersion").value("supply-xgb-v1"))
                .andExpect(jsonPath("$.model.trainingDataSource").value(containsString("Kaggle")))
                .andExpect(jsonPath("$.model.spatialGranularity").value("state x crop x season x crop_year"))
                .andExpect(jsonPath("$.limitations", hasSize(6)));
    }

    @Test
    void unsupportedSeason() throws Exception {
        mvc.perform(forecast("ZAID", "2019", "9852504"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_SEASON"))
                .andExpect(jsonPath("$.path").value("/api/supply/forecast"));
    }

    @Test
    void missingOrInvalidParametersAreValidationErrors() throws Exception {
        mvc.perform(forecast("RABI", "2019", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("areaHectares"));
        mvc.perform(forecast("RABI", "2019", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("areaHectares"));
        mvc.perform(forecast("RABI", "abc", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("cropYear"));
    }

    @Test
    void insufficientHistory() throws Exception {
        when(repository.findSeries("Uttar Pradesh", "Wheat", "Rabi", 2027, 2029)).thenReturn(List.of());
        when(repository.findSeriesYears("Uttar Pradesh", "Wheat", "Rabi")).thenReturn(List.of(1997, 2019));
        mvc.perform(forecast("RABI", "2030", "9852504"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_HISTORY"))
                .andExpect(jsonPath("$.message").value(containsString("crop year 2029")));
    }

    @Test
    void mlRejectionKeepsItsDetails() throws Exception {
        when(mlClient.predictSupply(any())).thenThrow(new MlRequestRejectedException(List.of(
                new FieldViolation("areaHectares", "Input should be greater than 0"),
                new FieldViolation(null, "unsupported crop 'Wheat '"))));
        mvc.perform(forecast("RABI", "2019", "9852504"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ML_REQUEST_REJECTED"))
                .andExpect(jsonPath("$.details[0].field").value("areaHectares"))
                .andExpect(jsonPath("$.details[1].field").value(nullValue()))
                .andExpect(jsonPath("$.details[1].message").value("unsupported crop 'Wheat '"));
    }

    @Test
    void mlUnavailable() throws Exception {
        when(mlClient.predictSupply(any())).thenThrow(MlUnavailableException.serviceUnreachable());
        mvc.perform(forecast("RABI", "2019", "9852504"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ML_SERVICE_UNAVAILABLE"));
    }

    @Test
    void mlMalformedResponse() throws Exception {
        when(mlClient.predictSupply(any())).thenThrow(new MlInvalidResponseException("bad body"));
        mvc.perform(forecast("RABI", "2019", "9852504"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ML_INVALID_RESPONSE"));
    }

    private static ProductionHistory row(int year, String area, String production) {
        return new ProductionHistory("Uttar Pradesh", "Wheat", "Rabi", year, new BigDecimal(area),
                new BigDecimal(production), ProductionDataSource.KAGGLE_CROP_YIELD,
                ProductionDataClassification.OBSERVED, DATASET);
    }
}
