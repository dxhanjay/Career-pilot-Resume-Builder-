package com.careerpilot.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for tests that need the whole application and a real database.
 *
 * <p>The datasource is supplied at runtime by {@link PostgresBackend}, which
 * starts PostgreSQL in a container when Docker is available and from an
 * embedded binary when it is not. Either way it is real PostgreSQL: H2 in
 * "PostgreSQL compatibility mode" accepts SQL that PostgreSQL rejects and has
 * different transaction, index, and type behaviour, so a green H2 suite is not
 * evidence that production will work.
 *
 * <p>One server per JVM rather than one per test class. Starting a database for
 * each of a dozen {@code *IT} classes turns a two-minute suite into a
 * ten-minute one, and a suite people stop running catches nothing.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
public abstract class AbstractIntegrationTest {

    private static final PostgresBackend POSTGRES = PostgresBackend.start();

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::jdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::username);
        registry.add("spring.datasource.password", POSTGRES::password);
    }
}
