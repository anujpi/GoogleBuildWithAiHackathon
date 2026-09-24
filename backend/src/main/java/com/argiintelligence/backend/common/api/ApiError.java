package com.argiintelligence.backend.common.api;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

/** Standard error body for every API failure. {@code code} is a stable, machine-readable value. */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldViolation> details) {

    public static ApiError of(HttpStatus status, String code, String message, String path) {
        return new ApiError(Instant.now(), status.value(), code, message, path, List.of());
    }

    public record FieldViolation(String field, String message) {
    }
}
