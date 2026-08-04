package com.careerpilot.config;

import com.careerpilot.config.properties.CorsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

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
 * <p><em>Credentials enabled.</em> Phase 11 may place the refresh token in an
 * {@code httpOnly} cookie, and a cookie does not cross origins without this.
 * Enabling it now avoids a confusing change of behaviour mid-frontend-work.
 *
 * <p><strong>Exposed as a {@link CorsConfigurationSource}, not a
 * {@code CorsFilter}.</strong> Phase 2 registered a standalone filter, which was
 * correct while there was no security chain. Now that Spring Security is
 * present, it must own CORS: its chain runs ahead of ordinary filters, so a
 * pre-flight {@code OPTIONS} request would be rejected by security <em>before</em>
 * a standalone CORS filter could add the headers. Registering the source instead
 * lets {@code SecurityConfig} apply it inside the chain, at the right point.
 * Two independent CORS mechanisms would also emit duplicate
 * {@code Access-Control-Allow-Origin} headers, which browsers reject.
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
     * Builds the CORS policy applied by the security chain.
     *
     * @return the configuration source consumed by {@code SecurityConfig}
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
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
        return source;
    }
}
