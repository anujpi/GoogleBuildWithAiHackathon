package com.argiintelligence.backend.reference.controller;

import com.argiintelligence.backend.reference.dto.ReferenceScopeResponse;
import com.argiintelligence.backend.reference.service.ReferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reference")
@RequiredArgsConstructor
public class ReferenceController {

    private final ReferenceService referenceService;

    @GetMapping("/scope")
    public ReferenceScopeResponse scope() {
        return referenceService.scope();
    }
}
