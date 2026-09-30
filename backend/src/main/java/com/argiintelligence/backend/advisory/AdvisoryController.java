package com.argiintelligence.backend.advisory;

import com.argiintelligence.backend.advisory.AdvisoryService.AdvisoryRequest;
import com.argiintelligence.backend.advisory.AdvisoryService.AdvisoryResponse;
import com.argiintelligence.backend.auth.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Farmer-facing explanation of a farm's intelligence report (Gemini, or a labelled template fallback). */
@RestController
@RequestMapping("/api/advisories")
@RequiredArgsConstructor
public class AdvisoryController {

    private final AdvisoryService advisoryService;

    @PostMapping
    public AdvisoryResponse advise(@AuthenticationPrincipal AuthenticatedUser user,
                                   @Valid @RequestBody AdvisoryRequest request) {
        return advisoryService.advise(user.id(), request);
    }
}
