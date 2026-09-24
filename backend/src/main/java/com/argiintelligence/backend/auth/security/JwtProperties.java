package com.argiintelligence.backend.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Bound from {@code app.jwt.*} (env: JWT_SECRET, JWT_EXPIRATION). Startup fails if the secret is missing or weak. */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration expiration) {

    /** HS256 needs a key of at least 256 bits. */
    static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET (app.jwt.secret) must be set to at least " + MIN_SECRET_BYTES + " bytes");
        }
        if (expiration == null || expiration.isZero() || expiration.isNegative()) {
            throw new IllegalStateException("JWT_EXPIRATION (app.jwt.expiration) must be a positive duration");
        }
    }

    byte[] secretBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }
}
