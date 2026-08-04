package com.careerpilot.auth.infrastructure;

import com.careerpilot.auth.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence access for {@link PasswordResetToken}.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /**
     * Looks a reset token up by hash, used or not.
     *
     * <p>Usability is decided by the domain object rather than filtered here, so
     * that "already used" and "never existed" remain distinguishable in logs.
     * Both return the same response to the client; only the log tells them
     * apart, and that difference matters when investigating a report of a link
     * that "didn't work".
     *
     * @param tokenHash SHA-256 hex of the presented token
     * @return the token record if one exists
     */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Invalidates every outstanding reset token for a user.
     *
     * <p>Called when a new reset is requested and when one is redeemed. Without
     * it, requesting three resets would leave three live tokens, so an attacker
     * who intercepted the first could still use it after the user completed a
     * reset with the third.
     *
     * @param userId the user
     * @param usedAt invalidation timestamp
     * @return number of tokens invalidated
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PasswordResetToken t
               SET t.usedAt = :usedAt
             WHERE t.user.id = :userId
               AND t.usedAt IS NULL
            """)
    int invalidateAllForUser(@Param("userId") UUID userId, @Param("usedAt") Instant usedAt);

    /**
     * Deletes tokens that expired before the given cut-off.
     *
     * @param cutoff delete tokens that expired before this instant
     * @return number of rows removed
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
