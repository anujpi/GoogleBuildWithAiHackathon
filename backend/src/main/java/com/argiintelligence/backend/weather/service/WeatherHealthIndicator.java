package com.argiintelligence.backend.weather.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

/**
 * Actuator "weather" component (MASTER_SPEC §6.8). Reports the last known provider outcome without calling the
 * provider (health probes must not spend the external rate limit): DEGRADED after a failure, UP otherwise.
 */
@Component("weather")
@RequiredArgsConstructor
class WeatherHealthIndicator implements HealthIndicator {

    private static final Status DEGRADED = new Status("DEGRADED");

    private final WeatherService weather;

    @Override
    public Health health() {
        return Health.status(weather.providerStatus().lastError() == null ? Status.UP : DEGRADED).build();
    }
}
