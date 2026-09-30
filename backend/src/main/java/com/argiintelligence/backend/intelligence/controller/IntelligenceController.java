package com.argiintelligence.backend.intelligence.controller;

import com.argiintelligence.backend.auth.security.AuthenticatedUser;
import com.argiintelligence.backend.intelligence.dto.IntelligenceReport;
import com.argiintelligence.backend.intelligence.service.IntelligenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Farm-scoped intelligence. The farm must belong to the caller (404 otherwise, as for /api/farms). */
@RestController
@RequestMapping("/api/intelligence")
@RequiredArgsConstructor
public class IntelligenceController {

    private final IntelligenceService intelligenceService;

    @GetMapping("/farms/{farmId}")
    public IntelligenceReport report(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID farmId,
                                     @RequestParam(required = false) String crop) {
        return intelligenceService.report(user.id(), farmId, crop);
    }
}
