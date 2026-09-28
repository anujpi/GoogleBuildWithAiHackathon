package com.argiintelligence.backend.ml;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Connection settings for the Python ML service (ml-service/). */
@ConfigurationProperties("app.ml")
public record MlProperties(
        @DefaultValue("http://localhost:8000") String baseUrl,
        @DefaultValue("PT2S") Duration connectTimeout,
        @DefaultValue("PT10S") Duration readTimeout) {
}
