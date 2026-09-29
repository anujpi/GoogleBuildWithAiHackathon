package com.argiintelligence.backend.intelligence.service;

import com.argiintelligence.backend.common.api.DataClassification;
import com.argiintelligence.backend.common.api.Provenance;
import com.argiintelligence.backend.common.api.RiskLevel;
import com.argiintelligence.backend.farm.dto.FarmResponse;
import com.argiintelligence.backend.farm.entity.SoilDataClassification;
import com.argiintelligence.backend.farm.entity.SoilDataSource;
import com.argiintelligence.backend.intelligence.dto.RiskAssessmentResponse.Risk;
import com.argiintelligence.backend.intelligence.dto.SupplyEstimateResponse;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse.HistoricalYieldStats;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse.Quantity;
import com.argiintelligence.backend.reference.entity.CropRequirement;
import com.argiintelligence.backend.weather.dto.WeatherResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * risk-rules-v1 thresholds at their boundaries (MASTER_SPEC §11.1). All numbers below are test fixtures chosen to
 * sit on a rule boundary; they are not agronomic data.
 */
class RiskRulesTest {

    @ParameterizedTest
    @CsvSource({"0.0, LOW", "64.4, LOW", "64.5, HIGH", "115.5, HIGH", "115.6, CRITICAL", "204.5, CRITICAL"})
    void heavyRainfallFollowsTheImdCategories(double maxMm, RiskLevel expected) {
        assertThat(RiskRules.heavyRainfall(weather(day(20.0, 30.0, 1.0), day(20.0, 30.0, maxMm)), null).level())
                .isEqualTo(expected);
    }

    @Test
    void heavyRainfallIsUnavailableWithoutForecastOrPrecipitation() {
        assertThat(RiskRules.heavyRainfall(null, "UPSTREAM_UNAVAILABLE").unavailableReason())
                .contains("UPSTREAM_UNAVAILABLE");
        assertThat(RiskRules.heavyRainfall(weather(day(20.0, 30.0, null)), null).level())
                .isEqualTo(RiskLevel.UNAVAILABLE);
    }

    @Test
    void onlyTheNextSevenDaysCount() {
        List<WeatherResponse.Daily> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            days.add(day(20.0, 30.0, 1.0));
        }
        days.add(day(20.0, 30.0, 300.0)); // day 8 is outside the window
        assertThat(RiskRules.heavyRainfall(weather(days.toArray(WeatherResponse.Daily[]::new)), null).level())
                .isEqualTo(RiskLevel.LOW);
    }

    @Test
    void temperatureStressCountsDaysOutsideTheAbsoluteRange() {
        CropRequirement req = requirement(10, 15, 25, 35);
        assertThat(level(req, day(16.0, 24.0, 0.0))).isEqualTo(RiskLevel.LOW);
        assertThat(level(req, day(12.0, 24.0, 0.0))).isEqualTo(RiskLevel.MODERATE);
        assertThat(level(req, day(9.0, 24.0, 0.0), day(16.0, 24.0, 0.0))).isEqualTo(RiskLevel.HIGH);
        assertThat(level(req, day(9.0, 24.0, 0.0), day(16.0, 36.0, 0.0))).isEqualTo(RiskLevel.HIGH);
        assertThat(level(req, day(9.0, 24.0, 0.0), day(16.0, 36.0, 0.0), day(5.0, 40.0, 0.0)))
                .isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    void temperatureStressIsUnavailableWithoutEcoCropLimits() {
        CropRequirement missing = new CropRequirement(); // all NULL, as seeded while blocker B2 is open
        RiskRules.Outcome o = RiskRules.temperatureStress(weather(day(1.0, 50.0, 0.0)), null, missing, "Wheat");
        assertThat(o.level()).isEqualTo(RiskLevel.UNAVAILABLE);
        assertThat(o.unavailableReason()).contains("FAO EcoCrop");
    }

    @ParameterizedTest
    @CsvSource({"0.149, LOW", "0.15, MODERATE", "0.30, MODERATE", "0.301, HIGH"})
    void yieldVariabilityBoundaries(double cv, RiskLevel expected) {
        assertThat(RiskRules.yieldVariability(supply(cv, 0.1), null).level()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"0.199, LOW", "0.2, MODERATE", "0.4, MODERATE", "0.401, HIGH"})
    void downsideFrequencyBoundaries(double share, RiskLevel expected) {
        assertThat(RiskRules.downsideFrequency(supply(0.1, share), null).level()).isEqualTo(expected);
    }

    @Test
    void mlFactorsAreUnavailableWithoutAStatistic() {
        assertThat(RiskRules.yieldVariability(supply(null, null), null).level()).isEqualTo(RiskLevel.UNAVAILABLE);
        assertThat(RiskRules.downsideFrequency(null, "ML_PREDICTION_UNAVAILABLE").unavailableReason())
                .contains("ML_PREDICTION_UNAVAILABLE");
    }

    @ParameterizedTest
    @CsvSource({"6.0, OPTIMAL", "7.5, OPTIMAL", "5.9, TOLERABLE", "8.5, TOLERABLE", "4.9, OUTSIDE_ABSOLUTE_RANGE",
            "8.6, OUTSIDE_ABSOLUTE_RANGE"})
    void phFitBoundaries(String ph, RiskRules.PhFit expected) {
        CropRequirement req = requirement(0, 0, 0, 0);
        ReflectionTestUtils.setField(req, "phAbsMin", new BigDecimal("5.0"));
        ReflectionTestUtils.setField(req, "phOptMin", new BigDecimal("6.0"));
        ReflectionTestUtils.setField(req, "phOptMax", new BigDecimal("7.5"));
        ReflectionTestUtils.setField(req, "phAbsMax", new BigDecimal("8.5"));
        assertThat(RiskRules.phFit(soil(ph), req)).isEqualTo(expected);
    }

    @Test
    void soilPhIsUnavailableWithoutSoilOrLimitsAndCarriesTheSoilClassification() {
        assertThat(RiskRules.soilPh(null, requirement(0, 0, 0, 0), "Wheat").unavailableReason()).contains("no soil");
        assertThat(RiskRules.soilPh(soil("6.5"), new CropRequirement(), "Wheat").level())
                .isEqualTo(RiskLevel.UNAVAILABLE);
        CropRequirement req = requirement(0, 0, 0, 0);
        ReflectionTestUtils.setField(req, "phAbsMin", new BigDecimal("5.0"));
        ReflectionTestUtils.setField(req, "phOptMin", new BigDecimal("6.0"));
        ReflectionTestUtils.setField(req, "phOptMax", new BigDecimal("7.5"));
        ReflectionTestUtils.setField(req, "phAbsMax", new BigDecimal("8.5"));
        assertThat(RiskRules.soilPh(soil("6.5"), req, "Wheat").factor().dataClassification())
                .isEqualTo(DataClassification.OBSERVED);
    }

    @Test
    void levelIsTheWorstAssessedFactorAndUnavailableWhenNothingIsAssessed() {
        Risk mixed = RiskRules.combine(List.of(
                RiskRules.heavyRainfall(weather(day(20.0, 30.0, 70.0)), null),       // HIGH
                RiskRules.yieldVariability(supply(0.1, 0.1), null),                  // LOW
                RiskRules.soilPh(null, null, "Wheat")), List.of("note"));            // unavailable
        assertThat(mixed.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(mixed.assessedFactors()).isEqualTo(2);
        assertThat(mixed.unavailableFactors()).containsExactly("SOIL_PH");
        assertThat(mixed.limitations()).contains("note");

        Risk none = RiskRules.combine(List.of(RiskRules.soilPh(null, null, "Wheat")), List.of());
        assertThat(none.level()).isEqualTo(RiskLevel.UNAVAILABLE);
        assertThat(none.factors()).isEmpty();
    }

    @Test
    void marketRiskIsUnavailableUntilM4() {
        Risk market = RiskRules.marketUnavailable();
        assertThat(market.level()).isEqualTo(RiskLevel.UNAVAILABLE);
        assertThat(market.factors()).isEmpty();
        assertThat(market.limitations()).containsExactly("Market data is not connected (milestone M4)");
    }

    // ---- fixtures ----

    private static RiskLevel level(CropRequirement req, WeatherResponse.Daily... days) {
        return RiskRules.temperatureStress(weather(days), null, req, "Wheat").level();
    }

    static WeatherResponse.Daily day(Double min, Double max, Double rainMm) {
        return new WeatherResponse.Daily(LocalDate.parse("2026-09-29"), min, max, rainMm, null, null);
    }

    static WeatherResponse weather(WeatherResponse.Daily... days) {
        Provenance p = new Provenance("OPEN_METEO", DataClassification.FORECAST, null, null, null, null, null, null,
                null, List.of());
        return new WeatherResponse(null, 26.85, 80.95, null, List.of(days), p);
    }

    static CropRequirement requirement(double absMin, double optMin, double optMax, double absMax) {
        CropRequirement r = new CropRequirement();
        ReflectionTestUtils.setField(r, "tempAbsMinC", BigDecimal.valueOf(absMin));
        ReflectionTestUtils.setField(r, "tempOptMinC", BigDecimal.valueOf(optMin));
        ReflectionTestUtils.setField(r, "tempOptMaxC", BigDecimal.valueOf(optMax));
        ReflectionTestUtils.setField(r, "tempAbsMaxC", BigDecimal.valueOf(absMax));
        return r;
    }

    static FarmResponse.Soil soil(String ph) {
        return new FarmResponse.Soil(null, new BigDecimal(ph), null, null, null, null, null, null, null, null, null,
                null, null, SoilDataSource.LAB_REPORT, SoilDataClassification.OBSERVED, null, null);
    }

    static SupplyEstimateResponse supply(Double cv, Double downside) {
        Provenance history = new Provenance("DES_S01_DATA_GOV_IN", DataClassification.OBSERVED, null, null, "s01-x",
                null, null, null, "2014", List.of());
        return new SupplyEstimateResponse(null, null, null, null, null,
                new HistoricalYieldStats(17, new Quantity(3.1, "TONNES_PER_HECTARE"), cv, downside, 15, "fixture"),
                null, null, history, List.of());
    }
}
