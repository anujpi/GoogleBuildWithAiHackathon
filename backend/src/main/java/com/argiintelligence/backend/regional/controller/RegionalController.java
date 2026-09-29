package com.argiintelligence.backend.regional.controller;

import com.argiintelligence.backend.auth.security.AuthenticatedUser;
import com.argiintelligence.backend.farm.dto.FarmResponse;
import com.argiintelligence.backend.farm.dto.FarmSummary;
import com.argiintelligence.backend.intelligence.dto.CropEvidenceResponse;
import com.argiintelligence.backend.intelligence.dto.RiskAssessmentResponse;
import com.argiintelligence.backend.intelligence.service.CanonicalSeason;
import com.argiintelligence.backend.intelligence.service.CropEvidenceService;
import com.argiintelligence.backend.intelligence.service.RiskService;
import com.argiintelligence.backend.regional.dto.RegionalDistrict;
import com.argiintelligence.backend.regional.service.RegionalService;
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

import java.util.List;
import java.util.UUID;

/**
 * Read-only regional view (MASTER_SPEC §6.6): FPO and AGRICULTURAL_OFFICER for their assigned districts, ADMIN for
 * all. Only GET mappings exist, so nothing here can change a farm. Bodies are the same as the owner endpoints.
 */
@RestController
@RequestMapping("/api/regional")
@RequiredArgsConstructor
public class RegionalController {

    private static final String ID = "[a-z0-9][a-z0-9-]{0,49}";

    private final RegionalService regional;
    private final WeatherService weatherService;
    private final CropEvidenceService cropEvidenceService;
    private final RiskService riskService;

    @GetMapping("/districts")
    public List<RegionalDistrict> districts(@AuthenticationPrincipal AuthenticatedUser user) {
        return regional.districts(user);
    }

    @GetMapping("/farms")
    public List<FarmSummary> farms(@AuthenticationPrincipal AuthenticatedUser user,
                                   @RequestParam @Pattern(regexp = ID, message = "must be a lower-case district id")
                                   String districtId) {
        return regional.farms(user, districtId);
    }

    @GetMapping("/farms/{id}")
    public FarmResponse farm(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return regional.farm(user, id);
    }

    @GetMapping("/farms/{id}/weather")
    public WeatherResponse weather(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                   @RequestParam(defaultValue = "7") @Min(1) @Max(14) int days) {
        return weatherService.forFarm(regional.farm(user, id), days);
    }

    @GetMapping("/farms/{id}/crop-evidence")
    public CropEvidenceResponse cropEvidence(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @RequestParam(required = false) @Pattern(regexp = CanonicalSeason.PATTERN,
                    message = CanonicalSeason.MESSAGE) String season,
            @RequestParam(required = false) @Min(1950) @Max(2100) Integer cropYear) {
        return cropEvidenceService.evidence(regional.farm(user, id), season, cropYear);
    }

    @GetMapping("/farms/{id}/risk")
    public RiskAssessmentResponse risk(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @RequestParam @Pattern(regexp = ID, message = "must be a lower-case crop id") String cropId,
            @RequestParam(required = false) @Pattern(regexp = CanonicalSeason.PATTERN,
                    message = CanonicalSeason.MESSAGE) String season) {
        return riskService.assess(regional.farm(user, id), cropId, season);
    }
}
