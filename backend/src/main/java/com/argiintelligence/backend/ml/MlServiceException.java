package com.argiintelligence.backend.ml;

import com.argiintelligence.backend.common.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Set;

/**
 * Client-safe ML failure with the MASTER_SPEC §13 codes. The underlying HTTP/client exception is logged,
 * never returned. ML error codes are translated per §9.3.
 */
public class MlServiceException extends ApiException {

    /**
     * Inputs outside the served scope. AREA_OUT_OF_RANGE is an ML-side extension of §9.3 (a requested area far
     * outside the series' reported range is refused rather than extrapolated); it maps here as well.
     */
    static final Set<String> UNSUPPORTED_CODES = Set.of("UNSUPPORTED_DISTRICT", "UNSUPPORTED_CROP",
            "UNSUPPORTED_SEASON", "UNSUPPORTED_SERIES", "AREA_OUT_OF_RANGE");
    static final Set<String> INSUFFICIENT_CODES = Set.of("INSUFFICIENT_HISTORY", "AREA_UNAVAILABLE");
    static final Set<String> NO_MODEL_CODES = Set.of("MODEL_NOT_LOADED", "ARTIFACT_LOAD_ERROR");

    private MlServiceException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    /** Connection refused, DNS failure or timeout. */
    public static MlServiceException unavailable() {
        return new MlServiceException(HttpStatus.SERVICE_UNAVAILABLE, "ML_UNAVAILABLE",
                "The prediction service is unavailable");
    }

    /** ML is up but has no usable model. */
    public static MlServiceException predictionUnavailable() {
        return new MlServiceException(HttpStatus.SERVICE_UNAVAILABLE, "ML_PREDICTION_UNAVAILABLE",
                "The prediction service has no model loaded");
    }

    /** ML answered with data that is malformed, incomplete or breaks the contract. */
    public static MlServiceException invalidResponse() {
        return new MlServiceException(HttpStatus.BAD_GATEWAY, "ML_INVALID_RESPONSE",
                "The prediction service returned an invalid response");
    }

    /** The ML messages for the 422 codes are defined by the ML contract (they name the value), so they pass on. */
    static MlServiceException fromMlError(String code, String message) {
        if (code != null && UNSUPPORTED_CODES.contains(code)) {
            return new MlServiceException(HttpStatus.UNPROCESSABLE_CONTENT, "UNSUPPORTED_INPUT", message);
        }
        if (code != null && INSUFFICIENT_CODES.contains(code)) {
            return new MlServiceException(HttpStatus.UNPROCESSABLE_CONTENT, "INSUFFICIENT_DATA", message);
        }
        if (code != null && NO_MODEL_CODES.contains(code)) {
            return predictionUnavailable();
        }
        return invalidResponse();
    }
}
