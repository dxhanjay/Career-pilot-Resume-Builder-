package com.careerpilot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables JPA auditing, which is what actually populates the timestamp fields
 * declared in {@link com.careerpilot.common.audit.AuditableEntity}.
 *
 * <p>This is a one-line class with a disproportionate consequence. Without
 * {@code @EnableJpaAuditing}, the {@code @CreatedDate} and {@code @LastModifiedDate}
 * annotations are inert: the listener is never registered, the fields stay
 * null, and every insert fails on a {@code NOT NULL} constraint. The failure
 * message points at the column, not at the missing annotation, so it is a
 * genuinely confusing hour to lose.
 *
 * <p>It lives in its own class rather than on the main application class so
 * that integration tests can import exactly the configuration they need, and so
 * that {@code @DataJpaTest} slices behave the same way as the full context.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
