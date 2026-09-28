package com.argiintelligence.backend.supply.service;

import com.argiintelligence.backend.common.api.ApiError.FieldViolation;
import com.argiintelligence.backend.ml.MlClient;
import com.argiintelligence.backend.ml.dto.MlSupplyRequest;
import com.argiintelligence.backend.ml.dto.MlSupplyResponse;
import com.argiintelligence.backend.ml.exception.MlInvalidResponseException;
import com.argiintelligence.backend.ml.exception.MlRequestRejectedException;
import com.argiintelligence.backend.ml.exception.MlUnavailableException;
import com.argiintelligence.backend.supply.dto.SupplyForecastRequest;
import com.argiintelligence.backend.supply.dto.SupplyForecastResponse;
import com.argiintelligence.backend.supply.entity.ProductionDataClassification;
import com.argiintelligence.backend.supply.entity.ProductionDataSource;
import com.argiintelligence.backend.supply.entity.ProductionHistory;
import com.argiintelligence.backend.supply.exception.InsufficientHistoryException;
import com.argiintelligence.backend.supply.exception.UnsupportedSeasonException;
import com.argiintelligence.backend.supply.repository.ProductionHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SupplyForecastServiceTest {

    private static final String DATASET = "crop_yield-sha256-ab9bc356b1f8";
    private static final BigDecimal STATE_AREA_2019 = new BigDecimal("9852504");

    private final ProductionHistoryRepository repository = mock(ProductionHistoryRepository.class);
    private final MlClient mlClient = mock(MlClient.class);
    private final SupplyForecastService service = new SupplyForecastService(repository, mlClient);

    /** Real Uttar Pradesh / Wheat / Rabi rows from crop_yield.csv, newest first as the repository returns them. */
    private static final List<ProductionHistory> UP_WHEAT_RABI = List.of(
            row(2018, "9855900", "38039724"), row(2017, "9752941", "35645666"), row(2016, "9884913", "34971381"));

    private MlSupplyResponse mlResponse;

    @BeforeEach
    void setUp() {
        mlResponse = response("tonnes", new MlSupplyResponse.Interval(new BigDecimal("27285797.492844444"),
                new BigDecimal("45232845.87415427"), new BigDecimal("0.8"),
                "empirical quantiles of log(actual/predicted) on the validation period",
                new BigDecimal("0.8192900681247759")));
    }

    @Test
    void buildsTheMlRequestFromStoredHistoryAndReturnsTheMlResultUnchanged() {
        when(repository.findSeries("uttar pradesh", "wheat", "Rabi", 2016, 2018)).thenReturn(UP_WHEAT_RABI);
        when(mlClient.predictSupply(any())).thenReturn(mlResponse);

        SupplyForecastResponse r = service.forecast(request(" uttar pradesh ", "wheat", "RABI", 2019));

        ArgumentCaptor<MlSupplyRequest> sent = ArgumentCaptor.forClass(MlSupplyRequest.class);
        verify(mlClient).predictSupply(sent.capture());
        // The dataset's spelling is sent, not the caller's; the area is the caller's explicit input.
        assertThat(sent.getValue()).isEqualTo(new MlSupplyRequest("Wheat", "Rabi", 2019, STATE_AREA_2019, List.of(
                new MlSupplyRequest.HistoryPoint(2018, new BigDecimal("9855900"), new BigDecimal("38039724")),
                new MlSupplyRequest.HistoryPoint(2017, new BigDecimal("9752941"), new BigDecimal("35645666")),
                new MlSupplyRequest.HistoryPoint(2016, new BigDecimal("9884913"), new BigDecimal("34971381")))));

        assertThat(r.state()).isEqualTo("Uttar Pradesh");
        assertThat(r.crop()).isEqualTo("Wheat");
        assertThat(r.season()).isEqualTo("RABI");
        assertThat(r.sourceSeasonLabel()).isEqualTo("Rabi");
        assertThat(r.targetAreaHectares()).isEqualByComparingTo(STATE_AREA_2019);
        assertThat(r.targetAreaSource()).isEqualTo("REQUEST_INPUT");
        assertThat(r.forecast().expectedProductionTonnes()).isEqualByComparingTo("37015561.210610636");
        assertThat(r.forecast().dataClassification()).isEqualTo("MODEL_PREDICTION");
        assertThat(r.forecast().predictionInterval().lowerTonnes()).isEqualByComparingTo("27285797.492844444");
        assertThat(r.forecast().predictionInterval().nominalCoverage()).isEqualByComparingTo("0.8");
        assertThat(r.baseline().productionTonnes()).isEqualByComparingTo("36297631.55735549");
        assertThat(r.baseline().productionTonnes()).isNotEqualByComparingTo(r.forecast().expectedProductionTonnes());
        assertThat(r.history()).extracting(SupplyForecastResponse.HistoryPoint::cropYear).containsExactly(2018, 2017, 2016);
        assertThat(r.history()).allSatisfy(h -> {
            assertThat(h.dataClassification()).isEqualTo("OBSERVED");
            assertThat(h.source()).isEqualTo("KAGGLE_CROP_YIELD");
            assertThat(h.datasetVersion()).isEqualTo(DATASET);
        });
        assertThat(r.model().trainingDataSource()).startsWith("Kaggle");
        assertThat(r.model().spatialGranularity()).isEqualTo("state x crop x season x crop_year");
        assertThat(r.model().datasetVersion()).isEqualTo(DATASET);
        assertThat(r.limitations()).anyMatch(l -> l.contains("not a confidence score"));
    }

    @Test
    void seasonIsCaseInsensitive() {
        when(repository.findSeries("Uttar Pradesh", "Wheat", "Rabi", 2016, 2018)).thenReturn(UP_WHEAT_RABI);
        when(mlClient.predictSupply(any())).thenReturn(mlResponse);
        assertThat(service.forecast(request("Uttar Pradesh", "Wheat", "rabi", 2019)).season()).isEqualTo("RABI");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZAID", "OTHER", "SUMMER", "Whole Year"})
    void unsupportedSeasonIsRejectedBeforeAnyLookup(String season) {
        assertThatThrownBy(() -> service.forecast(request("Uttar Pradesh", "Wheat", season, 2019)))
                .isInstanceOf(UnsupportedSeasonException.class)
                .hasMessageContaining("supported: KHARIF, RABI");
        verifyNoInteractions(repository, mlClient);
    }

    @Test
    void missingPreviousYearIsInsufficientHistory() {
        when(repository.findSeries("Uttar Pradesh", "Wheat", "Rabi", 2027, 2029)).thenReturn(List.of());
        when(repository.findSeriesYears("Uttar Pradesh", "Wheat", "Rabi")).thenReturn(List.of(1997, 2018, 2019));

        assertThatThrownBy(() -> service.forecast(request("Uttar Pradesh", "Wheat", "RABI", 2030)))
                .isInstanceOfSatisfying(InsufficientHistoryException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("INSUFFICIENT_HISTORY");
                    assertThat(ex.getMessage()).contains("crop year 2029").contains("1997-2019");
                });
        verifyNoInteractions(mlClient);
    }

    @Test
    void olderYearsWithoutThePreviousYearAreStillInsufficient() {
        when(repository.findSeries("Uttar Pradesh", "Wheat", "Rabi", 2017, 2019))
                .thenReturn(List.of(row(2018, "9855900", "38039724"), row(2017, "9752941", "35645666")));
        when(repository.findSeriesYears("Uttar Pradesh", "Wheat", "Rabi")).thenReturn(List.of(2017, 2018));

        assertThatThrownBy(() -> service.forecast(request("Uttar Pradesh", "Wheat", "RABI", 2020)))
                .isInstanceOf(InsufficientHistoryException.class);
        verifyNoInteractions(mlClient);
    }

    @Test
    void unknownSeriesIsInsufficientHistory() {
        when(repository.findSeries(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        when(repository.findSeriesYears("Uttar Pradesh", "Tomato", "Kharif")).thenReturn(List.of());

        assertThatThrownBy(() -> service.forecast(request("Uttar Pradesh", "Tomato", "KHARIF", 2019)))
                .isInstanceOf(InsufficientHistoryException.class)
                .hasMessageContaining("No production history exists");
    }

    @Test
    void mlFailuresPropagateUnchanged() {
        when(repository.findSeries("Uttar Pradesh", "Wheat", "Rabi", 2016, 2018)).thenReturn(UP_WHEAT_RABI);
        MlRequestRejectedException rejected = new MlRequestRejectedException(
                List.of(new FieldViolation(null, "history has no positive yield in the last three crop years")));
        when(mlClient.predictSupply(any())).thenThrow(rejected).thenThrow(MlUnavailableException.serviceUnreachable());
        assertThatThrownBy(() -> service.forecast(request("Uttar Pradesh", "Wheat", "RABI", 2019))).isSameAs(rejected);
        assertThatThrownBy(() -> service.forecast(request("Uttar Pradesh", "Wheat", "RABI", 2019)))
                .isInstanceOf(MlUnavailableException.class);
    }

    @Test
    void otherUnitIsNeverRelabelledAsTonnes() {
        when(repository.findSeries("Uttar Pradesh", "Wheat", "Rabi", 2016, 2018)).thenReturn(UP_WHEAT_RABI);
        when(mlClient.predictSupply(any())).thenReturn(response("kilograms", null));
        assertThatThrownBy(() -> service.forecast(request("Uttar Pradesh", "Wheat", "RABI", 2019)))
                .isInstanceOf(MlInvalidResponseException.class)
                .hasMessageContaining("kilograms");
    }

    @Test
    void missingIntervalStaysNull() {
        when(repository.findSeries("Uttar Pradesh", "Wheat", "Rabi", 2016, 2018)).thenReturn(UP_WHEAT_RABI);
        when(mlClient.predictSupply(any())).thenReturn(response("tonnes", null));
        assertThat(service.forecast(request("Uttar Pradesh", "Wheat", "RABI", 2019)).forecast().predictionInterval())
                .isNull();
    }

    private static SupplyForecastRequest request(String state, String crop, String season, int cropYear) {
        return new SupplyForecastRequest(state, crop, season, cropYear, STATE_AREA_2019);
    }

    private static ProductionHistory row(int year, String area, String production) {
        return new ProductionHistory("Uttar Pradesh", "Wheat", "Rabi", year, new BigDecimal(area),
                new BigDecimal(production), ProductionDataSource.KAGGLE_CROP_YIELD,
                ProductionDataClassification.OBSERVED, DATASET);
    }

    private static MlSupplyResponse response(String unit, MlSupplyResponse.Interval interval) {
        return new MlSupplyResponse("supply-production-xgb", "supply-xgb-v1", "MODEL_PREDICTION",
                new MlSupplyResponse.Prediction(new BigDecimal("37015561.210610636"), unit,
                        "crop year 2019, Rabi season", interval),
                new MlSupplyResponse.Evidence(new BigDecimal("36297631.55735549"),
                        "area x mean reported yield of up to 3 previous crop years", 3),
                new MlSupplyResponse.Provenance(DATASET, "supply-features-v1", "2026-09-28T14:45:37+00:00", "<= 2013",
                        "2017-2019", "Kaggle: Agricultural Crop Yield in Indian States Dataset (akshatgupta7)",
                        "state x crop x season x crop_year"),
                "2026-09-28T14:59:17.116786Z");
    }
}
