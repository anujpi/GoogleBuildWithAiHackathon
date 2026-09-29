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

    /** 422: outside the D2 scope, series missing, farm without an in-scope district, or season OTHER. */
    public static ApiException unsupportedInput(String field, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "UNSUPPORTED_INPUT", message,
                List.of(new FieldViolation(field, message)));
    }

    /** 400 on one named field, for rules checked outside Bean Validation. */
    public static ApiException validation(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed",
                List.of(new FieldViolation(field, message)));
    }

    /** 409: last-admin rule, owner already set, assignment for the wrong role. */
    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    /** 404 with a resource-specific code; used for resources that are missing or not visible to the caller. */
    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
