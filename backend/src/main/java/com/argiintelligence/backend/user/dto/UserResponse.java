package com.argiintelligence.backend.user.dto;

import com.argiintelligence.backend.user.entity.Role;
import com.argiintelligence.backend.user.entity.User;

import java.util.UUID;

/** The only user shape the API returns. Deliberately has no password or hash field. */
public record UserResponse(UUID id, String fullName, String email, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
    }
}
