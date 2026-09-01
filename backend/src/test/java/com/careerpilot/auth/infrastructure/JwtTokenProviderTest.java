package com.careerpilot.auth.infrastructure;

import com.careerpilot.common.security.AuthenticatedUser;
import com.careerpilot.config.properties.JwtProperties;
import com.careerpilot.support.TestDataFactory;
import com.careerpilot.auth.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtTokenProvider}.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@DisplayName("JwtTokenProvider")
class JwtTokenProviderTest {

    private static final String SECRET =
            "a-test-signing-secret-that-is-comfortably-over-32-bytes-long";

    private final JwtProperties properties = new JwtProperties(
            SECRET, Duration.ofMinutes(15), Duration.ofDays(7), "careerpilot-ai");

    private final JwtTokenProvider provider = new JwtTokenProvider(properties);

    @Nested
    @DisplayName("access tokens")
    class AccessTokens {

        @Test
        @DisplayName("round-trip preserves identity and roles")
        void roundTrip() {
            User user = TestDataFactory.activeUser("aditi@example.com");

            String token = provider.createAccessToken(user);
            AuthenticatedUser principal = provider.parseAccessToken(token);

            assertThat(principal).isNotNull();
            assertThat(principal.getId()).isEqualTo(user.getId());
            assertThat(principal.getEmail()).isEqualTo("aditi@example.com");
            assertThat(principal.getAuthorities())
                    .extracting(Object::toString)
                    .containsExactly("ROLE_USER");
        }

        @Test
        @DisplayName("uses the user id as the subject, not the email")
        void subjectIsTheUserId() {
            User user = TestDataFactory.activeUser("aditi@example.com");

            AuthenticatedUser principal = provider.parseAccessToken(provider.createAccessToken(user));

            // Email addresses change; identifiers do not. A token whose subject
            // was an email would silently resolve to the wrong account after a
            // change of address.
            assertThat(principal.getId()).isEqualTo(user.getId());
        }

        @Test
        @DisplayName("rejects a token signed with a different secret")
        void rejectsForeignSignature() {
            User user = TestDataFactory.activeUser("aditi@example.com");

            JwtTokenProvider attacker = new JwtTokenProvider(new JwtProperties(
                    "a-completely-different-secret-also-over-32-bytes-long",
                    Duration.ofMinutes(15), Duration.ofDays(7), "careerpilot-ai"));

            String forged = attacker.createAccessToken(user);

            // The whole security model rests on this. If a token signed with any
            // other key were accepted, anyone could mint one claiming ROLE_ADMIN.
            assertThat(provider.parseAccessToken(forged)).isNull();
        }

        @Test
        @DisplayName("rejects a token from a different issuer")
        void rejectsForeignIssuer() {
            User user = TestDataFactory.activeUser("aditi@example.com");

            JwtTokenProvider other = new JwtTokenProvider(new JwtProperties(
                    SECRET, Duration.ofMinutes(15), Duration.ofDays(7), "some-other-service"));

            assertThat(provider.parseAccessToken(other.createAccessToken(user))).isNull();
        }

        @Test
        @DisplayName("rejects an expired token")
        void rejectsExpired() {
            // Issue the token as of an hour ago, against a normal 15-minute TTL,
            // so what is being tested is an expired token rather than a
            // configuration that must never exist.
            JwtTokenProvider inThePast = new JwtTokenProvider(
                    new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(7),
                            "careerpilot-ai"),
                    Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC));

            User user = TestDataFactory.activeUser("aditi@example.com");

            assertThat(provider.parseAccessToken(inThePast.createAccessToken(user))).isNull();
        }

        @Test
        @DisplayName("rejects a tampered payload")
        void rejectsTamperedPayload() {
            User user = TestDataFactory.activeUser("aditi@example.com");
            String token = provider.createAccessToken(user);

            // Flip a character in the payload segment. The signature no longer
            // matches, which is exactly what an attacker editing the roles claim
            // would produce.
            String[] parts = token.split("\\.");
            char[] payload = parts[1].toCharArray();
            payload[5] = payload[5] == 'A' ? 'B' : 'A';
            String tampered = parts[0] + "." + new String(payload) + "." + parts[2];

            assertThat(provider.parseAccessToken(tampered)).isNull();
        }

        @Test
        @DisplayName("rejects malformed input without throwing")
        void rejectsGarbage() {
            assertThat(provider.parseAccessToken("not-a-jwt")).isNull();
            assertThat(provider.parseAccessToken("")).isNull();
            assertThat(provider.parseAccessToken("a.b.c")).isNull();
        }
    }

    @Nested
    @DisplayName("refresh tokens")
    class RefreshTokens {

        @Test
        @DisplayName("are unpredictable")
        void areUnique() {
            String first = provider.generateRefreshToken();
            String second = provider.generateRefreshToken();

            assertThat(first).isNotEqualTo(second);
            // 32 bytes rendered as hex.
            assertThat(first).hasSize(64);
        }

        @Test
        @DisplayName("hash deterministically, so a presented token can be looked up")
        void hashingIsDeterministic() {
            String token = provider.generateRefreshToken();

            // This is why SHA-256 is used rather than BCrypt. A per-token salt
            // would make the same input hash differently every time, and finding
            // the matching row would require comparing every stored hash.
            assertThat(provider.hashRefreshToken(token))
                    .isEqualTo(provider.hashRefreshToken(token));
        }

        @Test
        @DisplayName("hash to the column width the schema declares")
        void hashFitsTheColumn() {
            // refresh_tokens.token_hash is VARCHAR(64). A mismatch here would fail
            // at insert time in production rather than in this test.
            assertThat(provider.hashRefreshToken("anything")).hasSize(64);
        }

        @Test
        @DisplayName("different tokens hash differently")
        void distinctTokensDistinctHashes() {
            assertThat(provider.hashRefreshToken("token-a"))
                    .isNotEqualTo(provider.hashRefreshToken("token-b"));
        }
    }
}
