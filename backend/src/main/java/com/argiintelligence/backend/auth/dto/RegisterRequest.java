package com.argiintelligence.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Public self-registration. There is intentionally no role field: every new account is FARMER. */
public record RegisterRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        // BCrypt only uses the first 72 bytes, so longer passwords are rejected rather than silently truncated.
        @NotBlank @Size(min = 8, max = 72) String password) {
}
