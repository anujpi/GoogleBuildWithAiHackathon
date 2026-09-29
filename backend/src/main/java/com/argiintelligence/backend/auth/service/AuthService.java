package com.argiintelligence.backend.auth.service;

import com.argiintelligence.backend.audit.entity.AuditAction;
import com.argiintelligence.backend.audit.service.AuditService;
import com.argiintelligence.backend.auth.dto.LoginRequest;
import com.argiintelligence.backend.auth.dto.LoginResponse;
import com.argiintelligence.backend.auth.dto.RegisterRequest;
import com.argiintelligence.backend.auth.security.JwtService;
import com.argiintelligence.backend.user.dto.UserResponse;
import com.argiintelligence.backend.user.entity.Role;
import com.argiintelligence.backend.user.entity.User;
import com.argiintelligence.backend.user.exception.EmailAlreadyRegisteredException;
import com.argiintelligence.backend.user.repository.UserRepository;
import com.argiintelligence.backend.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final UserQueryService userQueries;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuditService audit;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = new User();
        user.setFullName(request.fullName().strip());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.FARMER);
        user.setEnabled(true);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two registrations for the same email raced past the check above.
            throw new EmailAlreadyRegisteredException();
        }
        audit.record(AuditAction.USER_REGISTERED, user.getId(), "USER", user.getId().toString(), Map.of());
        return userQueries.toResponse(user);
    }

    /** Throws an AuthenticationException for unknown email, wrong password or disabled account alike. */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = normalize(request.email());
        try {
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        } catch (AuthenticationException ex) {
            // Only the normalized email is stored; never the password or the failure reason.
            audit.recordIndependently(AuditAction.LOGIN_FAILED, null, "USER", null, Map.of("email", email));
            throw ex;
        }
        User user = users.findByEmail(email).orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        JwtService.IssuedToken token = jwtService.issue(user);
        audit.recordIndependently(AuditAction.LOGIN_SUCCEEDED, user.getId(), "USER", user.getId().toString(),
                Map.of());
        return new LoginResponse(token.value(), "Bearer", token.expiresInSeconds(), userQueries.toResponse(user));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        // The JWT filter already confirmed this user exists and is enabled for this request.
        return users.findById(userId).map(userQueries::toResponse)
                .orElseThrow(() -> new BadCredentialsException("Invalid token"));
    }

    public static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
