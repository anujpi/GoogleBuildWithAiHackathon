package com.argiintelligence.backend.ml;

import com.argiintelligence.backend.common.api.DataClassification;
import com.argiintelligence.backend.ml.dto.MlError;
import com.argiintelligence.backend.ml.dto.MlHealthResponse;
import com.argiintelligence.backend.ml.dto.MlSupplyRequest;
import com.argiintelligence.backend.ml.dto.ScopeResponse;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse;
import com.argiintelligence.backend.ml.dto.SupplyPredictionResponse.Interval;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Supplier;

/**
 * The only place that talks HTTP to the ML service (MASTER_SPEC §9.5). It owns serialization, required-field and
 * contract validation, and the translation of ML errors into §13 codes. It applies no business rules.
 */
@Slf4j
@RequiredArgsConstructor
public class MlClient {

    private final RestClient http;

    public MlHealthResponse health() {
        String path = "/health";
        MlHealthResponse r = call(path, () -> http.get().uri(path).retrieve().body(MlHealthResponse.class));
        if (r == null || r.status() == null || r.models() == null) {
            throw invalid(path, "missing status or models");
        }
        return r;
    }

    public ScopeResponse scope() {
        String path = "/v1/reference/scope";
        ScopeResponse r = call(path, () -> http.get().uri(path).retrieve().body(ScopeResponse.class));
        if (r == null || r.states() == null || r.districts() == null || r.crops() == null
                || r.seasons() == null || r.supplySeries() == null || r.datasets() == null) {
            throw invalid(path, "missing scope lists");
        }
        return r;
    }

    public SupplyPredictionResponse predictSupply(MlSupplyRequest request) {
        String path = "/v1/predict/supply";
        SupplyPredictionResponse r = call(path, () -> http.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .body(request).retrieve().body(SupplyPredictionResponse.class));
        String problem = supplyContractViolation(request, r);
        if (problem != null) {
            throw invalid(path, problem);
        }
        return r;
    }

    /** Required fields and the contract rules of MASTER_SPEC §8.1 step 4 / §12; null when the response is valid. */
    static String supplyContractViolation(MlSupplyRequest request, SupplyPredictionResponse r) {
        if (r == null || r.target() == null || r.estimate() == null || r.baseline() == null || r.history() == null
                || r.historicalYieldStats() == null || r.modelEvaluation() == null || r.limitations() == null) {
            return "missing top-level field";
        }
        var e = r.estimate();
        if (e.production() == null || e.yieldValue() == null || e.area() == null || e.servedMethod() == null
                || e.provenance() == null || e.provenance().dataClassification() == null
                || r.history().units() == null || r.history().points() == null || r.history().provenance() == null) {
            return "missing estimate or history field";
        }
        var t = r.target();
        if (!request.districtId().equals(t.districtId()) || !request.cropId().equals(t.cropId())
                || !request.season().equals(t.season()) || request.cropYear() != t.cropYear()) {
            return "target does not echo the request";
        }
        DataClassification expected = switch (e.servedMethod()) {
            case "MODEL" -> DataClassification.MODEL_PREDICTION;
            case "BASELINE" -> DataClassification.ESTIMATED;
            default -> null;
        };
        if (expected == null || e.provenance().dataClassification() != expected) {
            return "servedMethod " + e.servedMethod() + " does not match " + e.provenance().dataClassification();
        }
        if (r.history().provenance().dataClassification() != DataClassification.OBSERVED) {
            return "history is not OBSERVED";
        }
        if (!"TONNES".equals(e.production().unit()) || !"TONNES_PER_HECTARE".equals(e.yieldValue().unit())
                || !"HECTARES".equals(e.area().unit())) {
            return "unexpected unit";
        }
        if (e.production().value() < 0) {
            return "negative production";
        }
        if (!inside(e.production().value(), e.production().interval())
                || !inside(e.yieldValue().value(), e.yieldValue().interval())) {
            return "interval does not contain its value";
        }
        return null;
    }

    private static boolean inside(double value, Interval interval) {
        return interval == null || (interval.lower() <= value && value <= interval.upper());
    }

    private static MlServiceException invalid(String path, String problem) {
        log.error("ML response on {} breaks the contract: {}", path, problem);
        return MlServiceException.invalidResponse();
    }

    private <T> T call(String path, Supplier<T> request) {
        try {
            return request.get();
        } catch (ResourceAccessException ex) {
            log.warn("ML service unreachable on {}: {}", path, ex.getMessage());
            throw MlServiceException.unavailable();
        } catch (RestClientResponseException ex) {
            MlError.Body error = errorBody(ex);
            String code = error == null ? null : error.code();
            if ("VALIDATION_ERROR".equals(code)) {
                // The backend validates first, so this is a contract bug on one side.
                log.error("ML rejected the request on {} as invalid: {}", path, error.message());
            } else {
                log.warn("ML service returned {} {} on {}", ex.getStatusCode(), code, path);
            }
            throw MlServiceException.fromMlError(code, error == null ? null : error.message());
        } catch (RestClientException ex) {
            log.warn("ML service response on {} could not be read", path, ex);
            throw MlServiceException.invalidResponse();
        }
    }

    private static MlError.Body errorBody(RestClientResponseException ex) {
        try {
            MlError error = ex.getResponseBodyAs(MlError.class);
            return error == null ? null : error.error();
        } catch (RuntimeException unreadable) {
            return null;
        }
    }
}
