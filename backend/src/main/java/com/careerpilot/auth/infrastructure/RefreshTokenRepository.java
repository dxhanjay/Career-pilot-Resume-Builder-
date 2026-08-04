package com.careerpilot.auth.infrastructure;

import com.careerpilot.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence access for {@link RefreshToken}.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Looks a token up by its hash.
     *
     * <p>Returns revoked and expired tokens as well as valid ones — that is
     * essential rather than sloppy. Reuse detection depends on being able to
     * distinguish "this token was never issued" from "this token was issued and
     * has already been revoked". A query filtered to usable tokens would collapse
     * both into an empty result, and the replay would look identical to a
     * garbage value.
     *
     * @param tokenHash SHA-256 hex of the presented token
     * @return the token record if one was ever issued with this hash
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revokes every unrevoked token in a rotation family.
     *
     * <p>Invoked when a revoked token is replayed, which indicates theft. A bulk
     * {@code UPDATE} rather than a load-and-modify loop because this runs during
     * a suspected compromise and must be atomic and immediate; iterating would
     * leave a window in which the attacker's newest token still works.
     *
     * @param familyId the compromised lineage
     * @param revokedAt revocation timestamp
     * @return number of tokens revoked
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :revokedAt
             WHERE t.familyId = :familyId
               AND t.revokedAt IS NULL
            """)
    int revokeFamily(@Param("familyId") UUID familyId, @Param("revokedAt") Instant revokedAt);

    /**
     * Revokes every active token belonging to a user.
     *
     * <p>Backs "log out of all sessions" and is also invoked on password change
     * and account deletion. Changing a password while old sessions stay alive
     * would defeat the main reason people change passwords.
     *
     * @param userId the user
     * @param revokedAt revocation timestamp
     * @return number of tokens revoked
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken t
               SET t.revokedAt = :revokedAt
             WHERE t.user.id = :userId
               AND t.revokedAt IS NULL
            """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);

    /**
     * Deletes tokens that expired before the given cut-off.
     *
     * <p>Called by the nightly purge. Retention is bounded because an
     * indefinitely growing token table slows every lookup on the login path, and
     * the rows have no value once expired.
     *
     * @param cutoff delete tokens that expired before this instant
     * @return number of rows removed
     */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
