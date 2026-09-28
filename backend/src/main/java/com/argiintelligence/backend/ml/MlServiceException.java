package com.argiintelligence.backend.ml;

import com.argiintelligence.backend.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/** Client-safe ML failure. The underlying HTTP/client exception is logged, never returned. */
public class MlServiceException extends ApiException {

    private MlServiceException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    /** Connection refused, DNS failure or timeout. */
    static MlServiceException unavailable() {
        return new MlServiceException(HttpStatus.SERVICE_UNAVAILABLE, "ML_SERVICE_UNAVAILABLE",
                "Prediction service is currently unavailable");
    }

    /** ML service answered with 4xx/5xx or a response we cannot use. */
    static MlServiceException badResponse() {
        return new MlServiceException(HttpStatus.BAD_GATEWAY, "ML_SERVICE_ERROR",
                "Prediction service returned an invalid response");
    }
}
