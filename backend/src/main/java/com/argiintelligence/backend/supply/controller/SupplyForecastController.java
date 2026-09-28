package com.argiintelligence.backend.supply.controller;

import com.argiintelligence.backend.supply.dto.SupplyForecastRequest;
import com.argiintelligence.backend.supply.dto.SupplyForecastResponse;
import com.argiintelligence.backend.supply.service.SupplyForecastService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Regional supply intelligence. Any authenticated role may read it; it exposes no farm or user data. */
@RestController
@RequestMapping("/api/supply")
@RequiredArgsConstructor
public class SupplyForecastController {

    private final SupplyForecastService supplyForecastService;

    /** Query parameters bind to {@link SupplyForecastRequest}. */
    @GetMapping("/forecast")
    public SupplyForecastResponse forecast(@Valid SupplyForecastRequest request) {
        return supplyForecastService.forecast(request);
    }
}
