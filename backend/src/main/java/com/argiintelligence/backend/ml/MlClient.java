package com.argiintelligence.backend.ml;

import com.argiintelligence.backend.common.api.ApiError.FieldViolation;
import com.argiintelligence.backend.ml.dto.MlSupplyRequest;
import com.argiintelligence.backend.ml.dto.MlSupplyResponse;
import com.argiintelligence.backend.ml.exception.MlInvalidResponseException;
import com.argiintelligence.backend.ml.exception.MlRequestRejectedException;
import com.argiintelligence.backend.ml.exception.MlUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Typed client for the Python ML service. It returns a prediction only when the response satisfies the contract
 * (ml-service/docs/ml-contracts/supply.md); every other outcome becomes an {@code Ml*Exception}. It never
 * substitutes, defaults or estimates a value the ML service did not return.
 */
@Slf4j
public class MlClient {

    static final String SUPPLY_PATH = "/v1/predict/supply";
    public static final String SUITABILITY_PATH = "/v1/predict/crop-suitability";
    public static final String DEMAND_PATH = "/v1/predict/demand";
    public static final String ANOMALY_PATH = "/v1/predict/anomaly";
    public static final String DISEASE_PATH = "/v1/predict/disease";

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public MlClient(RestClient restClient, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
    }

    public MlSupplyResponse predictSupply(MlSupplyRequest request) {
        try {
            return restClient.post()
                    .uri(SUPPLY_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(jsonMapper.writeValueAsBytes(request))
                    .exchange((req, res) -> handleSupply(res.getStatusCode(), res.getBody().readAllBytes()));
        } catch (ResourceAccessException ex) {
            // Connection refused, DNS failure, connect or read timeout.
            log.warn("ML service unreachable at {}: {}", SUPPLY_PATH, ex.getMessage());
            throw MlUnavailableException.serviceUnreachable();
        }
    }

    /**
     * POSTs a JSON body to one of the transparent ML endpoints (suitability, demand, anomaly) and returns the body
     * as a tree. The caller checks the fields it relies on with {@link #requireFields}; nothing is defaulted here.
     */
    public JsonNode postJson(String path, Object request) {
        try {
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(jsonMapper.writeValueAsBytes(request))
                    .exchange((req, res) -> handleTree(path, res.getStatusCode(), res.getBody().readAllBytes()));
        } catch (ResourceAccessException ex) {
            log.warn("ML service unreachable at {}: {}", path, ex.getMessage());
            throw MlUnavailableException.serviceUnreachable();
        }
    }

    /** Sends one image to {@code POST /v1/predict/disease} as multipart field {@code image}. */
    public JsonNode predictDisease(byte[] image, String filename, String contentType) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        NamedBytes part = new NamedBytes(image, filename);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        parts.add("image", new HttpEntity<>(part, headers));
        try {
            return restClient.post()
                    .uri(DISEASE_PATH)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(parts)
                    .exchange((req, res) -> handleTree(DISEASE_PATH, res.getStatusCode(), res.getBody().readAllBytes()));
        } catch (ResourceAccessException ex) {
            log.warn("ML service unreachable at {}: {}", DISEASE_PATH, ex.getMessage());
            throw MlUnavailableException.serviceUnreachable();
        }
    }

    /** A named in-memory file part (multipart needs a filename). */
    private static final class NamedBytes extends ByteArrayResource {
        private final String filename;

        NamedBytes(byte[] bytes, String filename) {
            super(bytes);
            this.filename = filename == null || filename.isBlank() ? "image" : filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }

    /** Throws {@link MlInvalidResponseException} unless every dotted path is present and non-null. */
    public static JsonNode requireFields(JsonNode body, String... paths) {
        List<String> missing = new ArrayList<>();
        for (String p : paths) {
            JsonNode n = body;
            for (String part : p.split("\\.")) {
                n = n == null ? null : n.get(part);
            }
            if (n == null || n.isNull()) {
                missing.add(p);
            }
        }
        if (!missing.isEmpty()) {
            throw new MlInvalidResponseException("The ML response is missing required fields: " + missing);
        }
        return body;
    }

    private JsonNode handleTree(String path, HttpStatusCode status, byte[] body) {
        if (status.value() == 200) {
            try {
                JsonNode tree = jsonMapper.readTree(body);
                if (tree == null || !tree.isObject()) {
                    throw new MlInvalidResponseException("The ML service returned a non-object body on " + path);
                }
                return tree;
            } catch (JacksonException ex) {
                throw new MlInvalidResponseException("The ML service returned a body that is not JSON on " + path);
            }
        }
        if (status.value() == 422 || status.value() == 415 || status.value() == 413) {
            throw new MlRequestRejectedException(rejectionDetails(body));
        }
        if (status.value() == 503) {
            throw MlUnavailableException.modelNotLoaded(path);
        }
        log.warn("ML service answered {} on {}", status.value(), path);
        throw new MlInvalidResponseException("The ML service answered with unexpected status " + status.value());
    }

    private MlSupplyResponse handleSupply(HttpStatusCode status, byte[] body) {
        if (status.value() == 200) {
            return validated(read(body));
        }
        if (status.value() == 422) {
            throw new MlRequestRejectedException(rejectionDetails(body));
        }
        if (status.value() == 503) {
            throw MlUnavailableException.modelNotLoaded();
        }
        log.warn("ML service answered {} on {}", status.value(), SUPPLY_PATH);
        throw new MlInvalidResponseException("The ML service answered with unexpected status " + status.value());
    }

    private MlSupplyResponse read(byte[] body) {
        try {
            MlSupplyResponse response = jsonMapper.readValue(body, MlSupplyResponse.class);
            if (response == null) {
                throw new MlInvalidResponseException("The ML service returned an empty body");
            }
            return response;
        } catch (JacksonException ex) {
            throw new MlInvalidResponseException("The ML service returned a body that is not a supply prediction");
        }
    }

    /** Every contract field that is not documented as nullable must be present. */
    private static MlSupplyResponse validated(MlSupplyResponse r) {
        List<String> missing = new ArrayList<>();
        require(r.modelName(), "modelName", missing);
        require(r.modelVersion(), "modelVersion", missing);
        require(r.dataClassification(), "dataClassification", missing);
        require(r.generatedAt(), "generatedAt", missing);
        if (require(r.prediction(), "prediction", missing)) {
            require(r.prediction().value(), "prediction.value", missing);
            require(r.prediction().unit(), "prediction.unit", missing);
            require(r.prediction().period(), "prediction.period", missing);
            MlSupplyResponse.Interval i = r.prediction().interval();
            if (i != null) {
                require(i.lower(), "prediction.interval.lower", missing);
                require(i.upper(), "prediction.interval.upper", missing);
                require(i.nominalCoverage(), "prediction.interval.nominalCoverage", missing);
                require(i.method(), "prediction.interval.method", missing);
                require(i.testEmpiricalCoverage(), "prediction.interval.testEmpiricalCoverage", missing);
            }
        }
        if (require(r.evidence(), "evidence", missing)) {
            require(r.evidence().baselineValue(), "evidence.baselineValue", missing);
            require(r.evidence().baselineMethod(), "evidence.baselineMethod", missing);
            require(r.evidence().historyYearsUsed(), "evidence.historyYearsUsed", missing);
        }
        if (require(r.provenance(), "provenance", missing)) {
            require(r.provenance().datasetVersion(), "provenance.datasetVersion", missing);
            require(r.provenance().featureVersion(), "provenance.featureVersion", missing);
            require(r.provenance().trainedAt(), "provenance.trainedAt", missing);
            require(r.provenance().trainingPeriod(), "provenance.trainingPeriod", missing);
            require(r.provenance().evaluationPeriod(), "provenance.evaluationPeriod", missing);
        }
        if (!missing.isEmpty()) {
            throw new MlInvalidResponseException("The ML response is missing required fields: " + missing);
        }
        return r;
    }

    private static boolean require(Object value, String field, List<String> missing) {
        if (value == null) {
            missing.add(field);
            return false;
        }
        return true;
    }

    /**
     * FastAPI sends {@code detail} either as a string (the ML service's own input checks) or as a list of
     * {@code {loc, msg, type}} objects (request schema validation). Both become field violations.
     */
    private List<FieldViolation> rejectionDetails(byte[] body) {
        JsonNode detail;
        try {
            detail = jsonMapper.readTree(body).get("detail");
        } catch (JacksonException ex) {
            detail = null;
        }
        if (detail != null && detail.isString()) {
            return List.of(new FieldViolation(null, detail.asString()));
        }
        if (detail != null && detail.isArray()) {
            List<FieldViolation> violations = new ArrayList<>();
            for (JsonNode error : detail) {
                JsonNode msg = error.get("msg");
                violations.add(new FieldViolation(fieldPath(error.get("loc")),
                        msg != null && msg.isString() ? msg.asString() : error.toString()));
            }
            return violations;
        }
        return List.of(new FieldViolation(null, "The ML service rejected the request without a readable reason"));
    }

    /** {@code ["body", "history", 0, "cropYear"]} becomes {@code history[0].cropYear}. */
    private static String fieldPath(JsonNode loc) {
        if (loc == null || !loc.isArray()) {
            return null;
        }
        StringJoiner path = new StringJoiner(".");
        String pending = null;
        for (JsonNode part : loc) {
            if (part.isInt()) {
                pending = (pending == null ? "" : pending) + "[" + part.asInt() + "]";
                continue;
            }
            if (pending != null) {
                path.add(pending);
            }
            pending = part.asString();
        }
        if (pending != null) {
            path.add(pending);
        }
        String joined = path.toString();
        if (joined.equals("body")) {
            return null;
        }
        return joined.startsWith("body.") ? joined.substring("body.".length()) : joined;
    }
}
