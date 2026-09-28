package com.argiintelligence.backend.ml.exception;

import com.argiintelligence.backend.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/** No prediction is possible right now: the ML service is unreachable, timed out, or has no model loaded. */
public class MlUnavailableException extends ApiException {

    private MlUnavailableException(String code, String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }

    public static MlUnavailableException serviceUnreachable() {
        return new MlUnavailableException("ML_SERVICE_UNAVAILABLE",
                "The ML service could not be reached or did not respond in time");
    }

    public static MlUnavailableException modelNotLoaded() {
        return new MlUnavailableException("ML_MODEL_UNAVAILABLE", "The ML service has no supply model loaded");
    }
}
