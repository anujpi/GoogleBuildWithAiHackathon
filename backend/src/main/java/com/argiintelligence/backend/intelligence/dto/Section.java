package com.argiintelligence.backend.intelligence.dto;

/**
 * One independently computed part of the intelligence report. A failure in one section (ML down, no history,
 * unsupported crop) never hides the others; it is reported here with the reason instead of a substitute value.
 */
public record Section<T>(String status, T data, String errorCode, String errorMessage) {

    public static <T> Section<T> ok(T data) {
        return new Section<>("OK", data, null, null);
    }

    public static <T> Section<T> unavailable(String code, String message) {
        return new Section<>("UNAVAILABLE", null, code, message);
    }

    public boolean isOk() {
        return "OK".equals(status);
    }
}
