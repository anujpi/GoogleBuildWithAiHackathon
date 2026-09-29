package com.argiintelligence.backend.intelligence.controller;

import com.argiintelligence.backend.intelligence.dto.SupplyEstimateResponse;
import com.argiintelligence.backend.intelligence.service.CanonicalSeason;
import com.argiintelligence.backend.intelligence.service.SupplyService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/intelligence/supply (MASTER_SPEC §6.5, §8). The old demand/supply-demand/risk/recommendation
 * placeholders and supply-forecast were removed (§6.9); crop evidence and risk live under /api/farms/{id}.
 */
@RestController
@RequestMapping("/api/intelligence")
@RequiredArgsConstructor
public class IntelligenceController {

    /** Lower-case slug such as "up-agra" or "potato". */
    static final String ID = "[a-z0-9][a-z0-9-]{0,49}";
    static final String ID_MESSAGE = "must be a lower-case id (a-z, 0-9, '-'), at most 50 characters";

    private final SupplyService service;

    @GetMapping("/supply")
    public SupplyEstimateResponse supply(
            @RequestParam @Pattern(regexp = ID, message = ID_MESSAGE) String districtId,
            @RequestParam @Pattern(regexp = ID, message = ID_MESSAGE) String cropId,
            @RequestParam @Pattern(regexp = CanonicalSeason.PATTERN, message = CanonicalSeason.MESSAGE) String season,
            @RequestParam @Min(1950) @Max(2100) int cropYear,
            @RequestParam(required = false) @Positive Double areaHectares) {
        return service.supply(districtId, cropId, season, cropYear, areaHectares);
    }
}
