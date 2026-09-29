package com.argiintelligence.backend.weather.controller;

import com.argiintelligence.backend.weather.dto.WeatherResponse;
import com.argiintelligence.backend.weather.service.WeatherService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/weather for a point (MASTER_SPEC §6.4). Farm weather is GET /api/farms/{id}/weather (and the regional
 * equivalent); the old /api/weather/farms/{farmId} path was removed (§6.9).
 */
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping
    public WeatherResponse forPoint(@RequestParam @DecimalMin("-90") @DecimalMax("90") double latitude,
                                    @RequestParam @DecimalMin("-180") @DecimalMax("180") double longitude,
                                    @RequestParam(defaultValue = "7") @Min(1) @Max(14) int days) {
        return weatherService.forLocation(null, latitude, longitude, days);
    }
}
