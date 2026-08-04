package com.careerpilot.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Typed binding for {@code app.jwt.*}.
 *
 * <p>The {@code @Size(min = 32)} on the secret is a security control rather
 * than tidiness. HS256 is an HMAC, and an HMAC is only as strong as its key: a
 * short or guessable secret means anyone can mint a valid token for any user,
 * including one claiming {@code ROLE_ADMIN}. The RFC 7518 minimum for HS256 is
 * 256 bits, and this constraint refuses to start the application below it.
 *
 * <p>Failing at startup is the point. A weak secret otherwise produces an
 * application that works perfectly and is trivially forgeable — a defect with no
 * symptom until it is exploited.
 *
 * @param secret             signing key; minimum 32 characters, from the environment
 * @param accessTokenTtl     access-token lifetime (NFR-SEC-03: ≤ 15 minutes)
 * @param refreshTokenTtl    refresh-token lifetime (NFR-SEC-03: ≤ 7 days)
 * @param issuer             {@code iss} claim
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(

        @NotBlank(message = "JWT secret must be configured")
        @Size(min = 32, message = """
                JWT secret must be at least 32 characters (256 bits) for HS256. \
                Generate one with: openssl rand -base64 48""")
        String secret,

        @Positive
        Duration accessTokenTtl,

        @Positive
        Duration refreshTokenTtl,

        @NotBlank
        String issuer
) {
}
