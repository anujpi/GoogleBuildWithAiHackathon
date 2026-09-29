package com.argiintelligence.backend.intelligence.controller;

import com.argiintelligence.backend.auth.security.AuthenticatedUser;
import com.argiintelligence.backend.farm.service.FarmService;
import com.argiintelligence.backend.intelligence.dto.CropEvidenceResponse;
import com.argiintelligence.backend.intelligence.dto.RiskAssessmentResponse;
import com.argiintelligence.backend.intelligence.service.CanonicalSeason;
import com.argiintelligence.backend.intelligence.service.CropEvidenceService;
import com.argiintelligence.backend.intelligence.service.RiskService;
import com.argiintelligence.backend.weather.dto.WeatherResponse;
import com.argiintelligence.backend.weather.service.WeatherService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Intelligence for the caller's own farm (MASTER_SPEC §6.2; FARMER/FPO). Ownership is resolved first, so another
 * user's farm is a 404 before any upstream call is made. The read-only regional equivalents live in
 * RegionalController and reuse the same services.
 */
@RestController
@RequestMapping("/api/farms/{id}")
@RequiredArgsConstructor
public class FarmIntelligenceController {

    private final FarmService farmService;
    private final WeatherService weatherService;
    private final CropEvidenceService cropEvidenceService;
    private final RiskService riskService;

    @GetMapping("/weather")
    public WeatherResponse weather(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                   @RequestParam(defaultValue = "7") @Min(1) @Max(14) int days) {
        return weatherService.forFarm(farmService.get(user.id(), id), days);
    }

    @GetMapping("/crop-evidence")
    public CropEvidenceResponse cropEvidence(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @RequestParam(required = false) @Pattern(regexp = CanonicalSeason.PATTERN,
                    message = CanonicalSeason.MESSAGE) String season,
            @RequestParam(required = false) @Min(1950) @Max(2100) Integer cropYear) {
        return cropEvidenceService.evidence(farmService.get(user.id(), id), season, cropYear);
    }

    @GetMapping("/risk")
    public RiskAssessmentResponse risk(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @RequestParam @Pattern(regexp = IntelligenceController.ID,
                    message = IntelligenceController.ID_MESSAGE) String cropId,
            @RequestParam(required = false) @Pattern(regexp = CanonicalSeason.PATTERN,
                    message = CanonicalSeason.MESSAGE) String season) {
        return riskService.assess(farmService.get(user.id(), id), cropId, season);
    }
}
