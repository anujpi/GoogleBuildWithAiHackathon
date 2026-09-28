package com.argiintelligence.backend.ml;

import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Pure unit test: the ML HTTP endpoint is mocked, no ML service or Spring context needed. */
class MlClientTest {

    private static final String URL = "http://ml.test/v1/predict/supply";
    private static final Map<String, Object> REQUEST = Map.of("crop", "tomato", "district", "Ramanagara");

    private MockRestServiceServer server;
    private MlClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ml.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MlClient(builder.build());
    }

    @Test
    void parsesSuccessfulPrediction() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"crop\":\"tomato\",\"district\":\"Ramanagara\"}"))
                .andRespond(withSuccess("""
                        { "modelVersion": "supply-xgb-0.3",
                          "prediction": { "value": 1234.5, "unit": "tonnes", "period": "2026-KHARIF" },
                          "provenance": { "datasetVersion": "ds-2026-09", "featureVersion": "f-2",
                                          "trainedAt": "2026-09-20T10:00:00Z" },
                          "someFutureField": 1 }
                        """, MediaType.APPLICATION_JSON));

        SupplyPredictionResponse r = client.predictSupply(REQUEST);

        assertThat(r.modelVersion()).isEqualTo("supply-xgb-0.3");
        assertThat(r.prediction().value()).isEqualByComparingTo(new BigDecimal("1234.5"));
        assertThat(r.prediction().unit()).isEqualTo("tonnes");
        assertThat(r.prediction().period()).isEqualTo("2026-KHARIF");
        assertThat(r.provenance().datasetVersion()).isEqualTo("ds-2026-09");
        assertThat(r.provenance().featureVersion()).isEqualTo("f-2");
        assertThat(r.provenance().trainedAt()).isEqualTo("2026-09-20T10:00:00Z");
        server.verify();
    }

    @Test
    void missingProvenanceIsAllowed() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                { "modelVersion": "v1", "prediction": { "value": 10, "unit": "tonnes" } }
                """, MediaType.APPLICATION_JSON));

        SupplyPredictionResponse r = client.predictSupply(REQUEST);

        assertThat(r.provenance()).isNull();
        assertThat(r.prediction().period()).isNull();
    }

    @Test
    void connectionRefusedIsUnavailable() {
        server.expect(requestTo(URL)).andRespond(withException(new ConnectException("refused")));
        assertCode(503, "ML_SERVICE_UNAVAILABLE");
    }

    @Test
    void timeoutIsUnavailable() {
        server.expect(requestTo(URL)).andRespond(withException(new HttpTimeoutException("timed out")));
        assertCode(503, "ML_SERVICE_UNAVAILABLE");
    }

    @Test
    void serverErrorIsBadGateway() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("Traceback ..."));
        assertCode(502, "ML_SERVICE_ERROR");
    }

    @Test
    void clientErrorIsBadGateway() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .contentType(MediaType.APPLICATION_JSON).body("{\"detail\":\"bad features\"}"));
        assertCode(502, "ML_SERVICE_ERROR");
    }

    @Test
    void malformedJsonIsBadGateway() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{not json", MediaType.APPLICATION_JSON));
        assertCode(502, "ML_SERVICE_ERROR");
    }

    @Test
    void missingRequiredFieldsIsBadGateway() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"modelVersion\":\"v1\"}", MediaType.APPLICATION_JSON));
        assertCode(502, "ML_SERVICE_ERROR");
    }

    @Test
    void emptyBodyIsBadGateway() {
        server.expect(requestTo(URL)).andRespond(withSuccess());
        assertCode(502, "ML_SERVICE_ERROR");
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
