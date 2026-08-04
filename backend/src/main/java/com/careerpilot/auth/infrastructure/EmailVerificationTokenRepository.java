package com.careerpilot.auth.infrastructure;

import com.careerpilot.auth.domain.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence access for {@link EmailVerificationToken}.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, UUID> {

    /**
     * Looks a verification token up by hash, used or not.
     *
     * @param tokenHash SHA-256 hex of the presented token
     * @return the token record if one exists
     */
    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /**
     * Invalidates every outstanding verification token for a user.
     *
     * <p>Called when a resend is requested, so that only the newest link works.
     * Otherwise a user who requests three resends and clicks the oldest link
     * gets an inconsistent experience for no reason.
     *
     * @param userId the user
     * @param usedAt invalidation timestamp
     * @return number of tokens invalidated
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EmailVerificationToken t
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
    @Query("DELETE FROM EmailVerificationToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
