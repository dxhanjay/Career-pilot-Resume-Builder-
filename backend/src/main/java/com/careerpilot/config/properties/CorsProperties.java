package com.careerpilot.config.properties;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Typed binding for {@code app.cors.*}.
 *
 * <p>Origins are configuration, not code, because they differ per environment:
 * {@code http://localhost:5173} in development, the Vercel production domain in
 * production, and a preview domain for each pull-request deployment. Hardcoding
 * them would mean a code change and a redeploy to add an environment.
 *
 * <p>{@code @Validated} with {@code @NotEmpty} makes a missing or blank
 * {@code APP_CORS_ALLOWED_ORIGINS} fail at <em>startup</em> with a clear
 * message. The alternative — binding to an empty list — produces an application
 * that starts cleanly, serves health checks happily, and rejects every browser
 * request from the frontend with an opaque CORS error. Failing fast turns a
 * confusing runtime symptom into an obvious deployment error.
 *
 * @param allowedOrigins exact origins permitted to call the API; never {@code "*"}
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(

        @NotEmpty(message = "At least one allowed origin must be configured")
        List<String> allowedOrigins
) {
}
