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
 * A refresh token, stored only as a SHA-256 hash.
 *
 * <p><strong>The token itself is never persisted.</strong> It is generated,
 * returned to the client once, and immediately forgotten by the server. What is
 * stored is its hash, and verification hashes the presented value to look it up.
 * The consequence is that a stolen database backup contains no usable
 * credentials — with plaintext storage it would contain a complete set of
 * account takeovers.
 *
 * <p><strong>{@code familyId} is what makes theft detectable.</strong> Every
 * token descended from a single login shares a family. Refreshing revokes the
 * presented token and issues a successor in the same family. Because each token
 * is single-use, a <em>revoked</em> token being presented has exactly one
 * plausible explanation: it was captured and replayed. The response is to revoke
 * the entire family — see {@code TokenService}.
 *
 * <p>Without the family, the best available response is rejecting the one
 * replayed token, which does nothing about the attacker's freshly-rotated one.
 * The family turns a detectable event into an actionable one.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Owning user.
     *
     * <p>{@code LAZY} here, unlike {@code User.roles}. Refresh handling needs
     * the user's identifier, which the foreign key already supplies without
     * loading the row — so an eager fetch would issue a join on every refresh
     * for data usually not read.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. Not for application use. */
    protected RefreshToken() {
    }

    /**
     * Creates a token in a given rotation family.
     *
     * @param user      owning user
     * @param tokenHash SHA-256 hex of the raw token
     * @param familyId  rotation lineage; a fresh UUID for a new login, or the
     *                  predecessor's family when rotating
     * @param expiresAt absolute expiry
     * @param userAgent client user agent, for the "active sessions" view
     * @param ipAddress client address, for the same
     */
    public RefreshToken(User user,
                        String tokenHash,
                        UUID familyId,
                        Instant expiresAt,
                        String userAgent,
                        String ipAddress) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
        this.userAgent = truncate(userAgent, 300);
        this.ipAddress = truncate(ipAddress, 45);
    }

    /**
     * Revokes this token, optionally recording its successor.
     *
     * <p>Idempotent — the first revocation timestamp is kept. Overwriting it
     * would destroy the evidence of when a compromise was detected, which is
     * the one thing an incident investigation needs from this row.
     *
     * @param replacementId id of the token issued in its place, or {@code null}
     */
    public void revoke(UUID replacementId) {
        if (this.revokedAt == null) {
            this.revokedAt = Instant.now();
        }
        if (replacementId != null) {
            this.replacedById = replacementId;
        }
    }

    /**
     * @return whether this token has been revoked
     */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * @return whether this token is past its expiry
     */
    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    /**
     * @return whether the token may still be exchanged for a new pair
     */
    public boolean isUsable() {
        return !isRevoked() && !isExpired();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    // --- accessors ---------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedById() {
        return replacedById;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RefreshToken token)) {
            return false;
        }
        return id != null && id.equals(token.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(RefreshToken.class);
    }

    /**
     * Diagnostic representation.
     *
     * <p>Never includes {@code tokenHash}. Even the hash is sensitive: it is the
     * exact value the lookup compares against, so an attacker who obtains it
     * from a log can forge a database row. Logs are not a safe place for it.
     */
    @Override
    public String toString() {
        return "RefreshToken{id=" + id + ", familyId=" + familyId + ", revoked=" + isRevoked() + "}";
    }
}
