package com.argiintelligence.backend.auth.security;

import com.argiintelligence.backend.user.entity.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

/** The principal in the SecurityContext for every authenticated request. Inject with {@code @AuthenticationPrincipal}. */
public record AuthenticatedUser(UUID id, String email, Role role) {

    public List<GrantedAuthority> authorities() {
        return List.of(authority(role));
    }

    static GrantedAuthority authority(Role role) {
        return new SimpleGrantedAuthority("ROLE_" + role.name());
    }
}
