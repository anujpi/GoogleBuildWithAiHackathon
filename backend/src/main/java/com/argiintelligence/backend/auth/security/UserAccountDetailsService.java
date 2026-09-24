package com.argiintelligence.backend.auth.security;

import com.argiintelligence.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** Login lookup by email for Spring Security's DaoAuthenticationProvider (password + disabled checks). */
@Component
@RequiredArgsConstructor
class UserAccountDetailsService implements UserDetailsService {

    private final UserRepository users;

    @Override
    public UserDetails loadUserByUsername(String email) {
        return users.findByEmail(email.strip().toLowerCase(Locale.ROOT))
                .map(u -> User.withUsername(u.getEmail())
                        .password(u.getPasswordHash())
                        .disabled(!u.isEnabled())
                        .authorities(List.of(AuthenticatedUser.authority(u.getRole())))
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
