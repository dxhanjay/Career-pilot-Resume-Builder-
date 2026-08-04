package com.careerpilot.auth.application;

import com.careerpilot.auth.application.dto.TokenResponse;
import com.careerpilot.auth.application.dto.UserResponse;
import com.careerpilot.auth.application.port.EmailSender;
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
import com.careerpilot.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService}.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private EmailVerificationTokenRepository verificationTokenRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private TokenService tokenService;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailSender emailSender;

    private AuthService authService;

    private final AuthProperties authProperties = new AuthProperties(
            true,
            5,
            Duration.ofMinutes(15),
            Duration.ofHours(24),
            Duration.ofHours(1),
            "http://localhost:5173");

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, roleRepository, verificationTokenRepository,
                passwordResetTokenRepository, tokenService, jwtTokenProvider,
                passwordEncoder, emailSender, authProperties);

        lenient().when(jwtTokenProvider.generateRefreshToken()).thenReturn("raw-token");
        lenient().when(jwtTokenProvider.hashRefreshToken(anyString())).thenReturn("token-hash");
        lenient().when(jwtTokenProvider.getAccessTokenTtlSeconds()).thenReturn(900L);
    }

    @Nested
    @DisplayName("registration")
    class Registration {

        @Test
        @DisplayName("rejects a duplicate address with CONFLICT")
        void duplicateEmailRejected() {
            when(userRepository.existsByEmailIgnoreCase("aditi@example.com")).thenReturn(true);

            assertThatThrownBy(() ->
                    authService.register("aditi@example.com", "a-long-enough-password", "Aditi"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CONFLICT));

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("hashes the password and never stores it in the clear")
        void passwordIsHashed() {
            when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
            when(roleRepository.findByName(RoleName.ROLE_USER))
                    .thenReturn(Optional.of(TestDataFactory.userRole()));
            when(passwordEncoder.encode("a-long-enough-password")).thenReturn("$2a$12$hashed");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse result =
                    authService.register("aditi@example.com", "a-long-enough-password", "Aditi");

            verify(passwordEncoder).encode("a-long-enough-password");
            assertThat(result.email()).isEqualTo("aditi@example.com");
        }

        @Test
        @DisplayName("normalises the address to lowercase")
        void emailIsLowercased() {
            when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
            when(roleRepository.findByName(RoleName.ROLE_USER))
                    .thenReturn(Optional.of(TestDataFactory.userRole()));
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashed");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UserResponse result =
                    authService.register("Aditi@Example.COM", "a-long-enough-password", "Aditi");

            // The unique index is on LOWER(email). Storing mixed case would let
            // two "different" addresses resolve to the same account at login.
            assertThat(result.email()).isEqualTo("aditi@example.com");
        }

        @Test
        @DisplayName("sends a verification email")
        void verificationEmailSent() {
            when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
            when(roleRepository.findByName(RoleName.ROLE_USER))
                    .thenReturn(Optional.of(TestDataFactory.userRole()));
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashed");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            authService.register("aditi@example.com", "a-long-enough-password", "Aditi");

            verify(emailSender).sendVerificationEmail(eq("aditi@example.com"), eq("Aditi"), anyString());
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("runs the password encoder even when no account exists (timing defence)")
        void unknownAccountStillCostsAHash() {
            when(userRepository.findActiveByEmail("nobody@example.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    authService.authenticate("nobody@example.com", "guess", "agent", "1.2.3.4"))
                    .isInstanceOf(ApiException.class);

            // Without this call, an unknown address returns in ~2ms while a
            // wrong password takes ~100ms, and response time alone reveals which
            // addresses are registered. This assertion is the only thing
            // stopping someone deleting that line as dead code.
            verify(passwordEncoder).matches(eq("guess"), anyString());
        }

        @Test
        @DisplayName("returns the same message for unknown address and wrong password")
        void failuresAreIndistinguishable() {
            when(userRepository.findActiveByEmail("nobody@example.com")).thenReturn(Optional.empty());

            Throwable unknownAccount = catchThrowable(() ->
                    authService.authenticate("nobody@example.com", "guess", "a", "i"));

            User user = TestDataFactory.activeUser("aditi@example.com");
            when(userRepository.findActiveByEmail("aditi@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(eq("wrong"), anyString())).thenReturn(false);

            Throwable wrongPassword = catchThrowable(() ->
                    authService.authenticate("aditi@example.com", "wrong", "a", "i"));

            assertThat(unknownAccount).isInstanceOf(ApiException.class);
            assertThat(wrongPassword).isInstanceOf(ApiException.class);

            // Identical messages and identical status. Any difference between
            // these two responses is a user-enumeration oracle.
            assertThat(unknownAccount.getMessage()).isEqualTo(wrongPassword.getMessage());
            assertThat(((ApiException) unknownAccount).getErrorCode())
                    .isEqualTo(((ApiException) wrongPassword).getErrorCode());
        }

        @Test
        @DisplayName("checks the lock before verifying the password")
        void lockedAccountSkipsPasswordCheck() {
            User user = TestDataFactory.activeUser("locked@example.com");
            user.recordFailedLogin(1, Duration.ofMinutes(15));   // threshold 1 -> locks immediately

            when(userRepository.findActiveByEmail("locked@example.com")).thenReturn(Optional.of(user));

            assertThatThrownBy(() ->
                    authService.authenticate("locked@example.com", "whatever", "a", "i"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.ACCOUNT_LOCKED));

            // If a locked account still had its password verified, lockout would
            // not actually reduce the rate at which an attacker tests candidates.
            verify(passwordEncoder, never()).matches(eq("whatever"), anyString());
        }

        @Test
        @DisplayName("refuses an unverified account when verification is enforced")
        void unverifiedAccountRefused() {
            User pending = TestDataFactory.pendingUser("pending@example.com");
            when(userRepository.findActiveByEmail("pending@example.com")).thenReturn(Optional.of(pending));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

            assertThatThrownBy(() ->
                    authService.authenticate("pending@example.com", "correct-password", "a", "i"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));
        }

        @Test
        @DisplayName("issues a token pair on success and clears the failure counter")
        void successIssuesTokens() {
            User user = TestDataFactory.activeUser("aditi@example.com");
            user.recordFailedLogin(5, Duration.ofMinutes(15));   // one failure, not locked

            when(userRepository.findActiveByEmail("aditi@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
            when(jwtTokenProvider.createAccessToken(user)).thenReturn("access-token");
            when(tokenService.issueNewFamily(eq(user), anyString(), anyString()))
                    .thenReturn("refresh-token");

            TokenResponse tokens =
                    authService.authenticate("aditi@example.com", "correct-password", "agent", "1.2.3.4");

            assertThat(tokens.accessToken()).isEqualTo("access-token");
            assertThat(tokens.refreshToken()).isEqualTo("refresh-token");
            assertThat(tokens.tokenType()).isEqualTo("Bearer");
            assertThat(tokens.expiresIn()).isEqualTo(900L);

            // Resetting on success is what keeps lockout aimed at attacks rather
            // than at users who mistype occasionally over months.
            assertThat(user.getFailedLoginAttempts()).isZero();
            assertThat(user.getLastLoginAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("password reset")
    class PasswordReset {

        @Test
        @DisplayName("succeeds silently for an address with no account")
        void unknownAddressDoesNotRevealItself() {
            when(userRepository.findActiveByEmail("nobody@example.com")).thenReturn(Optional.empty());

            // Must not throw. A 404 here would be a free, unauthenticated user
            // enumeration API: no password guessing, no rate-limit pressure,
            // just a yes/no answer per address.
            assertThatCode(() -> authService.requestPasswordReset("nobody@example.com"))
                    .doesNotThrowAnyException();

            verify(emailSender, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("invalidates outstanding tokens before issuing a new one")
        void previousTokensInvalidated() {
            User user = TestDataFactory.activeUser("aditi@example.com");
            when(userRepository.findActiveByEmail("aditi@example.com")).thenReturn(Optional.of(user));

            authService.requestPasswordReset("aditi@example.com");

            // Three reset requests must not leave three live links: an
            // intercepted early one would still work after the user completed a
            // reset with a later one.
            verify(passwordResetTokenRepository).invalidateAllForUser(eq(user.getId()), any());
            verify(emailSender).sendPasswordResetEmail(eq("aditi@example.com"), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("password change")
    class PasswordChange {

        @Test
        @DisplayName("revokes every session so a stolen token cannot survive the change")
        void changeRevokesAllSessions() {
            User user = TestDataFactory.activeUser("aditi@example.com");
            when(userRepository.findActiveById(user.getId())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(eq("current"), anyString())).thenReturn(true);
            when(passwordEncoder.encode("brand-new-password")).thenReturn("$2a$12$new");

            authService.changePassword(user.getId(), "current", "brand-new-password");

            // Without this, an attacker holding a stolen refresh token keeps
            // access after the victim changes their password, which is usually
            // the exact reason the victim changed it.
            verify(tokenService).revokeAllForUser(user.getId());
            verify(emailSender).sendPasswordChangedNotification(eq("aditi@example.com"), anyString());
        }

        @Test
        @DisplayName("requires the current password even for an authenticated caller")
        void wrongCurrentPasswordRejected() {
            User user = TestDataFactory.activeUser("aditi@example.com");
            when(userRepository.findActiveById(user.getId())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(eq("wrong"), anyString())).thenReturn(false);

            assertThatThrownBy(() ->
                    authService.changePassword(user.getId(), "wrong", "brand-new-password"))
                    .isInstanceOf(ApiException.class);

            verify(tokenService, never()).revokeAllForUser(any());
        }
    }
}
