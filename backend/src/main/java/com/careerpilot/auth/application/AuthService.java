package com.careerpilot.auth.application;

import com.careerpilot.auth.application.dto.TokenResponse;
import com.careerpilot.auth.application.dto.UserResponse;
import com.careerpilot.auth.application.port.EmailSender;
import com.careerpilot.auth.domain.EmailVerificationToken;
import com.careerpilot.auth.domain.PasswordResetToken;
import com.careerpilot.auth.domain.Role;
import com.careerpilot.auth.domain.RoleName;
import com.careerpilot.auth.domain.User;
import com.careerpilot.auth.infrastructure.EmailVerificationTokenRepository;
import com.careerpilot.auth.infrastructure.JwtTokenProvider;
import com.careerpilot.auth.infrastructure.PasswordResetTokenRepository;
import com.careerpilot.auth.infrastructure.RoleRepository;
import com.careerpilot.auth.infrastructure.UserRepository;
import com.careerpilot.common.exception.ApiException;
import com.careerpilot.common.exception.ErrorCode;
import com.careerpilot.config.properties.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Registration, authentication, email verification, and password reset.
 *
 * <p>Several methods here behave in ways that look unhelpful in isolation —
 * returning success for a nonexistent email, deliberately wasting CPU on a
 * failed login. Each is a countermeasure against a specific attack, and each is
 * explained at its call site so a future maintainer does not "fix" it.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * A valid BCrypt hash of an arbitrary string, used to burn CPU when a login
     * is attempted against an address that does not exist. See
     * {@link #authenticate}.
     */
    private static final String DUMMY_BCRYPT_HASH =
            "$2a$12$C6UzMDM.H6dfI/f/IKcEe.3Ug3BdOZ.4Gd0lPQ1kIYxDS6MZ8Cvfy";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailVerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final TokenService tokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final AuthProperties authProperties;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       EmailVerificationTokenRepository verificationTokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       TokenService tokenService,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder,
                       EmailSender emailSender,
                       AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.tokenService = tokenService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.authProperties = authProperties;
    }

    // =======================================================================
    // Registration
    // =======================================================================

    /**
     * Registers a new account and dispatches a verification email.
     *
     * <p>Unlike {@link #requestPasswordReset}, this <em>does</em> report a
     * duplicate address with a 409. That asymmetry is deliberate: registration
     * cannot hide the fact that an address is taken — the user must be told why
     * their signup failed, and any design that concealed it would simply move
     * the disclosure to a confusing dead end. Password reset has no such
     * constraint, so it conceals.
     *
     * @param email    desired address
     * @param password plaintext password, hashed immediately and never stored
     * @param fullName display name
     * @return the public view of the created account
     * @throws ApiException if the address is already registered
     */
    @Transactional
    public UserResponse register(String email, String password, String fullName) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            log.info("Registration rejected: address already registered");
            throw new ApiException(ErrorCode.CONFLICT, "An account with this email already exists");
        }

        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseThrow(() -> new IllegalStateException(
                        "ROLE_USER is missing. Migration V2 seeds it; the database is inconsistent."));

        User user = new User(email, passwordEncoder.encode(password), fullName);
        user.addRole(userRole);
        userRepository.save(user);

        issueVerificationEmail(user);

        log.info("Registered new user {}", user.getId());
        return UserResponse.from(user);
    }

    // =======================================================================
    // Login
    // =======================================================================

    /**
     * Authenticates a user and issues a token pair.
     *
     * <p><strong>The dummy hash is a timing-attack countermeasure, not dead
     * code.</strong> BCrypt at strength 12 takes roughly 100 ms. If a login for
     * an unknown address returned immediately while a wrong password took 100 ms,
     * response time alone would reveal which addresses are registered — a user
     * enumeration oracle that needs no error message to exploit. Running the
     * encoder against a fixed hash when no user is found makes both paths cost
     * the same.
     *
     * <p>All three failure modes — unknown address, wrong password, and the
     * dummy comparison — return the identical 401 with the identical message,
     * for the same reason.
     *
     * @param email     submitted address
     * @param password  submitted password
     * @param userAgent client user agent, recorded against the session
     * @param ipAddress client address, recorded against the session
     * @return the new token pair, with the user embedded
     * @throws ApiException on any authentication failure
     */
    @Transactional
    public TokenResponse authenticate(String email,
                                      String password,
                                      String userAgent,
                                      String ipAddress) {

        Optional<User> maybeUser = userRepository.findActiveByEmail(email);

        if (maybeUser.isEmpty()) {
            // Deliberate work. Do not "optimise" this away.
            passwordEncoder.matches(password, DUMMY_BCRYPT_HASH);
            log.info("Login failed: no account for the submitted address");
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid email or password");
        }

        User user = maybeUser.get();

        // Checked before the password. A locked account should not have its
        // password verified at all — otherwise lockout does not actually reduce
        // the rate at which an attacker can test candidates.
        if (user.isLocked()) {
            log.warn("Login refused: account {} is locked until {}", user.getId(), user.getLockedUntil());
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED,
                    "Too many failed attempts. Please try again later.");
        }

        if (!user.canAuthenticate()) {
            log.warn("Login refused: account {} has status {}", user.getId(), user.getStatus());
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid email or password");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.recordFailedLogin(
                    authProperties.maxFailedLoginAttempts(),
                    authProperties.lockoutDuration());
            log.info("Login failed: incorrect password for account {}", user.getId());
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid email or password");
        }

        // Checked only after the password is verified. Announcing "this account
        // exists but is unverified" to someone who has not proven they know the
        // password would leak account existence.
        if (authProperties.requireEmailVerification() && !user.isEmailVerified()) {
            log.info("Login refused: account {} has not verified its email", user.getId());
            throw new ApiException(ErrorCode.EMAIL_NOT_VERIFIED,
                    "Please confirm your email address before signing in");
        }

        user.recordSuccessfulLogin();

        String accessToken = jwtTokenProvider.createAccessToken(user);
        String refreshToken = tokenService.issueNewFamily(user, userAgent, ipAddress);

        log.info("Login succeeded for account {}", user.getId());
        return TokenResponse.of(accessToken, refreshToken,
                jwtTokenProvider.getAccessTokenTtlSeconds(), UserResponse.from(user));
    }

    /**
     * Exchanges a refresh token for a new token pair.
     *
     * @param refreshToken the token as presented
     * @param userAgent    client user agent
     * @param ipAddress    client address
     * @return the new token pair, with the user embedded
     * @throws ApiException if the refresh token is unknown, revoked, or expired
     */
    @Transactional
    public TokenResponse refresh(String refreshToken, String userAgent, String ipAddress) {
        TokenService.RotationResult rotation = tokenService.rotate(refreshToken, userAgent, ipAddress);

        String accessToken = jwtTokenProvider.createAccessToken(rotation.user());

        return TokenResponse.of(accessToken, rotation.rawToken(),
                jwtTokenProvider.getAccessTokenTtlSeconds(),
                UserResponse.from(rotation.user()));
    }

    /**
     * Ends a single session.
     *
     * @param refreshToken the session's refresh token
     */
    @Transactional
    public void logout(String refreshToken) {
        tokenService.revoke(refreshToken);
    }

    /**
     * Ends every session belonging to a user.
     *
     * @param userId the user
     */
    @Transactional
    public void logoutAll(UUID userId) {
        tokenService.revokeAllForUser(userId);
    }

    // =======================================================================
    // Email verification
    // =======================================================================

    /**
     * Redeems an email verification token.
     *
     * @param rawToken the token from the emailed link
     * @throws ApiException if the token is unknown, already used, or expired
     */
    @Transactional
    public void verifyEmail(String rawToken) {
        String tokenHash = jwtTokenProvider.hashRefreshToken(rawToken);

        EmailVerificationToken token = verificationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(ErrorCode.MALFORMED_REQUEST,
                        "This verification link is not valid"));

        if (!token.isUsable()) {
            // One response for both "already used" and "expired". A user who
            // clicks a link twice and one whose link expired both need the same
            // next step: request a new one.
            log.info("Verification refused: token already used or expired");
            throw new ApiException(ErrorCode.MALFORMED_REQUEST,
                    "This verification link has expired or has already been used");
        }

        token.markUsed();
        token.getUser().verifyEmail();

        log.info("Verified email for account {}", token.getUser().getId());
    }

    /**
     * Re-sends a verification email.
     *
     * <p>Always completes without error, whether or not the address exists or is
     * already verified — this endpoint would otherwise be a cleaner enumeration
     * oracle than login, since it needs no password guess at all.
     *
     * @param email the address to re-send to
     */
    @Transactional
    public void resendVerification(String email) {
        userRepository.findActiveByEmail(email).ifPresent(user -> {
            if (user.isEmailVerified()) {
                log.debug("Resend skipped: address already verified");
                return;
            }
            verificationTokenRepository.invalidateAllForUser(user.getId(), Instant.now());
            issueVerificationEmail(user);
        });
    }

    // =======================================================================
    // Password reset
    // =======================================================================

    /**
     * Starts a password reset.
     *
     * <p><strong>Always succeeds, even for an address that does not exist.</strong>
     * This is the countermeasure the endpoint exists to carry: returning 404 for
     * an unknown address would turn it into a free, unauthenticated user
     * enumeration API — no password guessing, no rate-limit pressure, just a
     * yes/no answer per address. The response is identical either way; only the
     * log records which happened.
     *
     * @param email the address a reset was requested for
     */
    @Transactional
    public void requestPasswordReset(String email) {
        Optional<User> maybeUser = userRepository.findActiveByEmail(email);

        if (maybeUser.isEmpty()) {
            log.info("Password reset requested for an address with no account");
            return;
        }

        User user = maybeUser.get();

        // Invalidate outstanding tokens first. Without this, requesting three
        // resets leaves three live links, so an intercepted early one still
        // works after the user has completed a reset with a later one.
        passwordResetTokenRepository.invalidateAllForUser(user.getId(), Instant.now());

        String rawToken = jwtTokenProvider.generateRefreshToken();
        String tokenHash = jwtTokenProvider.hashRefreshToken(rawToken);
        Instant expiresAt = Instant.now().plus(authProperties.passwordResetTokenTtl());

        passwordResetTokenRepository.save(new PasswordResetToken(user, tokenHash, expiresAt));

        String resetUrl = authProperties.frontendBaseUrl()
                + "/reset-password?token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

        emailSender.sendPasswordResetEmail(user.getEmail(), user.getFullName(), resetUrl);

        log.info("Password reset issued for account {}", user.getId());
    }

    /**
     * Completes a password reset.
     *
     * <p>Three things happen together, and all three are necessary:
     * the password changes, the token is consumed, and <strong>every existing
     * session is revoked</strong>. Skipping the last would mean an attacker who
     * had already stolen a refresh token keeps their access after the victim
     * resets the password — which is precisely the scenario a reset is usually
     * performed to end.
     *
     * @param rawToken    the token from the emailed link
     * @param newPassword the new plaintext password
     * @throws ApiException if the token is unknown, already used, or expired
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String tokenHash = jwtTokenProvider.hashRefreshToken(rawToken);

        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(ErrorCode.MALFORMED_REQUEST,
                        "This reset link is not valid"));

        if (!token.isUsable()) {
            log.info("Password reset refused: token already used or expired");
            throw new ApiException(ErrorCode.MALFORMED_REQUEST,
                    "This reset link has expired or has already been used");
        }

        User user = token.getUser();

        token.markUsed();
        user.changePassword(passwordEncoder.encode(newPassword));
        tokenService.revokeAllForUser(user.getId());

        emailSender.sendPasswordChangedNotification(user.getEmail(), user.getFullName());

        log.info("Password reset completed for account {}", user.getId());
    }

    /**
     * Changes the password of an authenticated user.
     *
     * <p>Requires the current password even though the caller is already
     * authenticated. Without it, anyone with a few minutes at an unlocked laptop
     * — or a stolen access token — could take permanent ownership of the
     * account. Re-proving knowledge of the password is what makes that a
     * temporary compromise rather than a total one.
     *
     * @param userId          the authenticated user
     * @param currentPassword the existing password
     * @param newPassword     the replacement
     * @throws ApiException if the current password is wrong
     */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Account not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            log.warn("Password change refused: current password incorrect for account {}", userId);
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Current password is incorrect");
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        tokenService.revokeAllForUser(userId);

        emailSender.sendPasswordChangedNotification(user.getEmail(), user.getFullName());

        log.info("Password changed for account {}", userId);
    }

    // =======================================================================
    // Helpers
    // =======================================================================

    private void issueVerificationEmail(User user) {
        String rawToken = jwtTokenProvider.generateRefreshToken();
        String tokenHash = jwtTokenProvider.hashRefreshToken(rawToken);
        Instant expiresAt = Instant.now().plus(authProperties.verificationTokenTtl());

        verificationTokenRepository.save(new EmailVerificationToken(user, tokenHash, expiresAt));

        String verificationUrl = authProperties.frontendBaseUrl()
                + "/verify-email?token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

        emailSender.sendVerificationEmail(user.getEmail(), user.getFullName(), verificationUrl);
    }

    /**
     * Returns the public view of an authenticated user.
     *
     * <p>Backs {@code GET /auth/me}. Reads from the database rather than
     * reconstructing from the JWT's claims: the token is a snapshot taken up to
     * fifteen minutes ago, and this endpoint exists precisely so a client can
     * ask what is true <em>now</em>, after a verification or a role change.
     *
     * @param userId the authenticated user
     * @return the public view
     * @throws ApiException if the account no longer exists
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        return userRepository.findActiveById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "Account not found"));
    }
}
