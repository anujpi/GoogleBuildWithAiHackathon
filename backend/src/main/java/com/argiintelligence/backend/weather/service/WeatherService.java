package com.argiintelligence.backend.weather.service;

import com.argiintelligence.backend.common.exception.ApiException;
import com.argiintelligence.backend.farm.dto.FarmResponse;
import com.argiintelligence.backend.farm.service.FarmService;
import com.argiintelligence.backend.weather.dto.WeatherResponse;
import com.argiintelligence.backend.weather.provider.WeatherProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final WeatherProvider provider;
    private final FarmService farmService;

    public WeatherResponse forPoint(double latitude, double longitude, int days) {
        return fetch(null, latitude, longitude, days);
    }

    /** Ownership is enforced by FarmService: another user's farm is a 404. */
    public WeatherResponse forFarm(UUID ownerId, UUID farmId, int days) {
        FarmResponse.Location loc = farmService.get(ownerId, farmId).location();
        return fetch(farmId, loc.latitude().doubleValue(), loc.longitude().doubleValue(), days);
    }

    private WeatherResponse fetch(UUID farmId, double latitude, double longitude, int days) {
        WeatherProvider.Report report;
        try {
            report = provider.fetch(latitude, longitude, days);
        } catch (RuntimeException ex) {
            log.warn("Weather provider failed for ({}, {})", latitude, longitude, ex);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "WEATHER_UNAVAILABLE", "Weather data is currently unavailable");
        }
        return new WeatherResponse(farmId, latitude, longitude,
                new WeatherResponse.Provenance(report.source(), report.dataClassification(), Instant.now(), report.confidence()),
                report.current(), report.daily());
    }
}
