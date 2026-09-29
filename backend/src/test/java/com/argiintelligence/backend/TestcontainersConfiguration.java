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

    /**
     * Test-only settings. The JWT key is used only by tests. Tests never reach the network: the startup reference
     * sync is off (tests sync explicitly against a mocked MlClient), and the weather URL points at a closed local
     * port, so an unmocked weather call fails fast instead of calling Open-Meteo.
     */
    @Bean
    DynamicPropertyRegistrar testProperties() {
        return registry -> {
            registry.add("app.jwt.secret", () -> "test-only-jwt-signing-key-0123456789abcdef");
            registry.add("app.reference.sync-on-startup", () -> "false");
            registry.add("app.weather.base-url", () -> "http://127.0.0.1:9");
            registry.add("app.ml.base-url", () -> "http://127.0.0.1:9");
        };
    }
}
