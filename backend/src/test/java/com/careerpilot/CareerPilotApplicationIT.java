package com.careerpilot;

import com.careerpilot.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: the Spring context starts and the schema migrates.
 *
 * <p>This is the least glamorous test in the codebase and among the most
 * valuable. A context-load failure is the single most common way a Spring Boot
 * deployment breaks — a missing bean, an unresolvable {@code ${PLACEHOLDER}}, a
 * circular dependency, a {@code @ConfigurationProperties} record that will not
 * bind, a migration with a syntax error. Every one of those produces a container
 * that starts, fails, restarts, and fails again.
 *
 * <p>Catching that on a laptop in twenty seconds is worth considerably more than
 * catching it in a Railway deploy log at midnight.
 *
 * <p>Named {@code *IT} rather than {@code *Test} so Failsafe runs it during
 * {@code mvn verify} and Surefire skips it during {@code mvn test}. It needs
 * Docker; the fast unit suite must not.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@DisplayName("Application context")
class CareerPilotApplicationIT extends AbstractIntegrationTest {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Constructor injection, consistent with the rest of the codebase and with
     * the ArchUnit rule forbidding field injection. JUnit 5 supports constructor
     * parameters on test classes via Spring's test extension.
     *
     * @param jdbcTemplate template bound to the Testcontainers database
     */
    CareerPilotApplicationIT(@Autowired JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    @DisplayName("starts without error")
    void contextLoads() {
        // Reaching the body means every bean was constructed, every
        // @ConfigurationProperties record bound, and every placeholder resolved.
        assertThat(jdbcTemplate).isNotNull();
    }

    @Test
    @DisplayName("applies Flyway migrations to an empty database")
    void flywayMigrationsApply() {
        Integer appliedMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true",
                Integer.class);

        assertThat(appliedMigrations)
                .as("V1 should have been applied to the fresh container")
                .isNotNull()
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("installs the pg_trgm extension required by admin search")
    void trigramExtensionIsInstalled() {
        Integer installed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_extension WHERE extname = 'pg_trgm'",
                Integer.class);

        assertThat(installed)
                .as("V1__enable_extensions.sql should have installed pg_trgm")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("runs against PostgreSQL, not an in-memory substitute")
    void databaseIsPostgres() {
        String productName = jdbcTemplate.queryForObject("SELECT version()", String.class);

        // Guards against someone "simplifying" the suite onto H2 later. H2 in
        // PostgreSQL-compatibility mode passes many tests that real PostgreSQL
        // fails, so this assertion protects the value of every other
        // integration test in the project.
        assertThat(productName)
                .as("Integration tests must run against real PostgreSQL")
                .containsIgnoringCase("PostgreSQL");
    }
}
