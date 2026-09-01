package com.careerpilot.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * A real PostgreSQL server for the integration suite, started once per JVM.
 *
 * <p>Testcontainers when a Docker daemon answers, an embedded PostgreSQL binary
 * otherwise. Both are genuine PostgreSQL — the standing decision is "real
 * PostgreSQL, never H2", and neither path breaks it. H2 in "PostgreSQL
 * compatibility mode" accepts SQL that PostgreSQL rejects and has different
 * transaction, index, and type behaviour, so a green H2 suite is not evidence
 * that production will work.
 *
 * <p>The fallback exists because the alternative is worse. Without it the whole
 * integration suite is unrunnable on a machine with no Docker, and a suite that
 * only runs in CI is a suite that only catches mistakes after they are pushed.
 *
 * <p>Started eagerly in a static initialiser and stopped by a shutdown hook, so
 * one server serves every {@code *IT} class in the run rather than one per class.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
final class PostgresBackend {

    private static final Logger log = LoggerFactory.getLogger(PostgresBackend.class);

    private static final String DATABASE = "careerpilot_test";
    private static final String USERNAME = "test";
    private static final String PASSWORD = "test";

    private final String jdbcUrl;
    private final String username;
    private final String password;

    private PostgresBackend(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    static PostgresBackend start() {
        if (dockerIsAvailable()) {
            return startContainer();
        }
        log.warn("""
                No Docker daemon found. Falling back to an embedded PostgreSQL binary.
                This is still real PostgreSQL, but CI runs the container path — if a test \
                passes here and fails there, suspect the difference.""");
        return startEmbedded();
    }

    private static boolean dockerIsAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            // Testcontainers throws rather than returning false on some
            // misconfigurations. Either way the answer is "no Docker".
            return false;
        }
    }

    private static PostgresBackend startContainer() {
        @SuppressWarnings("resource") // Stopped by the shutdown hook below.
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName(DATABASE)
                .withUsername(USERNAME)
                .withPassword(PASSWORD)
                .withReuse(false);
        container.start();
        Runtime.getRuntime().addShutdownHook(new Thread(container::stop));
        return new PostgresBackend(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    private static PostgresBackend startEmbedded() {
        try {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException e) {
                    log.debug("Embedded PostgreSQL did not shut down cleanly", e);
                }
            }));
            // The embedded server exposes the default 'postgres' database owned
            // by a superuser, which is what the migrations need in order to
            // CREATE EXTENSION.
            return new PostgresBackend(
                    postgres.getJdbcUrl("postgres", "postgres"), "postgres", "postgres");
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Could not start an embedded PostgreSQL. Start Docker, or check that the "
                            + "embedded binaries could be downloaded.", e);
        }
    }

    String jdbcUrl() {
        return jdbcUrl;
    }

    String username() {
        return username;
    }

    String password() {
        return password;
    }
}
