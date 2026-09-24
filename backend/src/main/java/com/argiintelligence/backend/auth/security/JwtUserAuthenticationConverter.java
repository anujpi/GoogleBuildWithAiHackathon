package com.argiintelligence.backend.auth.security;

import com.argiintelligence.backend.user.entity.User;
import com.argiintelligence.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

/**
 * Turns a verified token into an {@link AuthenticatedUser}. Loads the user on every request so a
 * disabled or deleted account loses access immediately, and role changes apply without a new token.
 */
@RequiredArgsConstructor
class JwtUserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user = parseId(jwt.getSubject())
                .flatMap(users::findById)
                .filter(User::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Invalid token"));
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole());
        return new UsernamePasswordAuthenticationToken(principal, jwt, principal.authorities());
    }

    private static Optional<UUID> parseId(String subject) {
        try {
            return Optional.of(UUID.fromString(subject));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
