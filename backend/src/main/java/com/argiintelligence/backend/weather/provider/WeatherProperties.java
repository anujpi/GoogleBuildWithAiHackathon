package com.argiintelligence.backend.weather.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** {@code app.weather.*} (env: WEATHER_BASE_URL, WEATHER_*_TIMEOUT, WEATHER_CACHE_TTL). No API key is needed. */
@ConfigurationProperties("app.weather")
public record WeatherProperties(String baseUrl, Duration connectTimeout, Duration readTimeout, Duration cacheTtl) {
}
