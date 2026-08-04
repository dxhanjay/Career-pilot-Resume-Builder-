package com.careerpilot.auth.domain;

/**
 * Lifecycle states for a user account.
 *
 * <p>Persisted as a string via {@code @Enumerated(EnumType.STRING)} and backed
 * by a {@code CHECK} constraint in the schema. Never {@code EnumType.ORDINAL} —
 * ordinal storage means inserting a new constant in the middle of this list
 * silently reinterprets every existing row, which is a data corruption that no
 * test catches because the numbers are all still valid.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public enum UserStatus {

    /** Registered but email not yet verified. May log in only if verification is not enforced. */
    PENDING,

    /** Verified and in good standing. */
    ACTIVE,

    /** Suspended by an administrator. Cannot authenticate. */
    SUSPENDED,

    /** Deletion requested; rows are purged within 30 days (NFR-PRIV-01). */
    DELETED;

    /**
     * Whether an account in this state is permitted to authenticate at all.
     *
     * <p>{@link #PENDING} is included because whether unverified users may log
     * in is a product decision, not a domain one — it is governed by
     * {@code app.auth.require-email-verification} and enforced separately in the
     * service. Excluding PENDING here would hard-code that policy into the
     * domain and make the configuration flag a lie.
     *
     * @return {@code true} if authentication may proceed for this status
     */
    public boolean canAuthenticate() {
        return this == ACTIVE || this == PENDING;
    }
}
