package com.careerpilot.auth.infrastructure;

import com.careerpilot.auth.domain.Role;
import com.careerpilot.auth.domain.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence access for {@link Role}.
 *
 * <p>Read-only in practice: roles are reference data seeded by migration V2.
 * Nothing in the application creates or modifies them.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface RoleRepository extends JpaRepository<Role, UUID> {

    /**
     * Looks up a role by its name.
     *
     * @param name the role
     * @return the role if it exists
     */
    Optional<Role> findByName(RoleName name);
}
