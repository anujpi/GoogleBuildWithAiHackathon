package com.argiintelligence.backend.ml.dto;

/** ML error envelope: {@code {"error": {"code", "message", "details"}}}. Details are not used. */
public record MlError(Body error) {

    public record Body(String code, String message) {
    }
}
