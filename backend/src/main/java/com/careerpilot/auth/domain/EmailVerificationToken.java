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
 * A single-use email verification token, stored as a SHA-256 hash.
 *
 * <p>Structurally identical to {@link PasswordResetToken} and deliberately a
 * distinct type — see that class for why sharing a table would be a privilege
 * confusion rather than a tidy abstraction.
 *
 * <p>Longer-lived than a reset token (24 hours rather than one). The threat
 * differs: a reset token grants account takeover and should exist for as short
 * a window as possible, whereas a verification token only confirms that the
 * address receives mail. The cost of a short verification window is real —
 * users open mail the next morning — and the security benefit of shortening it
 * is small.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken {

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
    protected EmailVerificationToken() {
    }

    /**
     * @param user      the account whose address is being verified
     * @param tokenHash SHA-256 hex of the raw token
     * @param expiresAt absolute expiry; typically 24 hours
     */
    public EmailVerificationToken(User user, String tokenHash, Instant expiresAt) {
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
        if (!(other instanceof EmailVerificationToken token)) {
            return false;
        }
        return id != null && id.equals(token.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(EmailVerificationToken.class);
    }

    @Override
    public String toString() {
        return "EmailVerificationToken{id=" + id + ", used=" + (usedAt != null) + "}";
    }
}
