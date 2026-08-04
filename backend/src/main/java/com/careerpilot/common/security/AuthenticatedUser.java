package com.careerpilot.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The authenticated principal placed in the {@code SecurityContext}.
 *
 * <p>Reconstructed entirely from the JWT's claims — <strong>no database
 * lookup</strong>. That is the defining property of stateless authentication
 * and the reason the API scales horizontally without shared session storage:
 * any container can serve any request, because everything needed to authorise
 * it travels in the token.
 *
 * <p><strong>The cost, stated plainly.</strong> Because authorisation reads the
 * token rather than the database, a change made to an account is not visible
 * until the current access token expires. Suspend a user and they retain access
 * for up to 15 minutes; revoke a role and it stays effective for the same
 * window. That is an accepted trade — the access-token TTL is short precisely to
 * bound it. Where an action must take effect immediately, the correct mechanism
 * is revoking the refresh-token family (which prevents renewal) combined with
 * the short TTL, not a per-request database check that would negate the design.
 *
 * <p><strong>Why this lives in {@code common.security} rather than
 * {@code auth.infrastructure}.</strong> Controllers across every feature receive
 * it via {@code @AuthenticationPrincipal}, and an ArchUnit rule forbids the API
 * layer from importing infrastructure. Placing it in the shared kernel keeps
 * that rule honest instead of forcing an exception to it — a principal is a
 * cross-cutting concept, not an implementation detail of the auth module.
 *
 * <p>Carries the user identifier rather than only the email, so downstream
 * services can scope queries with {@code findByIdAndUserId(...)} without a
 * lookup to translate one into the other.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public class AuthenticatedUser implements UserDetails {

    private final UUID id;
    private final String email;
    private final List<GrantedAuthority> authorities;

    /**
     * @param id        the user identifier, from the token subject
     * @param email     the user's email address
     * @param roleNames granted role names, already {@code ROLE_}-prefixed
     */
    public AuthenticatedUser(UUID id, String email, Set<String> roleNames) {
        this.id = id;
        this.email = email;
        this.authorities = roleNames.stream()
                .map(name -> (GrantedAuthority) new SimpleGrantedAuthority(name))
                .toList();
    }

    /**
     * @return the authenticated user's identifier — the value every ownership
     *         check should be scoped by
     */
    public UUID getId() {
        return id;
    }

    /**
     * @return the authenticated user's email address
     */
    public String getEmail() {
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /**
     * Always {@code null}.
     *
     * <p>{@link UserDetails} demands this method, but no password ever reaches
     * this object. Returning the hash would place a credential in the
     * {@code SecurityContext} for the lifetime of the request, from which it
     * could reach a log line or a debugger snapshot for no benefit — nothing in
     * a JWT-authenticated request compares passwords.
     */
    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    /*
     * The four flags below are all true because their conditions are checked at
     * login, before a token is ever issued, and re-checked at refresh. Repeating
     * them here would require the database lookup this class exists to avoid.
     * A locked or suspended user simply cannot obtain a new token.
     */

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String toString() {
        return "AuthenticatedUser{id=" + id + "}";
    }
}
