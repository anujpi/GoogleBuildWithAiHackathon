package com.argiintelligence.backend.supply;

import com.argiintelligence.backend.TestcontainersConfiguration;
import com.argiintelligence.backend.auth.AuthTestSupport;
import com.argiintelligence.backend.ml.MlClient;
import com.argiintelligence.backend.ml.dto.MlSupplyRequest;
import com.argiintelligence.backend.ml.dto.MlSupplyResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real PostGIS with the Flyway migrations, so production_history holds the real Kaggle rows. Only ML is mocked. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SupplyForecastIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    MlClient mlClient;

    @Test
    void migrationLoadsTheRealKaggleRows() {
        assertThat(jdbc.queryForObject("select count(*) from production_history", Integer.class)).isEqualTo(19_041);
        assertThat(jdbc.queryForObject("select count(*) from production_history where crop in ('Coconut', 'Cotton(lint)')",
                Integer.class)).isZero();
        Map<String, Object> row = jdbc.queryForMap("""
                select area_hectares, production_tonnes, source, data_classification, dataset_version
                from production_history
                where state = 'Uttar Pradesh' and crop = 'Wheat' and season = 'Rabi' and crop_year = 2018""");
        assertThat((BigDecimal) row.get("area_hectares")).isEqualByComparingTo("9855900");
        assertThat((BigDecimal) row.get("production_tonnes")).isEqualByComparingTo("38039724");
        assertThat(row).containsEntry("source", "KAGGLE_CROP_YIELD")
                .containsEntry("data_classification", "OBSERVED")
                .containsEntry("dataset_version", "crop_yield-sha256-ab9bc356b1f8");
    }

    @Test
    void forecastSendsStoredHistoryToTheMlService() throws Exception {
        when(mlClient.predictSupply(any())).thenReturn(new MlSupplyResponse("supply-production-xgb", "supply-xgb-v1",
                "MODEL_PREDICTION",
                new MlSupplyResponse.Prediction(new BigDecimal("37015561.210610636"), "tonnes",
                        "crop year 2019, Rabi season", null),
                new MlSupplyResponse.Evidence(new BigDecimal("36297631.55735549"), "area x mean yield", 3),
                new MlSupplyResponse.Provenance("crop_yield-sha256-ab9bc356b1f8", "supply-features-v1",
                        "2026-09-28T14:45:37+00:00", "<= 2013", "2017-2019", "Kaggle: ...", "state x crop x season x crop_year"),
                "2026-09-28T14:59:17.116786Z"));

        mvc.perform(get("/api/supply/forecast").header("Authorization", AuthTestSupport.newUserBearer(mvc))
                        .param("state", "uttar pradesh").param("crop", "wheat").param("season", "RABI")
                        .param("cropYear", "2019").param("areaHectares", "9852504"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("Uttar Pradesh"))
                .andExpect(jsonPath("$.forecast.predictionInterval").doesNotExist())
                .andExpect(jsonPath("$.history[2].cropYear").value(2016));

        ArgumentCaptor<MlSupplyRequest> sent = ArgumentCaptor.forClass(MlSupplyRequest.class);
        verify(mlClient).predictSupply(sent.capture());
        assertThat(sent.getValue().crop()).isEqualTo("Wheat");
        assertThat(sent.getValue().history()).extracting(MlSupplyRequest.HistoryPoint::productionTonnes)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("38039724"), new BigDecimal("35645666"), new BigDecimal("34971381"));
    }

    @Test
    void insufficientHistoryFromRealData() throws Exception {
        // The data ends in 2019 (2020 for Uttarakhand only), so 2026 has no previous-year observation.
        mvc.perform(get("/api/supply/forecast").header("Authorization", AuthTestSupport.newUserBearer(mvc))
                        .param("state", "Uttar Pradesh").param("crop", "Wheat").param("season", "RABI")
                        .param("cropYear", "2026").param("areaHectares", "9852504"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_HISTORY"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/supply/forecast").param("state", "Uttar Pradesh").param("crop", "Wheat")
                        .param("season", "RABI").param("cropYear", "2019").param("areaHectares", "1"))
                .andExpect(status().isUnauthorized());
    }
}
