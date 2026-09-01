package com.careerpilot.auth.infrastructure;

import com.careerpilot.auth.domain.Role;
import com.careerpilot.auth.domain.User;
import com.careerpilot.common.security.AuthenticatedUser;
import com.careerpilot.config.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Creates and verifies JWTs, and generates the opaque refresh tokens.
 *
 * <p>The single place in the codebase that touches JJWT. Everything else works
 * with {@link AuthenticatedUser} and plain strings, so a change of JWT library
 * or signing algorithm is confined to this file.
 *
 * <p><strong>Why HS256 rather than RS256.</strong> Asymmetric signing exists so
 * that many services can verify tokens without holding the power to mint them.
 * This is one service that both issues and verifies, so RS256 would add key-pair
 * management and rotation machinery to solve a problem we do not have. If a
 * second service ever needs to verify these tokens, that is the moment to
 * switch, and this class is the only thing that would change.
 *
 * <p><strong>Refresh tokens are deliberately not JWTs.</strong> A JWT is
 * self-validating, which is precisely wrong for a refresh token: revocation must
 * be authoritative and immediate, and a self-validating token cannot be revoked
 * without a server-side blocklist. At that point the statelessness that
 * justified using a JWT is gone. Refresh tokens here are opaque random strings
 * whose only meaning is a database row, which makes revocation a single UPDATE.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    /** Claim carrying the user's granted roles. */
    private static final String CLAIM_ROLES = "roles";

    /** Claim carrying the user's email, so the principal needs no lookup. */
    private static final String CLAIM_EMAIL = "email";

    /** 32 bytes = 256 bits of entropy. Guessing one is not a viable attack. */
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;

    /**
     * @implNote Explicitly {@code @Autowired} because this class has two
     *     constructors. Spring only infers the injection point when there is
     *     exactly one, and otherwise fails at startup with "No default
     *     constructor found" — an error that names neither constructor.
     */
    @Autowired
    public JwtTokenProvider(JwtProperties jwtProperties) {
        this(jwtProperties, Clock.systemUTC());
    }

    /**
     * Package-private, for tests that need to issue a token as of a different
     * moment.
     *
     * <p>The alternative — constructing a provider with a negative token
     * lifetime to force an expired token — stopped working once
     * {@link JwtProperties} began rejecting non-positive durations, and was
     * always a strange thing to do: it tested the behaviour of a configuration
     * that must never exist rather than the behaviour of an expired token.
     *
     * @param clock the source of "now" for issuing tokens
     */
    JwtTokenProvider(JwtProperties jwtProperties, Clock clock) {
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.signingKey = Keys.hmacShaKeyFor(
                jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Issues a signed access token for a user.
     *
     * <p>Roles are embedded so authorisation needs no database read. See
     * {@link AuthenticatedUser} for the staleness this implies and why it is
     * acceptable.
     *
     * @param user the authenticated user
     * @return a compact, signed JWT
     */
    public String createAccessToken(User user) {
        Instant now = clock.instant();
        Instant expiry = now.plus(jwtProperties.accessTokenTtl());

        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .map(Enum::name)
                .collect(Collectors.toSet());

        return Jwts.builder()
                // The subject is the user id, not the email. Email addresses can
                // change; the identifier cannot. A token whose subject is an
                // email would silently point at the wrong account after a change.
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLES, roleNames)
                .issuer(jwtProperties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .id(UUID.randomUUID().toString())
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generates an opaque refresh token.
     *
     * <p>{@link SecureRandom}, not {@code Math.random()} or {@code Random}. The
     * latter are seeded predictably and produce a sequence an attacker who has
     * seen a few outputs can continue, which for a credential means forging
     * other users' tokens.
     *
     * @return a URL-safe random token; returned to the client once and never stored
     */
    public String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Hashes a token for storage or lookup.
     *
     * <p>SHA-256, deliberately <em>not</em> BCrypt. BCrypt is correct for
     * passwords because it is slow, which is what defeats offline brute force
     * against low-entropy human-chosen secrets. A refresh token is 256 bits of
     * cryptographic randomness, so brute force is not on the table. More
     * decisively, BCrypt's per-token salt would make lookup impossible: finding
     * a row requires hashing the presented value deterministically, and with
     * BCrypt we would have to load and compare every stored hash on every
     * refresh.
     *
     * @param rawToken the token as presented by the client
     * @return lowercase hex SHA-256, 64 characters
     */
    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated for every conforming JVM. If it is genuinely
            // missing, the platform is broken in a way no fallback could
            // sensibly handle.
            throw new IllegalStateException("SHA-256 unavailable on this JVM", e);
        }
    }

    /**
     * Verifies an access token and rebuilds the principal from its claims.
     *
     * <p>Returns {@code null} rather than throwing on any failure. The filter
     * that calls this simply leaves the {@code SecurityContext} empty, and the
     * request is then rejected by the security chain with a consistent 401. A
     * thrown exception here would need catching in the filter anyway, and the
     * distinction between "expired", "malformed", and "wrong signature" is
     * useful in a log but must never reach the client: telling an attacker which
     * part of their forgery failed is free assistance.
     *
     * @param token the raw bearer token
     * @return the principal, or {@code null} if the token is not valid
     */
    public AuthenticatedUser parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(jwtProperties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            UUID userId = UUID.fromString(claims.getSubject());
            String email = claims.get(CLAIM_EMAIL, String.class);

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get(CLAIM_ROLES, List.class);

            return new AuthenticatedUser(userId, email, Set.copyOf(roles));

        } catch (ExpiredJwtException e) {
            log.debug("Rejected expired access token");
            return null;
        } catch (JwtException | IllegalArgumentException e) {
            // Covers bad signature, malformed structure, wrong issuer, and a
            // subject that is not a UUID. Logged at DEBUG because a public API
            // receives a steady background rate of these from scanners, and
            // logging each at WARN would bury genuine warnings.
            log.debug("Rejected invalid access token: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * @return the configured access-token lifetime in seconds, for the
     *         {@code expiresIn} field clients use to schedule a refresh
     */
    public long getAccessTokenTtlSeconds() {
        return jwtProperties.accessTokenTtl().toSeconds();
    }

    /**
     * @return the configured refresh-token lifetime
     */
    public Duration getRefreshTokenTtl() {
        return jwtProperties.refreshTokenTtl();
    }
}
