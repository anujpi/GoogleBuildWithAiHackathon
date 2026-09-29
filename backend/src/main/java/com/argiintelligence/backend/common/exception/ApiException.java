package com.argiintelligence.backend.common.exception;

import com.argiintelligence.backend.common.api.ApiError.FieldViolation;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

/** Base for domain errors that map to a specific HTTP status and a stable error code. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    /** Returned as ApiError.details; names the offending field where there is one. */
    private final List<FieldViolation> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(HttpStatus status, String code, String message, List<FieldViolation> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }
}
