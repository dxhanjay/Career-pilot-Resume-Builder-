package com.careerpilot.auth.domain;

import com.careerpilot.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A platform user.
 *
 * <p><strong>This entity carries behaviour, not just fields.</strong> Methods
 * like {@link #recordFailedLogin(int, java.time.Duration)} and
 * {@link #verifyEmail()} live here rather than in {@code AuthService} because
 * the rules they encode — when an account locks, what verification does to
 * status — are properties of what a user <em>is</em>. Spread across a service,
 * the same rule gets reimplemented slightly differently the second time it is
 * needed, and the two versions diverge.
 *
 * <p>The setters that do exist are deliberately narrow. There is no
 * {@code setStatus}, no {@code setFailedLoginAttempts}, and no
 * {@code setEmailVerifiedAt}: those fields change only through the methods that
 * enforce the rules around them.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    /**
     * Time-ordered UUID rather than random.
     *
     * <p>A v4 UUID scatters inserts uniformly across the primary-key B-tree,
     * dirtying pages throughout the index. A time-ordered value keeps new rows
     * near the right edge, which is materially better for both insert
     * throughput and cache locality.
     *
     * <p>Accuracy note: Hibernate's {@code Style.TIME} produces a time-ordered
     * UUID of its own construction — it is not literally RFC 9562 version 7.
     * The property we actually want is monotonic ordering, and that it does
     * provide.
     */
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.PENDING;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "failed_login_attempts", nullable = false)
    private short failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "ai_credits_used_month", nullable = false)
    private int aiCreditsUsedMonth;

    @Column(name = "ai_credits_reset_at", nullable = false)
    private Instant aiCreditsResetAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * Roles, eagerly fetched.
     *
     * <p>{@code EAGER} is usually the wrong default, and is correct here for a
     * specific reason: roles are read on essentially every authentication, the
     * set has at most two elements, and a lazy collection would throw
     * {@code LazyInitializationException} the moment Spring Security touches it
     * outside a transaction — which is exactly where it does touch it, given
     * {@code open-in-view} is disabled.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    /** Required by JPA. Not for application use. */
    protected User() {
    }

    /**
     * Registers a new user in {@link UserStatus#PENDING}.
     *
     * <p>The email is normalised to lowercase here, at the single point of
     * construction, rather than being left to each caller. The unique index is
     * on {@code LOWER(email)}, so a caller that forgot would produce a row that
     * violates the index in a way that only surfaces on the second registration.
     *
     * @param email        the address, normalised to lowercase
     * @param passwordHash a BCrypt hash — never a plaintext password
     * @param fullName     display name
     */
    public User(String email, String passwordHash, String fullName) {
        this.email = normaliseEmail(email);
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.status = UserStatus.PENDING;
        this.failedLoginAttempts = 0;
        this.aiCreditsUsedMonth = 0;
        this.aiCreditsResetAt = Instant.now().plusSeconds(30L * 24 * 60 * 60);
    }

    // --- behaviour ---------------------------------------------------------

    /**
     * Marks the email address verified and promotes the account to
     * {@link UserStatus#ACTIVE}.
     *
     * <p>Idempotent: verifying twice is a no-op rather than an error. Users do
     * click emailed links twice, and failing the second click would present an
     * error for an action that succeeded.
     */
    public void verifyEmail() {
        if (this.emailVerifiedAt == null) {
            this.emailVerifiedAt = Instant.now();
        }
        if (this.status == UserStatus.PENDING) {
            this.status = UserStatus.ACTIVE;
        }
    }

    /**
     * @return whether the email address has been verified
     */
    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    /**
     * Records a successful authentication, clearing any accumulated failures.
     *
     * <p>Resetting the counter on success is what keeps lockout targeted at
     * attacks rather than at users. Without it, four typos spread across three
     * months would eventually lock an account that was never under attack.
     */
    public void recordSuccessfulLogin() {
        this.lastLoginAt = Instant.now();
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    /**
     * Records a failed authentication and locks the account once the threshold
     * is reached.
     *
     * <p>A temporary lock rather than a permanent one. A permanent lock turns
     * the login endpoint into a denial-of-service tool: anyone who knows a
     * victim's email address can lock them out indefinitely by submitting wrong
     * passwords. A timed lock slows an attacker to a crawl while the legitimate
     * user is inconvenienced for minutes rather than until they contact support.
     *
     * @param maxAttempts   failures tolerated before locking
     * @param lockDuration  how long the lock lasts
     */
    public void recordFailedLogin(int maxAttempts, java.time.Duration lockDuration) {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= maxAttempts) {
            this.lockedUntil = Instant.now().plus(lockDuration);
            this.failedLoginAttempts = 0;
        }
    }

    /**
     * @return whether the account is currently within a lock window
     */
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    /**
     * Replaces the password hash and clears any lock.
     *
     * <p>Clearing the lock is intentional: someone completing a password reset
     * has proven control of the email address, which is stronger evidence than
     * the failed attempts that caused the lock. Leaving them locked out after a
     * successful reset would be a confusing dead end.
     *
     * @param newPasswordHash a BCrypt hash
     */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    /**
     * Grants a role, if not already held.
     *
     * @param role the role to grant
     */
    public void addRole(Role role) {
        this.roles.add(role);
    }

    /**
     * Marks the account deleted, beginning the 30-day purge window
     * (NFR-PRIV-01).
     *
     * <p>Soft delete, so an accidental deletion can be reversed by support
     * within the window. The hard purge is a scheduled job, not this method.
     */
    public void markDeleted() {
        this.status = UserStatus.DELETED;
        this.deletedAt = Instant.now();
    }

    /**
     * Suspends the account. An administrator action, not a user one.
     *
     * <p>Deliberately does nothing to a deleted account: undeleting by way of
     * suspend-then-reactivate would be a silent restore path around the deletion
     * policy.
     */
    public void suspend() {
        if (status != UserStatus.DELETED) {
            this.status = UserStatus.SUSPENDED;
        }
    }

    /**
     * Lifts a suspension.
     *
     * <p>Returns the account to PENDING rather than ACTIVE when the email was
     * never verified, so reinstating someone cannot quietly hand them a
     * verification they never completed.
     */
    public void reactivate() {
        if (status == UserStatus.SUSPENDED) {
            this.status = isEmailVerified() ? UserStatus.ACTIVE : UserStatus.PENDING;
            this.failedLoginAttempts = 0;
            this.lockedUntil = null;
        }
    }

    /**
     * @return whether this account may attempt authentication at all
     */
    public boolean canAuthenticate() {
        return deletedAt == null && status.canAuthenticate();
    }

    private static String normaliseEmail(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    // --- accessors ---------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public short getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public int getAiCreditsUsedMonth() {
        return aiCreditsUsedMonth;
    }

    public Instant getAiCreditsResetAt() {
        return aiCreditsResetAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    /**
     * @return an unmodifiable view of granted roles
     */
    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof User user)) {
            return false;
        }
        return id != null && id.equals(user.id);
    }

    /** See {@link Role#hashCode()} for why this is type-constant. */
    @Override
    public int hashCode() {
        return Objects.hash(User.class);
    }

    /**
     * Diagnostic representation.
     *
     * <p>Contains the id and status only. It must never include the email
     * address or the password hash: {@code toString()} output ends up in log
     * lines and exception messages, and both of those leave the process.
     */
    @Override
    public String toString() {
        return "User{id=" + id + ", status=" + status + "}";
    }
}
