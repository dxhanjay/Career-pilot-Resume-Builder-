package com.careerpilot.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests, providing a real PostgreSQL instance.
 *
 * <p><strong>Why PostgreSQL in Docker rather than H2.</strong> H2's
 * "PostgreSQL compatibility mode" is a compatibility <em>approximation</em>. It
 * accepts SQL that PostgreSQL rejects, rejects SQL that PostgreSQL accepts, and
 * differs on the things this schema actually depends on — {@code TIMESTAMPTZ}
 * semantics, {@code JSONB}, partial indexes, {@code ON CONFLICT}, and
 * {@code SELECT … FOR UPDATE SKIP LOCKED}, which the entire job engine is built
 * on. A green H2 suite is not evidence that production will work; it is evidence
 * that a different database works. The Flyway migrations would also never be
 * exercised against their real target.
 *
 * <p>The cost is honest and worth stating: <strong>Docker must be running</strong>
 * to execute integration tests. {@code mvn test} (unit tests only) does not need
 * it; {@code mvn verify} does. CI runners provide Docker by default.
 *
 * <p>The container is {@code static}, so one instance is shared by every test
 * class that extends this. Starting a fresh PostgreSQL per class would add
 * several seconds each and make the suite slow enough that people stop running
 * it. Testcontainers stops it when the JVM exits.
 *
 * <p>{@code @ServiceConnection} is what removes the boilerplate that used to be
 * required here: it discovers the container's JDBC URL, username, and password
 * and feeds them to Spring automatically. No {@code @DynamicPropertySource}
 * block, and no risk of it drifting out of sync with the container definition.
 *
 * <p>Note that Flyway runs against this container on first context start, so
 * every integration test is also a test that the migrations apply cleanly to an
 * empty database.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
public abstract class AbstractIntegrationTest {

    /**
     * Shared PostgreSQL container.
     *
     * <p>The image tag is pinned to a specific minor version rather than
     * {@code latest}. A floating tag means the database under test can change
     * without a commit, which turns a reproducible suite into an intermittent
     * one — and the version must match production, or the tests are verifying
     * behaviour that will not exist when it matters.
     */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("careerpilot_test")
                    .withUsername("test")
                    .withPassword("test")
                    // Reuse across runs when the developer has opted in via
                    // ~/.testcontainers.properties. Off by default: reuse is a
                    // local speed optimisation, and a CI run must always start
                    // from a genuinely empty database.
                    .withReuse(false);
}
