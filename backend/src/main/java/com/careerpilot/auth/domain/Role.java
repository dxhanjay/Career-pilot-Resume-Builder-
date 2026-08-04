package com.careerpilot.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * A grantable role. Reference data, seeded by migration V2.
 *
 * <p>Deliberately minimal and immutable from the application's perspective:
 * there is no setter and no public constructor that assigns a new name. Roles
 * are created by migration, not by code, so an entity that could rewrite them
 * would only ever be a way to introduce drift between environments.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private RoleName name;

    @Column(name = "description", length = 200)
    private String description;

    /** Required by JPA. Not for application use. */
    protected Role() {
    }

    public UUID getId() {
        return id;
    }

    public RoleName getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Identity is the database key.
     *
     * <p>Implemented explicitly rather than via Lombok's {@code @Data}, which
     * would generate equality over every field — including any lazy association
     * — and trigger loading during a {@code Set} operation.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Role role)) {
            return false;
        }
        return id != null && id.equals(role.id);
    }

    /**
     * Constant hash, not {@code Objects.hash(id)}.
     *
     * <p>A JPA entity's identifier is null before persist and populated after.
     * Hashing on it means an entity added to a {@code HashSet} before flush
     * becomes unfindable in that set afterwards, because its bucket changed. A
     * type-constant hash is correct for entities and costs only a linear scan
     * within the rare collection that holds many roles — of which there are two.
     */
    @Override
    public int hashCode() {
        return Objects.hash(Role.class);
    }

    @Override
    public String toString() {
        return "Role{" + name + "}";
    }
}
