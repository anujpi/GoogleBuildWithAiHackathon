package com.argiintelligence.backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Base for domain errors that map to a specific HTTP status and a stable error code. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
