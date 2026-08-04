package com.careerpilot.auth.infrastructure;

import com.careerpilot.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence access for {@link User}.
 *
 * <p>Note what is <em>absent</em>: there is no {@code findByEmail(String)}.
 * Every lookup goes through {@link #findActiveByEmail(String)}, which lowercases
 * in the query so it matches the {@code LOWER(email)} unique index and excludes
 * soft-deleted rows. A convenience method that did neither would eventually be
 * used by someone in a hurry, and would let a deleted account authenticate.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds a non-deleted user by email, case-insensitively.
     *
     * <p>{@code LOWER(u.email)} is not merely for correctness — it is what lets
     * PostgreSQL use {@code ux_users_email_lower}. A query comparing the raw
     * column could not use that index and would sequentially scan the users
     * table on every single login.
     *
     * @param email the address, any case
     * @return the user if one exists and is not soft-deleted
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(:email) AND u.deletedAt IS NULL")
    Optional<User> findActiveByEmail(@Param("email") String email);

    /**
     * Whether an address is already registered.
     *
     * <p>Includes soft-deleted rows deliberately. The unique index covers every
     * row regardless of {@code deleted_at}, so ignoring deleted accounts here
     * would let registration pass its own check and then fail on a constraint
     * violation — surfacing as a 500 rather than a clean 409.
     *
     * @param email the address, any case
     * @return {@code true} if the address is taken
     */
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE LOWER(u.email) = LOWER(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    /**
     * Finds a non-deleted user by identifier.
     *
     * @param id the user identifier
     * @return the user if present and not soft-deleted
     */
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<User> findActiveById(@Param("id") UUID id);
}
