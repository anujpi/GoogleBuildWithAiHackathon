package com.argiintelligence.backend.weather.provider;

import com.argiintelligence.backend.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Client-safe weather provider failure with the MASTER_SPEC §7.3 / §13 codes. The message names the provider;
 * the underlying exception is logged, never returned, and the body never contains weather values.
 */
public class WeatherProviderException extends ApiException {

    private WeatherProviderException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    /** Connection failure or timeout. */
    public static WeatherProviderException unavailable(String provider) {
        return new WeatherProviderException(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE",
                "The weather provider " + provider + " is unavailable");
    }

    /** HTTP 429. */
    public static WeatherProviderException rateLimited(String provider) {
        return new WeatherProviderException(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_RATE_LIMITED",
                "The weather provider " + provider + " is rate-limiting requests; try again later");
    }

    /** Other 4xx/5xx, malformed or incomplete data, or data that breaks the provenance rules. */
    public static WeatherProviderException invalidResponse(String provider) {
        return new WeatherProviderException(HttpStatus.BAD_GATEWAY, "UPSTREAM_INVALID_RESPONSE",
                "The weather provider " + provider + " returned an invalid response");
    }
}
