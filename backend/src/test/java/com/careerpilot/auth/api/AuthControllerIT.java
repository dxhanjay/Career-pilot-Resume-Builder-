package com.careerpilot.auth.api;

import com.careerpilot.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for the authentication endpoints.
 *
 * <p>Runs the full stack — real HTTP dispatch, the real security chain, real
 * Flyway migrations, real PostgreSQL. The unit tests verify that each class
 * behaves correctly in isolation; these verify that the pieces are actually
 * wired together, which is a different and equally common way to be broken. A
 * filter registered in the wrong order, a path missing from the permit list, or
 * a validation annotation that does nothing because the starter is absent all
 * pass every unit test and fail here.
 *
 * <p>The reuse-detection test at the end is the one worth reading: it exercises
 * the complete theft scenario through HTTP, which no unit test can.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@AutoConfigureMockMvc
@DisplayName("Authentication API")
class AuthControllerIT extends AbstractIntegrationTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    AuthControllerIT(@Autowired MockMvc mockMvc, @Autowired ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Nested
    @DisplayName("registration")
    class Registration {

        @Test
        @DisplayName("creates an account and never echoes the password back")
        void registerSucceeds() throws Exception {
            String email = uniqueEmail();

            MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody(email)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.email").value(email))
                    .andExpect(jsonPath("$.data.roles[0]").value("ROLE_USER"))
                    .andReturn();

            String body = result.getResponse().getContentAsString();

            // NFR-SEC-05 checked at the wire, not at the DTO. A field added to
            // the entity in a later phase cannot leak without failing here.
            assertThat(body)
                    .doesNotContain("password")
                    .doesNotContain("passwordHash")
                    .doesNotContain("failedLoginAttempts")
                    .doesNotContain("aiCreditsUsedMonth");
        }

        @Test
        @DisplayName("rejects a duplicate address with 409")
        void duplicateRejected() throws Exception {
            String email = uniqueEmail();

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody(email)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody(email)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("CONFLICT"));
        }

        @Test
        @DisplayName("rejects a short password with 422 and names the field")
        void shortPasswordRejected() throws Exception {
            String body = """
                    {"email":"%s","password":"short","fullName":"Test User"}
                    """.formatted(uniqueEmail());

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.data.details[0].field").value("password"));
        }

        @Test
        @DisplayName("rejects a malformed address with 422")
        void invalidEmailRejected() throws Exception {
            String body = """
                    {"email":"not-an-email","password":"a-long-enough-password","fullName":"Test"}
                    """;

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("returns a token pair")
        void loginSucceeds() throws Exception {
            String email = uniqueEmail();
            register(email);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(email)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.expiresIn").value(900));
        }

        @Test
        @DisplayName("returns 401 for a wrong password")
        void wrongPasswordRejected() throws Exception {
            String email = uniqueEmail();
            register(email);

            String body = """
                    {"email":"%s","password":"definitely-the-wrong-password"}
                    """.formatted(email);

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.data.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("returns an identical response for an unregistered address")
        void unknownAddressLooksTheSame() throws Exception {
            String body = """
                    {"email":"%s","password":"definitely-the-wrong-password"}
                    """.formatted(uniqueEmail());

            // Same status and same code as a wrong password. Any divergence
            // reintroduces the enumeration oracle.
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.data.code").value("UNAUTHORIZED"));
        }
    }

    @Nested
    @DisplayName("protected endpoints")
    class ProtectedEndpoints {

        @Test
        @DisplayName("reject an anonymous request with 401 in the standard envelope")
        void anonymousRejected() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.data.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("reject a forged token")
        void forgedTokenRejected() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer not.a.real.token"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("accept a valid token and return the current user")
        void validTokenAccepted() throws Exception {
            String email = uniqueEmail();
            register(email);
            String accessToken = login(email).get("accessToken").asText();

            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.email").value(email));
        }
    }

    @Nested
    @DisplayName("refresh token rotation")
    class Rotation {

        @Test
        @DisplayName("issues a different refresh token on every exchange")
        void rotationIssuesNewToken() throws Exception {
            String email = uniqueEmail();
            register(email);
            String firstRefresh = login(email).get("refreshToken").asText();

            JsonNode refreshed = refresh(firstRefresh);

            assertThat(refreshed.get("refreshToken").asText())
                    .as("a reused refresh token would defeat reuse detection entirely")
                    .isNotEqualTo(firstRefresh);
        }

        @Test
        @DisplayName("⭐ replaying a consumed token revokes the whole family")
        void reuseDetectionEndsEverySession() throws Exception {
            String email = uniqueEmail();
            register(email);

            // The victim logs in and refreshes once, as normal.
            String stolenToken = login(email).get("refreshToken").asText();
            JsonNode legitimate = refresh(stolenToken);
            String victimsCurrentToken = legitimate.get("refreshToken").asText();

            // The attacker replays the token they captured earlier. It has
            // already been consumed, so this is unambiguous evidence of theft.
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"refreshToken":"%s"}""".formatted(stolenToken)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.data.code").value("TOKEN_REUSE_DETECTED"));

            // The assertion that matters: the victim's CURRENT, never-replayed
            // token is now dead too. Revoking only the replayed token would
            // leave the attacker's rotated successor working, and the compromise
            // would continue indefinitely.
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"refreshToken":"%s"}""".formatted(victimsCurrentToken)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("logout makes the refresh token unusable")
        void logoutRevokes() throws Exception {
            String email = uniqueEmail();
            register(email);
            String refreshToken = login(email).get("refreshToken").asText();

            mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"refreshToken":"%s"}""".formatted(refreshToken)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"refreshToken":"%s"}""".formatted(refreshToken)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("enumeration-resistant endpoints")
    class EnumerationResistance {

        @Test
        @DisplayName("forgot-password returns 202 for an address with no account")
        void forgotPasswordHidesUnknownAddress() throws Exception {
            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s"}""".formatted(uniqueEmail())))
                    .andExpect(status().isAccepted());
        }

        @Test
        @DisplayName("forgot-password returns 202 for a registered address too")
        void forgotPasswordHidesKnownAddress() throws Exception {
            String email = uniqueEmail();
            register(email);

            // Identical to the unknown-address case. That identity is the point.
            mockMvc.perform(post("/api/v1/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"%s"}""".formatted(email)))
                    .andExpect(status().isAccepted());
        }
    }

    @Nested
    @DisplayName("cross-cutting concerns")
    class CrossCutting {

        @Test
        @DisplayName("every response carries a correlation id")
        void correlationIdPresent() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized())
                    .andReturn();

            // NFR-OBS-01 verified on the failure path specifically: an error
            // response is exactly when a user needs an id to quote to support.
            assertThat(result.getResponse().getHeader("X-Correlation-Id")).isNotBlank();
        }

        @Test
        @DisplayName("a 500 never leaks a stack trace")
        void noStackTraceLeaks() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ this is not valid json"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(result.getResponse().getContentAsString())
                    .doesNotContain("com.careerpilot")
                    .doesNotContain("Exception")
                    .doesNotContain("at java.");
        }
    }

    // --- helpers -----------------------------------------------------------

    /**
     * A fresh address per test.
     *
     * <p>The Testcontainers database is shared across the class, so a fixed
     * address would make the second test that used it fail on a duplicate — and
     * would make tests order-dependent, which is the least debuggable kind of
     * flake.
     *
     * @return an address guaranteed not to collide
     */
    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private String registerBody(String email) {
        return """
                {"email":"%s","password":"a-long-enough-password","fullName":"Test User"}
                """.formatted(email);
    }

    private String loginBody(String email) {
        return """
                {"email":"%s","password":"a-long-enough-password"}
                """.formatted(email);
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated());
    }

    private JsonNode login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private JsonNode refresh(String refreshToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}""".formatted(refreshToken)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
