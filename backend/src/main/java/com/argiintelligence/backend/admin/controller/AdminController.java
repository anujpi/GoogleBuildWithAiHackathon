package com.argiintelligence.backend.admin.controller;

import com.argiintelligence.backend.admin.dto.AdminDtos.DistrictAssignmentRequest;
import com.argiintelligence.backend.admin.dto.AdminDtos.FarmOwnerRequest;
import com.argiintelligence.backend.admin.dto.AdminDtos.MlModelsResponse;
import com.argiintelligence.backend.admin.dto.AdminDtos.SystemHealthResponse;
import com.argiintelligence.backend.admin.dto.AdminDtos.UserPatchRequest;
import com.argiintelligence.backend.admin.service.AdminSystemService;
import com.argiintelligence.backend.admin.service.AdminUserService;
import com.argiintelligence.backend.audit.dto.AuditEventResponse;
import com.argiintelligence.backend.audit.entity.AuditAction;
import com.argiintelligence.backend.audit.service.AuditService;
import com.argiintelligence.backend.auth.security.AuthenticatedUser;
import com.argiintelligence.backend.common.api.PageResponse;
import com.argiintelligence.backend.farm.dto.FarmSummary;
import com.argiintelligence.backend.farm.service.FarmService;
import com.argiintelligence.backend.reference.dto.ReferenceSyncResponse;
import com.argiintelligence.backend.reference.service.ReferenceSyncService;
import com.argiintelligence.backend.user.dto.UserResponse;
import com.argiintelligence.backend.user.entity.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Admin API (MASTER_SPEC §6.7). ADMIN only, enforced in SecurityConfig. The acting admin always comes from the token. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminUserService users;
    private final AdminSystemService system;
    private final AuditService audit;
    private final ReferenceSyncService referenceSync;
    private final FarmService farms;

    @GetMapping("/users")
    public PageResponse<UserResponse> users(@RequestParam(required = false) Role role,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return users.list(role, q, page, size);
    }

    @PatchMapping("/users/{id}")
    public UserResponse patchUser(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                                  @RequestBody UserPatchRequest request) {
        return users.patch(admin.id(), id, request);
    }

    @PutMapping("/users/{id}/districts")
    public UserResponse assignDistricts(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                                        @Valid @RequestBody DistrictAssignmentRequest request) {
        return users.assignDistricts(admin.id(), id, request.districtIds());
    }

    @GetMapping("/system/health")
    public SystemHealthResponse health() {
        return system.health();
    }

    @GetMapping("/ml/models")
    public MlModelsResponse models() {
        return system.models();
    }

    @PostMapping("/reference/sync")
    public ReferenceSyncResponse syncReference(@AuthenticationPrincipal AuthenticatedUser admin) {
        return referenceSync.sync(admin.id());
    }

    @GetMapping("/audit")
    public PageResponse<AuditEventResponse> audit(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return audit.search(action, actorUserId, from, to, page, size);
    }

    @GetMapping("/farms/unowned")
    public List<FarmSummary> unownedFarms() {
        return farms.unownedSummaries();
    }

    @PostMapping("/farms/{id}/owner")
    public FarmSummary assignOwner(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable UUID id,
                                   @Valid @RequestBody FarmOwnerRequest request) {
        return users.assignFarmOwner(admin.id(), id, request.userId());
    }
}
