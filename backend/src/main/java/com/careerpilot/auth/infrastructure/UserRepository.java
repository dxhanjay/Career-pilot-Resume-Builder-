package com.careerpilot.auth.infrastructure;

import com.careerpilot.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    /**
     * Admin user search (FR-ADM-01).
     *
     * <p>A blank term returns everyone, so the same query backs both the
     * unfiltered list and the search box and the two cannot drift apart.
     *
     * @param term matched against email and full name, case-insensitively
     */
    @Query("""
            SELECT u FROM User u
             WHERE u.deletedAt IS NULL
               AND (:term = ''
                    OR LOWER(u.email) LIKE LOWER(CONCAT('%', :term, '%'))
                    OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :term, '%')))
            """)
    Page<User> search(@Param("term") String term, Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NULL")
    long countActive();

    @Query("SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NULL AND u.status = :status")
    long countByStatus(@Param("status") com.careerpilot.auth.domain.UserStatus status);

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :since AND u.deletedAt IS NULL")
    long countCreatedSince(@Param("since") java.time.Instant since);
}
