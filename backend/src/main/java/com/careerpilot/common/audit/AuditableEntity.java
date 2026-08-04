package com.careerpilot.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Base class supplying {@code created_at} and {@code updated_at} to every
 * entity that needs them.
 *
 * <p>{@code @MappedSuperclass} means this is not a table of its own — the
 * columns are folded into each subclass's table. That is the correct mechanism
 * here: the alternative is repeating two fields and two annotations across
 * roughly twenty entities, where the twenty-first will inevitably be written
 * with a subtly different column name or, worse, a timestamp set by hand in a
 * service method and therefore sometimes forgotten.
 *
 * <p>{@link Instant} rather than {@code LocalDateTime}, matching the
 * {@code TIMESTAMPTZ} choice in the database design. A {@code LocalDateTime}
 * carries no zone, so the value's meaning depends on the JVM's default — which
 * differs between a developer's laptop and a Railway container, and changes
 * under daylight saving. {@code Instant} is an unambiguous point in time.
 *
 * <p>Population depends on {@code @EnableJpaAuditing}, which is declared in
 * {@code JpaConfig}. Without it these fields stay null and every row violates
 * its {@code NOT NULL} constraint — a failure that appears at the first insert
 * rather than at startup, which is why the configuration and this class are
 * introduced in the same phase.
 *
 * <p>Not every entity should extend this. Append-only tables that record their
 * own timestamp semantics — {@code audit_logs}, {@code ai_usage_logs} — declare
 * a single {@code created_at} and are never updated; inheriting an
 * {@code updated_at} they will never use would be misleading.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * @return when the row was first persisted
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * @return when the row was last modified
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // No setters. These fields are owned by the auditing infrastructure; a
    // setter would let application code rewrite history, which defeats the
    // reason for having audit columns at all.
}
