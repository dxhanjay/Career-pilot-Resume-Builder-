package com.careerpilot.auth.application;

import com.careerpilot.auth.domain.RefreshToken;
import com.careerpilot.auth.domain.User;
import com.careerpilot.auth.infrastructure.JwtTokenProvider;
import com.careerpilot.auth.infrastructure.RefreshTokenRepository;
import com.careerpilot.common.exception.ApiException;
import com.careerpilot.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues, rotates, and revokes refresh tokens.
 *
 * <p>This class implements the refresh-token rotation scheme with reuse
 * detection. It is the most security-sensitive code in the project, so the
 * reasoning is spelled out rather than assumed.
 *
 * <h2>The problem being solved</h2>
 *
 * <p>Refresh tokens are long-lived by design — that is what spares users from
 * logging in every fifteen minutes. Long life makes them attractive to steal,
 * and a stolen one is indistinguishable from a legitimate one: same string,
 * same owner, same everything. Theft is therefore not directly detectable.
 *
 * <h2>Rotation makes it detectable</h2>
 *
 * <p>Each refresh consumes the presented token and issues a successor. Tokens
 * are strictly single-use. That yields the property this whole design rests on:
 *
 * <blockquote>
 * If an <em>already-revoked</em> token is presented, someone is replaying a
 * token that was legitimately consumed. There is no benign explanation.
 * </blockquote>
 *
 * <p>Both attacker and victim hold tokens from the same family, and whichever
 * refreshes second presents a consumed one. We cannot tell which party that is —
 * so we revoke the entire family, ending both sessions. The victim logs in
 * again; the attacker is locked out and cannot continue without the password.
 *
 * <p>Logging out a legitimate user is a real cost, accepted because the
 * alternative — rejecting the replay while leaving the attacker's freshly
 * rotated token working — leaves the compromise running indefinitely.
 *
 * <h2>What this does not do</h2>
 *
 * <p>Detection is retrospective: it fires when the second party refreshes, not
 * at the moment of theft. Between theft and detection the attacker has access.
 * That window is bounded by how soon the legitimate client next refreshes, which
 * is why the access-token TTL is short — it forces frequent refreshes and so
 * shortens the window.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public TokenService(RefreshTokenRepository refreshTokenRepository,
                        JwtTokenProvider jwtTokenProvider) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Issues the first refresh token of a new session, starting a new family.
     *
     * @param user      the authenticated user
     * @param userAgent client user agent, for session listing
     * @param ipAddress client address, for session listing
     * @return the raw token — returned to the client once and never stored
     */
    @Transactional
    public String issueNewFamily(User user, String userAgent, String ipAddress) {
        return persistToken(user, UUID.randomUUID(), userAgent, ipAddress);
    }

    /**
     * Exchanges a refresh token for a new one, detecting replay.
     *
     * <p>The order of checks matters and is deliberate:
     *
     * <ol>
     *   <li><strong>Unknown hash</strong> — was never issued. Garbage, a typo,
     *       or a token already purged. Rejected, no family action, because there
     *       is no family to act on.</li>
     *   <li><strong>Known but revoked</strong> — the replay case. Revoke the
     *       whole family and reject.</li>
     *   <li><strong>Known and expired</strong> — ordinary lifecycle. Rejected
     *       without punishing the family; an expired token is not evidence of
     *       anything.</li>
     *   <li><strong>Valid</strong> — revoke it, issue a successor in the same
     *       family, link them for the audit trail.</li>
     * </ol>
     *
     * <p>Steps 2 and 3 must stay distinct. Treating an expired token as a replay
     * would log out any user who left a tab open over a weekend, which would
     * make the security control indistinguishable from a bug.
     *
     * @param rawToken  the token as presented
     * @param userAgent client user agent
     * @param ipAddress client address
     * @return the owning user and the new raw token
     * @throws ApiException if the token is unknown, revoked, or expired
     */
    @Transactional
    public RotationResult rotate(String rawToken, String userAgent, String ipAddress) {
        String tokenHash = jwtTokenProvider.hashRefreshToken(rawToken);

        Optional<RefreshToken> maybeToken = refreshTokenRepository.findByTokenHash(tokenHash);

        if (maybeToken.isEmpty()) {
            log.warn("Refresh attempted with a token that was never issued");
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid refresh token");
        }

        RefreshToken token = maybeToken.get();

        if (token.isRevoked()) {
            // The replay case. This is a security incident, not a validation
            // failure, and is logged at ERROR so it surfaces in alerting rather
            // than blending into the steady stream of expired-token warnings.
            int revokedCount = refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now());
            log.error("""
                            Refresh token reuse detected — revoked {} token(s) in family {}. \
                            A token was replayed after being consumed, which indicates theft. \
                            All sessions in this family have been terminated.""",
                    revokedCount, token.getFamilyId());
            throw new ApiException(ErrorCode.TOKEN_REUSE_DETECTED,
                    "Your session was ended for security reasons. Please sign in again.");
        }

        if (token.isExpired()) {
            log.debug("Refresh attempted with an expired token");
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Refresh token has expired");
        }

        User user = token.getUser();

        // Re-check the account on every refresh. This is the mechanism that
        // makes suspension effective: the access token cannot be renewed once
        // the account is no longer permitted to authenticate, so access ends
        // within one access-token lifetime.
        if (!user.canAuthenticate()) {
            refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
            log.warn("Refresh refused for an account that may no longer authenticate");
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Account is not active");
        }

        String newRawToken = persistToken(user, token.getFamilyId(), userAgent, ipAddress);

        // Revoke the old token only after its successor exists, so the
        // replaced_by link can be recorded. If anything fails in between, the
        // transaction rolls back and the client can retry with the same token.
        String newHash = jwtTokenProvider.hashRefreshToken(newRawToken);
        refreshTokenRepository.findByTokenHash(newHash)
                .ifPresent(successor -> token.revoke(successor.getId()));

        return new RotationResult(user, newRawToken);
    }

    /**
     * Revokes a single token — an ordinary logout.
     *
     * <p>Silently ignores an unknown token. Logout is idempotent from the user's
     * point of view, and reporting "that token doesn't exist" tells an
     * unauthenticated caller which tokens are real.
     *
     * @param rawToken the token to revoke
     */
    @Transactional
    public void revoke(String rawToken) {
        String tokenHash = jwtTokenProvider.hashRefreshToken(rawToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(token -> token.revoke(null));
    }

    /**
     * Revokes every active token for a user.
     *
     * <p>Backs "sign out everywhere", and is also called on password change and
     * account deletion. A password change that left old sessions alive would
     * defeat the most common reason people change passwords.
     *
     * @param userId the user
     * @return number of sessions ended
     */
    @Transactional
    public int revokeAllForUser(UUID userId) {
        int revoked = refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        log.info("Revoked {} active session(s)", revoked);
        return revoked;
    }

    private String persistToken(User user, UUID familyId, String userAgent, String ipAddress) {
        String rawToken = jwtTokenProvider.generateRefreshToken();
        String tokenHash = jwtTokenProvider.hashRefreshToken(rawToken);
        Instant expiresAt = Instant.now().plus(jwtTokenProvider.getRefreshTokenTtl());

        refreshTokenRepository.save(
                new RefreshToken(user, tokenHash, familyId, expiresAt, userAgent, ipAddress));

        return rawToken;
    }

    /**
     * The outcome of a successful rotation.
     *
     * @param user     the token's owner, already re-checked as able to authenticate
     * @param rawToken the new refresh token, to be returned to the client
     */
    public record RotationResult(User user, String rawToken) {
    }
}
