package com.argiintelligence.backend.auth.dto;

import com.argiintelligence.backend.user.dto.UserResponse;

/** {@code expiresIn} is in seconds. Send the token as {@code Authorization: Bearer <accessToken>}. */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
