package com.argiintelligence.backend.auth.service;

import com.argiintelligence.backend.auth.dto.LoginRequest;
import com.argiintelligence.backend.auth.dto.LoginResponse;
import com.argiintelligence.backend.auth.dto.RegisterRequest;
import com.argiintelligence.backend.auth.security.JwtService;
import com.argiintelligence.backend.user.dto.UserResponse;
import com.argiintelligence.backend.user.entity.Role;
import com.argiintelligence.backend.user.entity.User;
import com.argiintelligence.backend.user.exception.EmailAlreadyRegisteredException;
import com.argiintelligence.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

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
            return UserResponse.from(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            // Two registrations for the same email raced past the check above.
            throw new EmailAlreadyRegisteredException();
        }
    }

    /** Throws an AuthenticationException for unknown email, wrong password or disabled account alike. */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = normalize(request.email());
        authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        User user = users.findByEmail(email).orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        JwtService.IssuedToken token = jwtService.issue(user);
        return new LoginResponse(token.value(), "Bearer", token.expiresInSeconds(), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        // The JWT filter already confirmed this user exists and is enabled for this request.
        return users.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> new BadCredentialsException("Invalid token"));
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
