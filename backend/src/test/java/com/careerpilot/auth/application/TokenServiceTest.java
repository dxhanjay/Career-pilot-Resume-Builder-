package com.careerpilot.auth.application;

import com.careerpilot.auth.domain.RefreshToken;
import com.careerpilot.auth.domain.User;
import com.careerpilot.auth.infrastructure.JwtTokenProvider;
import com.careerpilot.auth.infrastructure.RefreshTokenRepository;
import com.careerpilot.common.exception.ApiException;
import com.careerpilot.common.exception.ErrorCode;
import com.careerpilot.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TokenService}.
 *
 * <p>The reuse-detection tests below are the most important in the project. The
 * mechanism they cover has one property that makes it dangerous to leave
 * untested: <strong>when it silently stops working, nothing breaks.</strong>
 * Logins succeed, refreshes succeed, and users notice nothing — the system has
 * simply stopped detecting stolen tokens. There is no failing request, no error
 * log, and no metric that moves. Only a test can tell you it still works.
 *
 * <p>No Spring context: {@code TokenService} takes two collaborators through its
 * constructor, so plain Mockito runs the whole suite in milliseconds.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenService")
class TokenServiceTest {

    private static final String RAW_TOKEN = "a-raw-refresh-token";
    private static final String TOKEN_HASH = "hash-of-the-raw-token";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private TokenService tokenService;

    private User user;
    private UUID familyId;

    @BeforeEach
    void setUp() {
        user = TestDataFactory.activeUser("aditi@example.com");
        familyId = UUID.randomUUID();

        // lenient(): not every test reaches the hashing call, and strict stubs
        // would fail those tests for an unrelated reason.
        lenient().when(jwtTokenProvider.hashRefreshToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        lenient().when(jwtTokenProvider.generateRefreshToken()).thenReturn("new-raw-token");
        lenient().when(jwtTokenProvider.hashRefreshToken("new-raw-token")).thenReturn("new-hash");
        lenient().when(jwtTokenProvider.getRefreshTokenTtl()).thenReturn(Duration.ofDays(7));
    }

    @Nested
    @DisplayName("reuse detection")
    class ReuseDetection {

        @Test
        @DisplayName("revokes the ENTIRE family when a revoked token is replayed")
        void replayRevokesWholeFamily() {
            RefreshToken revoked = TestDataFactory.refreshToken(user, TOKEN_HASH, familyId);
            revoked.revoke(null);

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(revoked));

            assertThatThrownBy(() -> tokenService.rotate(RAW_TOKEN, "agent", "1.2.3.4"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.TOKEN_REUSE_DETECTED));

            // The assertion that matters. Revoking only the replayed token would
            // leave the attacker's freshly-rotated successor working, which is
            // the entire failure mode this design exists to prevent.
            ArgumentCaptor<UUID> capturedFamily = ArgumentCaptor.forClass(UUID.class);
            verify(refreshTokenRepository).revokeFamily(capturedFamily.capture(), any());
            assertThat(capturedFamily.getValue()).isEqualTo(familyId);
        }

        @Test
        @DisplayName("issues no replacement token when a replay is detected")
        void replayIssuesNoNewToken() {
            RefreshToken revoked = TestDataFactory.refreshToken(user, TOKEN_HASH, familyId);
            revoked.revoke(null);

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(revoked));

            assertThatThrownBy(() -> tokenService.rotate(RAW_TOKEN, "agent", "1.2.3.4"))
                    .isInstanceOf(ApiException.class);

            verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("does NOT revoke the family when a token has merely expired")
        void expiryIsNotTreatedAsTheft() {
            RefreshToken expired = TestDataFactory.expiredRefreshToken(user, TOKEN_HASH, familyId);

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> tokenService.rotate(RAW_TOKEN, "agent", "1.2.3.4"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));

            // Expiry is ordinary lifecycle, not evidence of anything. Treating
            // it as theft would sign out every user who left a tab open over a
            // weekend, which would make the control indistinguishable from a bug.
            verify(refreshTokenRepository, never()).revokeFamily(any(), any());
        }

        @Test
        @DisplayName("does NOT revoke any family for a token that was never issued")
        void unknownTokenTouchesNoFamily() {
            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> tokenService.rotate(RAW_TOKEN, "agent", "1.2.3.4"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.UNAUTHORIZED));

            verify(refreshTokenRepository, never()).revokeFamily(any(), any());
        }
    }

    @Nested
    @DisplayName("rotation")
    class Rotation {

        @Test
        @DisplayName("keeps the successor in the same family")
        void successorStaysInFamily() {
            RefreshToken valid = TestDataFactory.refreshToken(user, TOKEN_HASH, familyId);

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(valid));
            when(refreshTokenRepository.findByTokenHash("new-hash")).thenReturn(Optional.empty());

            TokenService.RotationResult result = tokenService.rotate(RAW_TOKEN, "agent", "1.2.3.4");

            assertThat(result.rawToken()).isEqualTo("new-raw-token");
            assertThat(result.user()).isSameAs(user);

            ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(saved.capture());

            // A new family here would break detection permanently: a replayed
            // token would revoke a family the attacker's token no longer
            // belongs to, and the compromise would continue undisturbed.
            assertThat(saved.getValue().getFamilyId())
                    .as("the successor must inherit the family")
                    .isEqualTo(familyId);
        }

        @Test
        @DisplayName("revokes the presented token, making it single-use")
        void presentedTokenIsConsumed() {
            RefreshToken valid = TestDataFactory.refreshToken(user, TOKEN_HASH, familyId);
            RefreshToken successor = TestDataFactory.refreshToken(user, "new-hash", familyId);

            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(valid));
            when(refreshTokenRepository.findByTokenHash("new-hash")).thenReturn(Optional.of(successor));

            tokenService.rotate(RAW_TOKEN, "agent", "1.2.3.4");

            // Single use is the precondition for reuse detection. If the old
            // token stayed valid, a replay would be indistinguishable from
            // ordinary use and nothing could ever be detected.
            assertThat(valid.isRevoked()).isTrue();
            assertThat(valid.getReplacedById()).isEqualTo(successor.getId());
        }

        @Test
        @DisplayName("refuses to refresh an account that may no longer authenticate")
        void suspendedAccountCannotRefresh() {
            User suspended = TestDataFactory.activeUser("suspended@example.com");
            suspended.markDeleted();

            RefreshToken valid = TestDataFactory.refreshToken(suspended, TOKEN_HASH, familyId);
            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(valid));

            assertThatThrownBy(() -> tokenService.rotate(RAW_TOKEN, "agent", "1.2.3.4"))
                    .isInstanceOf(ApiException.class);

            // This re-check at refresh is what bounds the staleness accepted by
            // stateless authentication: a suspended user cannot renew, so access
            // ends within one access-token lifetime.
            verify(refreshTokenRepository).revokeAllForUser(eq(suspended.getId()), any());
        }
    }

    @Nested
    @DisplayName("revocation")
    class Revocation {

        @Test
        @DisplayName("logout revokes the presented token")
        void logoutRevokes() {
            RefreshToken valid = TestDataFactory.refreshToken(user, TOKEN_HASH, familyId);
            when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(valid));

            tokenService.revoke(RAW_TOKEN);

            assertThat(valid.isRevoked()).isTrue();
        }

        @Test
        @DisplayName("logout of an unknown token is silent, not an error")
        void logoutOfUnknownTokenIsSilent() {
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            // Reporting "that token doesn't exist" would tell an unauthenticated
            // caller which tokens are real.
            tokenService.revoke(RAW_TOKEN);

            verify(refreshTokenRepository, times(1)).findByTokenHash(TOKEN_HASH);
        }
    }
}
