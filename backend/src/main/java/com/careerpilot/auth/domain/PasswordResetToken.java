package com.careerpilot.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A single-use password reset token, stored as a SHA-256 hash.
 *
 * <p>Possessing this token is sufficient to take over an account, which makes
 * it exactly as sensitive as a password. Two properties follow from that and
 * are enforced here rather than by convention:
 *
 * <ul>
 *   <li><strong>Hashed at rest.</strong> Same reasoning as
 *       {@link RefreshToken} — a database dump must not be a set of account
 *       takeovers.</li>
 *   <li><strong>Single use.</strong> {@link #markUsed()} stamps
 *       {@code usedAt}, and {@link #isUsable()} refuses a second redemption.
 *       Without this, a reset link sitting in an inbox — or in the browser
 *       history of a shared machine — stays live until it expires.</li>
 * </ul>
 *
 * <p>Deliberately a separate class and table from
 * {@link EmailVerificationToken} despite the identical shape. Sharing one table
 * behind a discriminator column would create a code path where a verification
 * token could be redeemed at the reset endpoint — a privilege confusion the
 * compiler could not catch. Two types make it unrepresentable.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. Not for application use. */
    protected PasswordResetToken() {
    }

    /**
     * @param user      the account this token can reset
     * @param tokenHash SHA-256 hex of the raw token
     * @param expiresAt absolute expiry; short, typically one hour
     */
    public PasswordResetToken(User user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /** Marks the token consumed. Idempotent. */
    public void markUsed() {
        if (this.usedAt == null) {
            this.usedAt = Instant.now();
        }
    }

    /**
     * @return whether the token may still be redeemed
     */
    public boolean isUsable() {
        return usedAt == null && expiresAt.isAfter(Instant.now());
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PasswordResetToken token)) {
            return false;
        }
        return id != null && id.equals(token.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(PasswordResetToken.class);
    }

    @Override
    public String toString() {
        return "PasswordResetToken{id=" + id + ", used=" + (usedAt != null) + "}";
    }
}
