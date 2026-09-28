package com.argiintelligence.backend.ml.exception;

import com.argiintelligence.backend.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/** The ML service answered, but not with a response this backend can trust (bad status, body or contract). */
public class MlInvalidResponseException extends ApiException {

    public MlInvalidResponseException(String message) {
        super(HttpStatus.BAD_GATEWAY, "ML_INVALID_RESPONSE", message);
    }
}
