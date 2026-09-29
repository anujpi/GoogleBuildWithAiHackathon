package com.argiintelligence.backend.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Allowed origins come from {@code app.cors.allowed-origins} (env: CORS_ALLOWED_ORIGINS).
 * Exposed as a CorsConfigurationSource so Spring Security answers preflights before authentication.
 */
@Configuration
public class CorsConfig {

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration api = new CorsConfiguration();
        api.setAllowedOrigins(allowedOrigins);
        api.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH"));
        api.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Request-Id"));
        // Lets the browser read the correlation id, e.g. to show it with an error.
        api.setExposedHeaders(List.of("X-Request-Id"));
        api.setMaxAge(1800L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", api);
        return source;
    }
}
