package com.careerpilot.auth.domain;

/**
 * The set of roles the platform recognises.
 *
 * <p>Values carry the {@code ROLE_} prefix because Spring Security's
 * {@code hasRole("ADMIN")} check prepends it internally, while
 * {@code hasAuthority(...)} does not. Storing the prefixed form and using
 * authorities consistently avoids the classic failure where {@code hasRole} and
 * {@code hasAuthority} silently disagree and an endpoint is open to everyone or
 * to no one.
 *
 * <p>An enum rather than free strings: a typo in {@code "ROLE_ADMIM"} would
 * otherwise compile, persist, and simply never match — granting nothing, with no
 * error anywhere.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public enum RoleName {

    /** Standard authenticated user. Granted at registration. */
    ROLE_USER,

    /** Platform administrator. Granted manually; never self-assignable. */
    ROLE_ADMIN
}
