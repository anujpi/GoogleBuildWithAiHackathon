package com.argiintelligence.backend.intelligence.service;

import com.argiintelligence.backend.intelligence.dto.IntelligenceReport.Decision;
import com.argiintelligence.backend.intelligence.dto.IntelligenceReport.Gap;
import com.argiintelligence.backend.intelligence.dto.IntelligenceReport.Risk;
import com.argiintelligence.backend.intelligence.dto.Section;
import com.argiintelligence.backend.weather.WeatherSnapshot;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Decision/risk rules on hand-written (SYNTHETIC) inputs; checks the rules, not any real-world value. */
class IntelligenceRulesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static Section<JsonNode> suitability(String json) {
        return Section.ok(JSON.readTree(json));
    }

    private static final Section<JsonNode> SUIT = suitability("""
            {"candidates":[
              {"crop":"Wheat","suitabilityScore":0.91,"components":[],"evidence":[]},
              {"crop":"Potato","suitabilityScore":0.83,"components":[
                 {"name":"stability","score":0.9,"weight":0.15,"evidence":"cv 0.10"}],"evidence":[]},
              {"crop":"Sugarcane","suitabilityScore":0.30,"components":[],"evidence":[]}]}""");

    private static Section<Gap> gap(String status, double pct) {
        return Section.ok(new Gap(2020, BigDecimal.TEN, "MODEL_PREDICTION", BigDecimal.ONE, "ESTIMATED",
                BigDecimal.ONE, BigDecimal.valueOf(pct), status, "m", List.of()));
    }

    private static final WeatherSnapshot NO_WEATHER = new WeatherSnapshot("UNAVAILABLE", "src", null, "t", 0.0, 0.0,
            null, List.of(), null, "down");

    @Test
    void largeSurplusMakesMarketRiskHighButKeepsSuitableCropAsCandidate() {
        Risk risk = IntelligenceService.risk(NO_WEATHER, SUIT, "Potato", gap("SURPLUS", 83), List.of());
        assertThat(risk.overall()).isEqualTo("HIGH");
        Decision d = IntelligenceService.decide("Potato", SUIT, gap("SURPLUS", 83), risk);
        assertThat(d.status()).isEqualTo("SUITABLE_WITH_MARKET_RISK");
        assertThat(d.alternatives()).containsExactly("Wheat (index 0.91)");
    }

    @Test
    void strongDemandNeverRescuesAnUnsuitableCrop() {
        Risk risk = IntelligenceService.risk(NO_WEATHER, SUIT, "Sugarcane", gap("DEFICIT", -60), List.of());
        Decision d = IntelligenceService.decide("Sugarcane", SUIT, gap("DEFICIT", -60), risk);
        assertThat(d.status()).isEqualTo("NOT_RECOMMENDED");
    }

    @Test
    void balancedMarketAndSuitableCropIsACandidate() {
        Risk risk = IntelligenceService.risk(NO_WEATHER, SUIT, "Potato", gap("BALANCED", 4), List.of());
        assertThat(IntelligenceService.decide("Potato", SUIT, gap("BALANCED", 4), risk).status())
                .isEqualTo("SUITABLE_CANDIDATE");
    }

    @Test
    void unavailableWeatherIsUnknownNotLow() {
        Risk risk = IntelligenceService.risk(NO_WEATHER, SUIT, "Potato", Section.unavailable("X", "y"), List.of());
        assertThat(risk.factors()).anySatisfy(f -> {
            assertThat(f.category()).isEqualTo("WEATHER");
            assertThat(f.level()).isEqualTo("UNKNOWN");
        });
    }

    @Test
    void heavyRainIsHighWeatherRisk() {
        WeatherSnapshot wet = new WeatherSnapshot("OK", "src", "FORECAST", "t", 0.0, 0.0,
                new WeatherSnapshot.Current("t", 25.0, 90.0, 1.0, 5.0), List.of(),
                new WeatherSnapshot.Summary(120.0, 30.0, 20.0), null);
        Risk risk = IntelligenceService.risk(wet, SUIT, "Potato", gap("BALANCED", 0), List.of());
        assertThat(risk.factors()).anySatisfy(f -> {
            assertThat(f.category()).isEqualTo("WEATHER_RAINFALL");
            assertThat(f.level()).isEqualTo("HIGH");
        });
        assertThat(risk.factors()).anySatisfy(f -> {
            assertThat(f.category()).isEqualTo("DISEASE_CONDITIONS");
            assertThat(f.level()).isEqualTo("MODERATE");
        });
    }

    @Test
    void cropWithoutEvidenceIsInsufficient() {
        Risk risk = IntelligenceService.risk(NO_WEATHER, SUIT, "Saffron", Section.unavailable("X", "y"), List.of());
        assertThat(IntelligenceService.decide("Saffron", SUIT, Section.unavailable("X", "y"), risk).status())
                .isEqualTo("INSUFFICIENT_EVIDENCE");
    }
}
