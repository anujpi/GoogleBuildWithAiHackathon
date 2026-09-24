package com.argiintelligence.backend.farm.controller;

import com.argiintelligence.backend.auth.security.AuthenticatedUser;
import com.argiintelligence.backend.farm.dto.FarmRequest;
import com.argiintelligence.backend.farm.dto.FarmResponse;
import com.argiintelligence.backend.farm.service.FarmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/** The owner always comes from the authenticated principal, never from the request. */
@RestController
@RequestMapping("/api/farms")
@RequiredArgsConstructor
public class FarmController {

    private final FarmService farmService;

    @PostMapping
    public ResponseEntity<FarmResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                               @Valid @RequestBody FarmRequest request) {
        FarmResponse created = farmService.create(user.id(), request);
        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @GetMapping
    public List<FarmResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return farmService.list(user.id());
    }

    @GetMapping("/{id}")
    public FarmResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return farmService.get(user.id(), id);
    }

    @PutMapping("/{id}")
    public FarmResponse update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                               @Valid @RequestBody FarmRequest request) {
        return farmService.update(user.id(), id, request);
    }
}
