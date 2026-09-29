package com.argiintelligence.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Startup, health (MASTER_SPEC §6.8) and migrations (§5.1, §16) against a real PostGIS container. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BackendApplicationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PostgreSQLContainer postgres;

    @Test
    void healthIsDegradedNotDownWhenMlIsUnreachable() throws Exception {
        // The test configuration points ML at a closed port, so ML is unreachable here by design.
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.ml.status").value("DEGRADED"))
                .andExpect(jsonPath("$.components.weather.status").value("UP"));
    }

    @Test
    void cleanDatabaseMigratesToV7() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);
        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7");
    }

    @Test
    void v7SeedsTheFourScopeCropsWithoutInventedRequirements() {
        assertThat(jdbc.queryForList("select crop_id from ref_crop order by crop_id", String.class))
                .contains("maize", "onion", "potato", "wheat");
        List<Map<String, Object>> reqs = jdbc.queryForList("select * from crop_requirement order by crop_id");
        assertThat(reqs).extracting(r -> r.get("crop_id")).containsExactly("maize", "onion", "potato", "wheat");
        // Blocker B2: nothing transcribed yet, so every limit and citation is NULL, never a guess.
        for (Map<String, Object> r : reqs) {
            for (String column : List.of("ph_abs_min", "ph_opt_min", "ph_opt_max", "ph_abs_max", "temp_abs_min_c",
                    "temp_opt_min_c", "temp_opt_max_c", "temp_abs_max_c", "source_url", "retrieved_on")) {
                assertThat(r.get(column)).as(r.get("crop_id") + "." + column).isNull();
            }
            assertThat(r.get("source")).isEqualTo("FAO EcoCrop");
        }
    }

    /**
     * A database that already holds a legacy ownerless farm (created before V3) is migrated V4..V7 without losing
     * it (MASTER_SPEC D13, §16). Runs Flyway against a separate, fresh database in the same container.
     */
    @Test
    void legacyOwnerlessFarmsSurviveTheNewMigrations() {
        jdbc.execute("drop database if exists legacy_migration_test");
        jdbc.execute("create database legacy_migration_test");
        String url = postgres.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/legacy_migration_test$1");
        DriverManagerDataSource legacy = new DriverManagerDataSource(url, postgres.getUsername(),
                postgres.getPassword());

        Flyway.configure().dataSource(legacy).target("3").load().migrate();
        JdbcTemplate legacyJdbc = new JdbcTemplate(legacy);
        UUID location = UUID.randomUUID();
        UUID farm = UUID.randomUUID();
        legacyJdbc.update("insert into farm_location (id, latitude, longitude, state, district) "
                + "values (?, 26.9, 80.9, 'Uttar Pradesh', 'Lucknow')", location);
        legacyJdbc.update("insert into farm (id, name, area, area_unit, irrigation_type, season, location_id, "
                + "created_at, updated_at) values (?, 'Legacy', 2, 'HECTARE', 'DRIP', 'RABI', ?, now(), now())",
                farm, location);

        Flyway.configure().dataSource(legacy).load().migrate();

        Map<String, Object> row = legacyJdbc.queryForMap("select owner_id, district_id, name from farm where id = ?",
                farm);
        assertThat(row.get("name")).isEqualTo("Legacy");
        assertThat(row.get("owner_id")).isNull();
        assertThat(row.get("district_id")).as("nothing is guessed").isNull();
        assertThat(legacyJdbc.queryForObject("select count(*) from farm", Integer.class)).isEqualTo(1);
    }
}
