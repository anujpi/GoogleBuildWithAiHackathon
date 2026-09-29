package com.argiintelligence.backend.user.dto;

import com.argiintelligence.backend.user.entity.Role;
import com.argiintelligence.backend.user.entity.User;

import java.util.List;
import java.util.UUID;

/**
 * The only user shape the API returns (MASTER_SPEC §6.1). Deliberately has no password or hash field.
 * {@code assignedDistrictIds} is empty for roles without district assignments.
 */
public record UserResponse(UUID id, String fullName, String email, Role role, boolean enabled,
                           List<String> assignedDistrictIds) {

    public static UserResponse from(User user, List<String> assignedDistrictIds) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.isEnabled(),
                List.copyOf(assignedDistrictIds));
    }
}
