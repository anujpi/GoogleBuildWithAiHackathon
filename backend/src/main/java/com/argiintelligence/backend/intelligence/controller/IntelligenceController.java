package com.argiintelligence.backend.intelligence.controller;

import com.argiintelligence.backend.auth.security.AuthenticatedUser;
import com.argiintelligence.backend.intelligence.dto.SupplyForecastResponse;
import com.argiintelligence.backend.intelligence.service.IntelligenceService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Contract: docs/intelligence-api.md. Endpoints without a connected prediction source answer
 * 503 PREDICTION_UNAVAILABLE after validation and authorization, so their success bodies are not served yet.
 */
@RestController
@RequestMapping("/api/intelligence")
@RequiredArgsConstructor
public class IntelligenceController {

    /** Lower-case slug such as "ka" or "tomato". */
    private static final String ID = "[a-z0-9][a-z0-9-]{0,49}";
    private static final String ID_MESSAGE = "must be a lower-case id (a-z, 0-9, '-'), at most 50 characters";

    private final IntelligenceService service;

    @GetMapping("/supply-forecast")
    public SupplyForecastResponse supplyForecast(@RequestParam @Pattern(regexp = ID, message = ID_MESSAGE) String regionId,
                                                 @RequestParam @Pattern(regexp = ID, message = ID_MESSAGE) String cropId,
                                                 @RequestParam(defaultValue = "3") @Min(1) @Max(12) int horizonMonths) {
        return service.supplyForecast(regionId, cropId, horizonMonths);
    }

    @GetMapping("/demand-forecast")
    public void demandForecast(@RequestParam @Pattern(regexp = ID, message = ID_MESSAGE) String regionId,
                               @RequestParam @Pattern(regexp = ID, message = ID_MESSAGE) String cropId,
                               @RequestParam(defaultValue = "3") @Min(1) @Max(12) int horizonMonths) {
        service.demandForecast(regionId, cropId, horizonMonths);
    }

    @GetMapping("/supply-demand")
    public void supplyDemand(@RequestParam @Pattern(regexp = ID, message = ID_MESSAGE) String regionId,
                             @RequestParam @Pattern(regexp = ID, message = ID_MESSAGE) String cropId,
                             @RequestParam(defaultValue = "3") @Min(1) @Max(12) int horizonMonths) {
        service.supplyDemand(regionId, cropId, horizonMonths);
    }

    @GetMapping("/crop-recommendations")
    public void cropRecommendations(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam UUID farmId) {
        service.cropRecommendations(user.id(), farmId);
    }

    @GetMapping("/agricultural-risk")
    public void agriculturalRisk(@RequestParam @Pattern(regexp = ID, message = ID_MESSAGE) String regionId,
                                 @RequestParam @Pattern(regexp = ID, message = ID_MESSAGE) String cropId) {
        service.agriculturalRisk(regionId, cropId);
    }
}
