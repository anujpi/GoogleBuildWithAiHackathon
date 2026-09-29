package com.argiintelligence.backend.admin.service;

import com.argiintelligence.backend.admin.dto.AdminDtos.UserPatchRequest;
import com.argiintelligence.backend.audit.entity.AuditAction;
import com.argiintelligence.backend.audit.service.AuditService;
import com.argiintelligence.backend.common.api.PageResponse;
import com.argiintelligence.backend.common.exception.ApiException;
import com.argiintelligence.backend.farm.dto.FarmSummary;
import com.argiintelligence.backend.farm.service.FarmService;
import com.argiintelligence.backend.reference.repository.RefDistrictRepository;
import com.argiintelligence.backend.user.dto.UserResponse;
import com.argiintelligence.backend.user.entity.Role;
import com.argiintelligence.backend.user.entity.User;
import com.argiintelligence.backend.user.entity.UserDistrictAssignment;
import com.argiintelligence.backend.user.repository.UserDistrictAssignmentRepository;
import com.argiintelligence.backend.user.repository.UserRepository;
import com.argiintelligence.backend.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** User, role, district-assignment and legacy-farm administration (MASTER_SPEC §4.3, §6.7). Every change is audited. */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final Set<Role> DISTRICT_ROLES = EnumSet.of(Role.FPO, Role.AGRICULTURAL_OFFICER);
    private static final Set<Role> FARM_OWNER_ROLES = EnumSet.of(Role.FARMER, Role.FPO);

    private final UserRepository users;
    private final UserDistrictAssignmentRepository assignments;
    private final RefDistrictRepository districts;
    private final UserQueryService userQueries;
    private final FarmService farmService;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Role role, String q, int page, int size) {
        String query = q == null || q.isBlank() ? null : q.strip().toLowerCase(Locale.ROOT);
        var pageable = PageRequest.of(page, size, Sort.by("email"));
        return PageResponse.of(users.search(role, query, pageable), userQueries::toResponse);
    }

    @Transactional
    public UserResponse patch(UUID adminId, UUID userId, UserPatchRequest request) {
        User user = find(userId);
        boolean demotingAdmin = user.getRole() == Role.ADMIN && request.role() != null && request.role() != Role.ADMIN;
        boolean disablingAdmin = user.getRole() == Role.ADMIN && Boolean.FALSE.equals(request.enabled())
                && user.isEnabled();
        if ((demotingAdmin || disablingAdmin) && user.getId().equals(adminId)) {
            throw ApiException.conflict("An admin cannot remove their own ADMIN role or disable themselves");
        }
        if ((demotingAdmin || disablingAdmin) && user.isEnabled() && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw ApiException.conflict("The last enabled ADMIN cannot be demoted or disabled");
        }
        if (request.role() != null && request.role() != user.getRole()) {
            Role previous = user.getRole();
            user.setRole(request.role());
            audit.record(AuditAction.ROLE_CHANGED, adminId, "USER", userId.toString(),
                    Map.of("from", previous.name(), "to", request.role().name()));
        }
        if (request.enabled() != null && request.enabled() != user.isEnabled()) {
            user.setEnabled(request.enabled());
            audit.record(request.enabled() ? AuditAction.USER_ENABLED : AuditAction.USER_DISABLED, adminId, "USER",
                    userId.toString(), Map.of());
        }
        return userQueries.toResponse(user);
    }

    @Transactional
    public UserResponse assignDistricts(UUID adminId, UUID userId, List<String> districtIds) {
        User user = find(userId);
        if (!DISTRICT_ROLES.contains(user.getRole())) {
            throw ApiException.conflict("Districts can only be assigned to FPO or AGRICULTURAL_OFFICER users");
        }
        List<String> wanted = districtIds.stream().distinct().sorted().toList();
        List<String> unknown = wanted.stream().filter(d -> !districts.existsById(d)).toList();
        if (!unknown.isEmpty()) {
            throw ApiException.validation("districtIds", "unknown district(s): " + String.join(", ", unknown));
        }
        assignments.deleteByUserId(userId);
        assignments.flush();
        assignments.saveAll(wanted.stream().map(d -> new UserDistrictAssignment(userId, d, adminId)).toList());
        audit.record(AuditAction.DISTRICTS_ASSIGNED, adminId, "USER", userId.toString(),
                Map.of("districtIds", wanted));
        return userQueries.toResponse(user);
    }

    /** Legacy ownerless farm → first owner (D13). The farm service refuses farms that already have one. */
    @Transactional
    public FarmSummary assignFarmOwner(UUID adminId, UUID farmId, UUID ownerId) {
        User owner = find(ownerId);
        if (!FARM_OWNER_ROLES.contains(owner.getRole())) {
            throw ApiException.conflict("A farm owner must be a FARMER or FPO user");
        }
        return farmService.assignOwner(farmId, ownerId, adminId);
    }

    private User find(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User " + userId + " not found"));
    }
}
