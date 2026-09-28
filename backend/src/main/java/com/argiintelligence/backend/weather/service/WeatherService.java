package com.argiintelligence.backend.weather.service;

import com.argiintelligence.backend.common.api.DataClassification;
import com.argiintelligence.backend.common.exception.ApiException;
import com.argiintelligence.backend.farm.dto.FarmResponse;
import com.argiintelligence.backend.farm.service.FarmService;
import com.argiintelligence.backend.weather.dto.WeatherResponse;
import com.argiintelligence.backend.weather.provider.WeatherProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Serves weather only from a real {@link WeatherProvider}. No provider bean is registered today, so every
 * request answers 503 WEATHER_UNAVAILABLE; Spring Boot never generates weather values itself.
 */
@Slf4j
@Service
public class WeatherService {

    private final ObjectProvider<WeatherProvider> provider;
    private final FarmService farmService;

    public WeatherService(ObjectProvider<WeatherProvider> provider, FarmService farmService) {
        this.provider = provider;
        this.farmService = farmService;
    }

    public WeatherResponse forPoint(double latitude, double longitude, int days) {
        return fetch(null, latitude, longitude, days);
    }

    /** Ownership is checked first (another user's farm is a 404), so the farm's existence is never revealed. */
    public WeatherResponse forFarm(UUID ownerId, UUID farmId, int days) {
        FarmResponse.Location loc = farmService.get(ownerId, farmId).location();
        return fetch(farmId, loc.latitude().doubleValue(), loc.longitude().doubleValue(), days);
    }

    private WeatherResponse fetch(UUID farmId, double latitude, double longitude, int days) {
        WeatherProvider source = provider.getIfAvailable();
        if (source == null) {
            throw unavailable("No weather data source is configured");
        }
        WeatherProvider.Report report;
        try {
            report = source.fetch(latitude, longitude, days);
        } catch (RuntimeException ex) {
            log.warn("Weather provider failed for ({}, {})", latitude, longitude, ex);
            throw unavailable("Weather data is currently unavailable");
        }
        // Product rule: the weather API never serves generated values, whatever a provider claims to be.
        if (report.dataClassification() == DataClassification.SYNTHETIC) {
            log.error("Weather provider {} returned SYNTHETIC data; refusing to serve it", report.source());
            throw unavailable("Weather data is currently unavailable");
        }
        return new WeatherResponse(farmId, latitude, longitude,
                new WeatherResponse.Provenance(report.source(), report.dataClassification(), Instant.now(), report.confidence()),
                report.current(), report.daily());
    }

    private static ApiException unavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WEATHER_UNAVAILABLE", message);
    }
}
