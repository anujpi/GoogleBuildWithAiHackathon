package com.argiintelligence.backend.weather;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Open-Meteo forecast API (free, no key; non-commercial terms, see ml-service/docs/datasets S15). */
@ConfigurationProperties("app.weather")
public record WeatherProperties(
        @DefaultValue("https://api.open-meteo.com") String baseUrl,
        @DefaultValue("PT3S") Duration connectTimeout,
        @DefaultValue("PT6S") Duration readTimeout) {
}
