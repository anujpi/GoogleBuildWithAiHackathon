package com.argiintelligence.backend.ml;

import com.argiintelligence.backend.common.api.DataClassification;
import com.argiintelligence.backend.ml.dto.MlHealthResponse;
import com.argiintelligence.backend.ml.dto.MlSupplyRequest;
import com.argiintelligence.backend.ml.dto.ScopeResponse;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * ML client contract (MASTER_SPEC §16), against a mock HTTP server. Bodies are the golden files in
 * src/test/resources/contracts/ml, which the ML test suite validates against its own schemas.
 *
 * <p>The golden responses come from a model trained on SYNTHETIC rows and say so (history SYNTHETIC, served
 * method BASELINE). {@link #real} turns them into the shapes a real artifact returns.
 */
class MlClientTest {

    private static final String SUPPLY_URL = "http://ml.test/v1/predict/supply";
    static final MlSupplyRequest REQUEST = new MlSupplyRequest("up-agra", "potato", "RABI", 2015, null);

    private MockRestServiceServer server;
    private MlClient client;

    static String golden(String name) {
        try (InputStream in = MlClientTest.class.getResourceAsStream("/contracts/ml/" + name)) {
            if (in == null) {
                throw new IllegalStateException("golden ML contract file missing: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The golden response as served by a real artifact: history OBSERVED, served by MODEL or BASELINE. */
    static String real(boolean model) {
        String json = golden("supply-response.json").replace("\"SYNTHETIC\"", "\"OBSERVED\"");
        return model ? json.replace("\"BASELINE\"", "\"MODEL\"").replace("\"ESTIMATED\"", "\"MODEL_PREDICTION\"")
                : json;
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ml.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MlClient(builder.build());
    }

    private void respond(String body) {
        server.expect(requestTo(SUPPLY_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void sendsTheCanonicalRequestAndMapsEveryField() {
        server.expect(requestTo(SUPPLY_URL)).andExpect(method(HttpMethod.POST))
                .andExpect(content().json(golden("supply-request.json"), true))
                .andRespond(withSuccess(real(true), MediaType.APPLICATION_JSON));

        SupplyPredictionResponse r = client.predictSupply(REQUEST);

        assertThat(r.target()).isEqualTo(new SupplyPredictionResponse.Target("up-agra", "potato", "RABI", 2015));
        var est = r.estimate();
        assertThat(est.production().unit()).isEqualTo("TONNES");
        assertThat(est.production().interval().nominalCoverage()).isEqualTo(0.8);
        assertThat(est.production().interval().method()).isEqualTo("EMPIRICAL_LOG_RESIDUAL_QUANTILES_VALIDATION");
        assertThat(est.yieldValue().unit()).isEqualTo("TONNES_PER_HECTARE");
        assertThat(est.area().areaSource()).isEqualTo("LAST_REPORTED");
        assertThat(est.servedMethod()).isEqualTo("MODEL");
        assertThat(est.historyYearsUsed()).containsExactly(2012, 2013, 2014);
        var p = est.provenance();
        assertThat(p.source()).isEqualTo("ML_SERVICE");
        assertThat(p.dataClassification()).isEqualTo(DataClassification.MODEL_PREDICTION);
        assertThat(p.featureVersion()).isEqualTo("supply-features-v2");
        assertThat(p.dataThrough()).isEqualTo("2014");
        assertThat(p.generatedAt()).isNotNull();
        assertThat(r.baseline().production().unit()).isEqualTo("TONNES");
        assertThat(r.reported()).isNull();
        assertThat(r.history().units().yieldUnit()).isEqualTo("TONNES_PER_HECTARE");
        assertThat(r.history().points()).extracting(SupplyPredictionResponse.HistoryPoint::cropYear)
                .containsExactly(2012, 2013, 2014);
        assertThat(r.historicalYieldStats().downsideDefinition()).isEqualTo("YIELD_BELOW_85PCT_OF_TRAILING_3Y_MEAN");
        assertThat(r.modelEvaluation().testPeriod()).isEqualTo("2013-2014");
        assertThat(r.limitations()).isNotEmpty();
        server.verify();
    }

    @Test
    void baselineServedAsEstimatedIsValid() {
        respond(real(false));
        var p = client.predictSupply(REQUEST).estimate().provenance();
        assertThat(p.dataClassification()).isEqualTo(DataClassification.ESTIMATED);
    }

    @Test
    void backtestResponseCarriesReportedValues() {
        respond(golden("supply-response-backtest.json").replace("\"SYNTHETIC\"", "\"OBSERVED\""));
        var r = client.predictSupply(new MlSupplyRequest("up-agra", "potato", "RABI", 2013, null));
        assertThat(r.reported().production().unit()).isEqualTo("TONNES");
        assertThat(r.estimate().area().areaSource()).isEqualTo("REPORTED");
    }

    @Test
    void parsesScopeAndHealth() {
        server.expect(requestTo("http://ml.test/v1/reference/scope")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(golden("reference-scope-response.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://ml.test/health"))
                .andRespond(withSuccess(golden("health-response.json"), MediaType.APPLICATION_JSON));

        ScopeResponse s = client.scope();
        MlHealthResponse h = client.health();

        assertThat(s.districts()).extracting(ScopeResponse.District::districtId).contains("up-agra");
        assertThat(s.supplySeries().getFirst().season()).isEqualTo("RABI");
        assertThat(s.marketSeries()).isEmpty();
        assertThat(s.datasets().getFirst().dataThrough()).isEqualTo("2014");
        assertThat(h.status()).isEqualTo("UP");
        assertThat(h.models().getFirst().status()).isEqualTo("READY");
    }

    // ---------- contract and business-rule violations → 502 ML_INVALID_RESPONSE ----------

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "\"MODEL_PREDICTION\"|\"ESTIMATED\"",          // MODEL served but labelled ESTIMATED
            "\"TONNES\"|\"tonnes\"",                        // wrong unit
            "\"cropYear\": 2015|\"cropYear\": 2014",        // target does not echo the request
    })
    void contractViolationsAreInvalidResponses(String from, String to) {
        // MODEL-served body, with one field broken.
        respond(real(true).replace(from, to));
        assertCode(502, "ML_INVALID_RESPONSE");
    }

    @Test
    void syntheticHistoryIsRejected() {
        respond(golden("supply-response.json"));
        assertCode(502, "ML_INVALID_RESPONSE");
    }

    @Test
    void negativeProductionIsRejected() {
        respond(real(true).replaceFirst("\"value\": [0-9.]+", "\"value\": -1.0"));
        assertCode(502, "ML_INVALID_RESPONSE");
    }

    @Test
    void intervalNotContainingItsValueIsRejected() {
        respond(real(true).replaceFirst("\"lower\": [0-9.]+", "\"lower\": 99999999.0"));
        assertCode(502, "ML_INVALID_RESPONSE");
    }

    // ---------- ML error codes (§9.3) ----------

    @ParameterizedTest
    @CsvSource({
            "422, UNSUPPORTED_DISTRICT, 422, UNSUPPORTED_INPUT",
            "422, UNSUPPORTED_CROP,     422, UNSUPPORTED_INPUT",
            "422, UNSUPPORTED_SEASON,   422, UNSUPPORTED_INPUT",
            "422, UNSUPPORTED_SERIES,   422, UNSUPPORTED_INPUT",
            "422, AREA_OUT_OF_RANGE,    422, UNSUPPORTED_INPUT",
            "422, INSUFFICIENT_HISTORY, 422, INSUFFICIENT_DATA",
            "422, AREA_UNAVAILABLE,     422, INSUFFICIENT_DATA",
            "503, MODEL_NOT_LOADED,     503, ML_PREDICTION_UNAVAILABLE",
            "503, ARTIFACT_LOAD_ERROR,  503, ML_PREDICTION_UNAVAILABLE",
            "422, VALIDATION_ERROR,     502, ML_INVALID_RESPONSE",
            "500, INTERNAL_ERROR,       502, ML_INVALID_RESPONSE",
    })
    void mlErrorCodesMapToSpecCodes(int mlStatus, String mlCode, int status, String code) {
        server.expect(requestTo(SUPPLY_URL)).andRespond(withStatus(HttpStatus.valueOf(mlStatus))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":\"" + mlCode + "\",\"message\":\"m\",\"details\":{}}}"));
        assertCode(status, code);
    }

    @ParameterizedTest
    @CsvSource({
            "error-unsupported-crop.json,     422, UNSUPPORTED_INPUT, crop 'tomato' is not supported",
            "error-insufficient-history.json, 422, INSUFFICIENT_DATA, no positive reported production for cropYear - 1 (2015)",
    })
    void goldenErrorBodiesKeepTheirMlMessage(String file, int status, String code, String message) {
        server.expect(requestTo(SUPPLY_URL)).andRespond(withStatus(HttpStatus.valueOf(status))
                .contentType(MediaType.APPLICATION_JSON).body(golden(file)));
        assertThatThrownBy(() -> client.predictSupply(REQUEST)).isInstanceOfSatisfying(MlServiceException.class,
                ex -> {
                    assertThat(ex.getCode()).isEqualTo(code);
                    assertThat(ex.getMessage()).isEqualTo(message);
                });
    }

    @Test
    void goldenValidationErrorIsAContractBug() {
        server.expect(requestTo(SUPPLY_URL)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .contentType(MediaType.APPLICATION_JSON).body(golden("error-validation.json")));
        assertCode(502, "ML_INVALID_RESPONSE");
    }

    @Test
    void errorWithoutTheEnvelopeIsInvalid() {
        server.expect(requestTo(SUPPLY_URL)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .contentType(MediaType.APPLICATION_JSON).body("{\"detail\":\"bad\"}"));
        assertCode(502, "ML_INVALID_RESPONSE");
    }

    // ---------- transport ----------

    @Test
    void connectionRefusedIsUnavailable() {
        server.expect(requestTo(SUPPLY_URL)).andRespond(withException(new ConnectException("refused")));
        assertCode(503, "ML_UNAVAILABLE");
    }

    @Test
    void timeoutIsUnavailable() {
        server.expect(requestTo(SUPPLY_URL)).andRespond(withException(new HttpTimeoutException("timed out")));
        assertCode(503, "ML_UNAVAILABLE");
    }

    @Test
    void nonJsonServerErrorIsInvalid() {
        server.expect(requestTo(SUPPLY_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Traceback ..."));
        assertCode(502, "ML_INVALID_RESPONSE");
    }

    @Test
    void malformedJsonIsInvalid() {
        respond("{not json");
        assertCode(502, "ML_INVALID_RESPONSE");
    }

    @Test
    void missingRequiredFieldsIsInvalid() {
        respond("{\"target\":null}");
        assertCode(502, "ML_INVALID_RESPONSE");
    }

    @Test
    void emptyBodyIsInvalid() {
        server.expect(requestTo(SUPPLY_URL)).andRespond(withSuccess());
        assertCode(502, "ML_INVALID_RESPONSE");
    }

    private void assertCode(int status, String code) {
        assertThatThrownBy(() -> client.predictSupply(REQUEST))
                .isInstanceOfSatisfying(MlServiceException.class, ex -> {
                    assertThat(ex.getStatus().value()).isEqualTo(status);
                    assertThat(ex.getCode()).isEqualTo(code);
                    assertThat(ex.getMessage()).doesNotContain("Traceback", "refused", "ml.test");
                });
    }
}
