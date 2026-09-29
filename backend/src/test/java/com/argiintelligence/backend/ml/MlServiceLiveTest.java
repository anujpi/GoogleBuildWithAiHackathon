package com.argiintelligence.backend.ml;

import com.argiintelligence.backend.ml.dto.MlSupplyRequest;
import com.argiintelligence.backend.ml.dto.ScopeResponse;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real HTTP against a running ML service with a loaded artifact, through the production client configuration.
 * Skipped unless ML_E2E_BASE_URL is set:
 * <pre>ML_E2E_BASE_URL=http://localhost:8000 ./mvnw -Dtest=MlServiceLiveTest test</pre>
 * Everything is discovered from the service's own scope; nothing about the data is hard-coded. A service serving
 * a SYNTHETIC artifact fails the first test by design (history must be OBSERVED).
 */
@EnabledIfEnvironmentVariable(named = "ML_E2E_BASE_URL", matches = "https?://.+")
class MlServiceLiveTest {

    private MlClient client;

    @BeforeEach
    void setUp() {
        client = MlClientConfig.create(new MlServiceProperties(System.getenv("ML_E2E_BASE_URL"),
                Duration.ofSeconds(2), Duration.ofSeconds(10)));
    }

    @Test
    void healthReportsAReadySupplyModel() {
        var model = client.health().models().getFirst();
        assertThat(model.capability()).isEqualTo("SUPPLY");
        assertThat(model.status()).isEqualTo("READY");
        assertThat(model.modelVersion()).isNotBlank();
    }

    @Test
    void supportedSeriesReturnsAFullyAttributedPrediction() {
        ScopeResponse scope = client.scope();
        ScopeResponse.SupplySeries s = scope.supplySeries().stream()
                .max(Comparator.comparingInt(ScopeResponse.SupplySeries::lastYear)).orElseThrow();

        // predictSupply also enforces the contract rules (classification vs served method, units, bounds).
        SupplyPredictionResponse r = client.predictSupply(new MlSupplyRequest(
                s.districtId(), s.cropId(), s.season(), s.lastYear() + 1, null));

        assertThat(r.estimate().production().interval()).isNotNull();
        var p = r.estimate().provenance();
        assertThat(p.modelVersion()).isNotBlank();
        assertThat(p.datasetVersion()).isEqualTo(scope.datasets().getFirst().datasetVersion());
        assertThat(p.featureVersion()).isNotBlank();
        assertThat(p.dataThrough()).isEqualTo(String.valueOf(s.lastYear()));
        assertThat(p.generatedAt()).isNotNull();
        assertThat(r.history().points()).hasSize(s.yearsObserved());
        assertThat(r.modelEvaluation().testPeriod()).isNotBlank();
    }

    @Test
    void unsupportedCropIsUnsupportedInput() {
        ScopeResponse.SupplySeries s = client.scope().supplySeries().getFirst();
        assertThatThrownBy(() -> client.predictSupply(new MlSupplyRequest(
                s.districtId(), "tomato", s.season(), s.lastYear() + 1, null)))
                .isInstanceOfSatisfying(MlServiceException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("UNSUPPORTED_INPUT"));
    }
}
