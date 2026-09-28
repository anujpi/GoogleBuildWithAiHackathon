package com.argiintelligence.backend.ml;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.ml")
public record MlServiceProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
