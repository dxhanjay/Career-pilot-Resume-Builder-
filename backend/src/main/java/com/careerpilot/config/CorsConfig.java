package com.careerpilot.config;

import com.careerpilot.config.properties.CorsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * Cross-origin resource sharing configuration.
 *
 * <p>CORS is load-bearing in this architecture rather than incidental: the SPA
 * is served from Vercel and the API runs on Railway, so <em>every</em> browser
 * request is cross-origin. Get this wrong and the application is entirely
 * non-functional in a browser while working perfectly in Postman — which is a
 * genuinely disorienting way to spend an afternoon.
 *
 * <p><strong>Three decisions worth stating.</strong>
 *
 * <p><em>Exact origins, never a wildcard.</em> {@code allowedOrigins("*")}
 * paired with credentials is rejected by browsers outright, and even without
 * credentials it invites any site to call the API on a visitor's behalf. The
 * list comes from configuration so that adding an environment is a variable
 * change rather than a code change.
 *
 * <p><em>{@code allowedOriginPatterns} rather than {@code allowedOrigins}.</em>
 * The pattern variant is what permits {@code allowCredentials(true)} to
 * coexist with configured origins, and it accommodates Vercel's per-deployment
 * preview subdomains where an exact list cannot be maintained by hand.
 *
 * <p><em>Credentials enabled.</em> Phase 3 will place the refresh token in an
 * {@code httpOnly} cookie where the deployment topology allows it, and a cookie
 * does not cross origins without this. Enabling it now avoids a confusing
 * change of behaviour mid-authentication-work.
 *
 * <p>Registered as a {@link CorsFilter} bean rather than via
 * {@code WebMvcConfigurer}, because a filter runs ahead of Spring Security's
 * chain. Configured the other way, a pre-flight {@code OPTIONS} request is
 * rejected by security before CORS headers are ever added — the single most
 * common cause of "CORS works until I add authentication".
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    private final CorsProperties corsProperties;

    /**
     * Constructor injection — the only form used in this codebase.
     *
     * <p>Field injection makes dependencies invisible in the signature, allows
     * a class to be constructed in an invalid state, and cannot be used at all
     * in a plain unit test without reflection. An ArchUnit rule fails the build
     * on any {@code @Autowired} field.
     *
     * @param corsProperties validated CORS configuration
     */
    public CorsConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    /**
     * Builds the CORS filter from configured origins.
     *
     * @return a filter applying the CORS policy to every path
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));

        // Exposed so the SPA can read the correlation ID from a failed response
        // and show it to the user for support purposes. Response headers are not
        // readable by JavaScript unless explicitly exposed.
        configuration.setExposedHeaders(List.of("X-Correlation-Id", "Retry-After"));

        configuration.setAllowCredentials(true);

        // Cache pre-flight results for an hour. Without this the browser issues
        // an OPTIONS request before every single call, doubling request volume
        // and adding a round trip to each user action.
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        log.info("CORS configured for origins: {}", corsProperties.allowedOrigins());
        return new CorsFilter(source);
    }
}
