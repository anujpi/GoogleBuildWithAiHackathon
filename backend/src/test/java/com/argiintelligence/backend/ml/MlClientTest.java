package com.argiintelligence.backend.ml;

import com.argiintelligence.backend.common.api.ApiError.FieldViolation;
import com.argiintelligence.backend.common.exception.ApiException;
import com.argiintelligence.backend.ml.dto.MlSupplyRequest;
import com.argiintelligence.backend.ml.dto.MlSupplyResponse;
import com.argiintelligence.backend.ml.exception.MlInvalidResponseException;
import com.argiintelligence.backend.ml.exception.MlRequestRejectedException;
import com.argiintelligence.backend.ml.exception.MlUnavailableException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MlClientTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Real request/response pair recorded from the ML service (ml-service/docs/ml-contracts/supply.md). */
    static final MlSupplyRequest REQUEST = new MlSupplyRequest("Wheat", "Rabi", 2019, new BigDecimal("9852504"),
            List.of(new MlSupplyRequest.HistoryPoint(2018, new BigDecimal("9855900"), new BigDecimal("38039724")),
                    new MlSupplyRequest.HistoryPoint(2017, new BigDecimal("9752941"), new BigDecimal("35645666")),
                    new MlSupplyRequest.HistoryPoint(2016, new BigDecimal("9884913"), new BigDecimal("34971381"))));

    static final String REQUEST_JSON = """
            {"crop":"Wheat","season":"Rabi","cropYear":2019,"areaHectares":9852504,"history":[
             {"cropYear":2018,"areaHectares":9855900,"productionTonnes":38039724},
             {"cropYear":2017,"areaHectares":9752941,"productionTonnes":35645666},
             {"cropYear":2016,"areaHectares":9884913,"productionTonnes":34971381}]}""";

    static final String SUCCESS_JSON = """
            {"modelName":"supply-production-xgb","modelVersion":"supply-xgb-v1","dataClassification":"MODEL_PREDICTION",
             "prediction":{"value":37015561.210610636,"unit":"tonnes","period":"crop year 2019, Rabi season",
              "interval":{"lower":27285797.492844444,"upper":45232845.87415427,"nominalCoverage":0.8,
               "method":"empirical quantiles of log(actual/predicted) on the validation period",
               "testEmpiricalCoverage":0.8192900681247759}},
             "evidence":{"baselineValue":36297631.55735549,
              "baselineMethod":"area x mean reported yield of up to 3 previous crop years","historyYearsUsed":3},
             "provenance":{"datasetVersion":"crop_yield-sha256-ab9bc356b1f8","featureVersion":"supply-features-v1",
              "trainedAt":"2026-09-28T14:45:37+00:00","trainingPeriod":"<= 2013","evaluationPeriod":"2017-2019",
              "trainingDataSource":"Kaggle: Agricultural Crop Yield in Indian States Dataset (akshatgupta7)",
              "spatialGranularity":"state x crop x season x crop_year"},
             "generatedAt":"2026-09-28T14:59:17.116786Z"}""";

    private MockRestServiceServer server;
    private MlClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ml.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MlClient(builder.build(), JSON);
    }

    private void respond(org.springframework.test.web.client.ResponseCreator response) {
        server.expect(requestTo("http://ml.test/v1/predict/supply"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(response);
    }

    @Test
    void successSendsTheContractRequestAndReturnsEveryField() {
        server.expect(requestTo("http://ml.test/v1/predict/supply"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(REQUEST_JSON, org.springframework.test.json.JsonCompareMode.STRICT))
                .andRespond(withSuccess(SUCCESS_JSON, MediaType.APPLICATION_JSON));

        MlSupplyResponse r = client.predictSupply(REQUEST);

        server.verify();
        assertThat(r.modelVersion()).isEqualTo("supply-xgb-v1");
        assertThat(r.dataClassification()).isEqualTo("MODEL_PREDICTION");
        assertThat(r.prediction().value()).isEqualByComparingTo("37015561.210610636");
        assertThat(r.prediction().unit()).isEqualTo("tonnes");
        assertThat(r.prediction().interval().lower()).isEqualByComparingTo("27285797.492844444");
        assertThat(r.prediction().interval().nominalCoverage()).isEqualByComparingTo("0.8");
        assertThat(r.evidence().baselineValue()).isEqualByComparingTo("36297631.55735549");
        assertThat(r.evidence().historyYearsUsed()).isEqualTo(3);
        assertThat(r.provenance().trainingDataSource()).startsWith("Kaggle");
        assertThat(r.provenance().spatialGranularity()).isEqualTo("state x crop x season x crop_year");
    }

    @Test
    void nullIntervalStaysNull() {
        respond(withSuccess(SUCCESS_JSON.replaceFirst("\"interval\":\\{[^}]*}", "\"interval\":null"),
                MediaType.APPLICATION_JSON));
        assertThat(client.predictSupply(REQUEST).prediction().interval()).isNull();
    }

    @Test
    void rejectionWithStringDetail() {
        respond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).contentType(MediaType.APPLICATION_JSON)
                .body("{\"detail\":\"unsupported crop 'Tomato'; supported: ['Wheat']\"}"));
        assertThatThrownBy(() -> client.predictSupply(REQUEST))
                .isInstanceOfSatisfying(MlRequestRejectedException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(ex.getCode()).isEqualTo("ML_REQUEST_REJECTED");
                    assertThat(ex.getDetails()).containsExactly(
                            new FieldViolation(null, "unsupported crop 'Tomato'; supported: ['Wheat']"));
                });
    }

    @Test
    void rejectionWithFastApiValidationList() {
        respond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).contentType(MediaType.APPLICATION_JSON).body("""
                {"detail":[
                 {"type":"greater_than","loc":["body","areaHectares"],"msg":"Input should be greater than 0",
                  "input":-5,"ctx":{"gt":0.0}},
                 {"type":"missing","loc":["body","history",0,"cropYear"],"msg":"Field required","input":{}},
                 {"type":"extra_forbidden","loc":["body","unexpected"],"msg":"Extra inputs are not permitted"}]}"""));
        assertThatThrownBy(() -> client.predictSupply(REQUEST))
                .isInstanceOfSatisfying(MlRequestRejectedException.class, ex -> assertThat(ex.getDetails()).containsExactly(
                        new FieldViolation("areaHectares", "Input should be greater than 0"),
                        new FieldViolation("history[0].cropYear", "Field required"),
                        new FieldViolation("unexpected", "Extra inputs are not permitted")));
    }

    @Test
    void rejectionWithUnreadableBodyStillReportsTheRejection() {
        respond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT).body("not json"));
        assertThatThrownBy(() -> client.predictSupply(REQUEST))
                .isInstanceOfSatisfying(MlRequestRejectedException.class,
                        ex -> assertThat(ex.getDetails()).hasSize(1));
    }

    @Test
    void modelNotLoaded() {
        respond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
                .body("{\"detail\":\"supply model is not loaded\"}"));
        assertUnavailable("ML_MODEL_UNAVAILABLE");
    }

    @Test
    void ioFailureIsServiceUnavailable() {
        respond(withException(new SocketTimeoutException("Read timed out")));
        assertUnavailable("ML_SERVICE_UNAVAILABLE");
    }

    @Test
    void realReadTimeoutIsServiceUnavailable() throws Exception {
        HttpServer slow = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slow.createContext("/", exchange -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        slow.start();
        try {
            MlClient timingOut = new MlClientConfig().mlClient(new MlProperties(
                    "http://127.0.0.1:" + slow.getAddress().getPort(), Duration.ofSeconds(1), Duration.ofMillis(200)),
                    JSON);
            assertThatThrownBy(() -> timingOut.predictSupply(REQUEST))
                    .isInstanceOfSatisfying(MlUnavailableException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo("ML_SERVICE_UNAVAILABLE"));
        } finally {
            slow.stop(0);
        }
    }

    @Test
    void configuredClientSendsThePlainHttp11BodyOverTheWire() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> upgrade = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> body = new java.util.concurrent.atomic.AtomicReference<>();
        HttpServer ml = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ml.createContext("/v1/predict/supply", exchange -> {
            upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            byte[] out = SUCCESS_JSON.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        ml.start();
        try {
            MlClient real = new MlClientConfig().mlClient(new MlProperties(
                    "http://127.0.0.1:" + ml.getAddress().getPort(), Duration.ofSeconds(1), Duration.ofSeconds(2)), JSON);
            assertThat(real.predictSupply(REQUEST).modelVersion()).isEqualTo("supply-xgb-v1");
            // An h2c upgrade makes uvicorn drop the body (regression: every call returned 422 "Field required").
            assertThat(upgrade.get()).isNull();
            assertThat(JSON.readTree(body.get())).isEqualTo(JSON.readTree(REQUEST_JSON));
        } finally {
            ml.stop(0);
        }
    }

    @Test
    void connectionRefusedIsServiceUnavailable() throws Exception {
        int freePort;
        try (var socket = new java.net.ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        MlClient refused = new MlClientConfig().mlClient(new MlProperties(
                "http://127.0.0.1:" + freePort, Duration.ofSeconds(1), Duration.ofSeconds(1)), JSON);
        assertThatThrownBy(() -> refused.predictSupply(REQUEST))
                .isInstanceOfSatisfying(MlUnavailableException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("ML_SERVICE_UNAVAILABLE"));
    }

    @Test
    void malformedBodyIsInvalidResponse() {
        respond(withSuccess("<html>gateway</html>", MediaType.TEXT_HTML));
        assertInvalid("not a supply prediction");
    }

    @Test
    void missingRequiredFieldsAreInvalidResponse() {
        respond(withSuccess("""
                {"modelName":"supply-production-xgb","modelVersion":"supply-xgb-v1",
                 "dataClassification":"MODEL_PREDICTION","prediction":{"unit":"tonnes","period":"p","interval":null},
                 "generatedAt":"2026-09-28T14:59:17Z"}""", MediaType.APPLICATION_JSON));
        assertInvalid("prediction.value");
    }

    @Test
    void unexpectedStatusIsInvalidResponse() {
        respond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error"));
        assertInvalid("500");
    }

    private void assertUnavailable(String code) {
        assertThatThrownBy(() -> client.predictSupply(REQUEST))
                .isInstanceOfSatisfying(MlUnavailableException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getCode()).isEqualTo(code);
                });
    }

    private void assertInvalid(String messagePart) {
        assertThatThrownBy(() -> client.predictSupply(REQUEST))
                .isInstanceOf(MlInvalidResponseException.class)
                .hasMessageContaining(messagePart)
                .extracting(ex -> ((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }
}
