package com.careerpilot.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Typed binding for {@code app.auth.*} — authentication policy.
 *
 * <p>Every value here is a policy decision rather than a technical constant,
 * which is why they are configuration and not literals in a service. Lockout
 * thresholds in particular need tuning against real abuse patterns, and tuning
 * a constant means a deploy.
 *
 * @param requireEmailVerification whether unverified accounts may log in
 * @param maxFailedLoginAttempts   failures tolerated before a temporary lock
 * @param lockoutDuration          how long that lock lasts
 * @param verificationTokenTtl     lifetime of an email verification link
 * @param passwordResetTokenTtl    lifetime of a password reset link
 * @param frontendBaseUrl          base URL used to build links in outbound email
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(

        /*
         * Whether an account must verify its email address before it can log in.
         *
         * A genuine product trade, which is why it is a flag rather than a
         * decision baked into code. Enforcing it protects deliverability and
         * blocks throwaway signups, at the cost of every user who mistypes their
         * address or never finds the mail. Not enforcing it maximises activation
         * and accumulates unreachable accounts.
         *
         * Defaults to true. Flip it in configuration; no code changes either way.
         */
        boolean requireEmailVerification,

        @Min(3)
        int maxFailedLoginAttempts,

        Duration lockoutDuration,

        Duration verificationTokenTtl,

        Duration passwordResetTokenTtl,

        @NotBlank
        String frontendBaseUrl
) {
}
