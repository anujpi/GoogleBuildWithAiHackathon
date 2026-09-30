package com.argiintelligence.backend.advisory;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdvisoryGroundingTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final JsonNode EVIDENCE = JSON.readTree("""
            {"supply":{"expectedProductionTonnes":15311294},"gap":{"gapPctOfDemand":83.0},
             "weather":{"next7Days":{"maxTemperatureC":35.8}},"decision":{"reasons":["suitability index 0.83"]}}""");

    @Test
    void numbersFromEvidenceAndRescaledUnitsAreAccepted() {
        AdvisoryText t = new AdvisoryText("Supply about 153 lakh t, 83% above demand; max 35.8 °C; index 0.83.",
                List.of("15,311,294 tonnes"), List.of("Check 3 things"), List.of());
        assertThat(AdvisoryService.unverifiedNumbers(t, EVIDENCE)).isEmpty();
    }

    @Test
    void inventedNumbersAreFlagged() {
        AdvisoryText t = new AdvisoryText("Mandi price is 1450 per quintal and yield 42 t/ha.", List.of(), List.of(),
                List.of());
        assertThat(AdvisoryService.unverifiedNumbers(t, EVIDENCE)).containsExactly("1450", "42");
    }

    @Test
    void templateFallbackOnlyUsesEvidenceValues() {
        TemplateAdvisoryGenerator template = new TemplateAdvisoryGenerator(JSON);
        String evidence = """
                {"farm":{"name":"F","state":"S","district":"D"},"focusCrop":"Potato",
                 "decision":{"status":"REVIEW","headline":"Potato is possible","reasons":["r1"]},
                 "suitability":{"focusCandidate":{"suitabilityScore":0.7}},
                 "gap":{"status":"UNAVAILABLE"},"weather":{"status":"UNAVAILABLE"},
                 "risk":{"factors":[]},"dataNotices":["n1"]}""";
        for (String lang : List.of("en", "hi")) {
            AdvisoryText t = template.generate(evidence, lang);
            assertThat(t.explanation()).contains("Potato").contains("0.7");
            assertThat(AdvisoryService.unverifiedNumbers(t, JSON.readTree(evidence))).isEmpty();
        }
    }
}
