package com.argiintelligence.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgis/postgis:17-3.5")
                .asCompatibleSubstituteFor("postgres"));
    }

    /** Test-only signing key, so tests need no JWT_SECRET env var. Not used outside tests. */
    @Bean
    DynamicPropertyRegistrar jwtTestSecret() {
        return registry -> registry.add("app.jwt.secret", () -> "test-only-jwt-signing-key-0123456789abcdef");
    }
}
