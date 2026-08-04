package com.careerpilot.config;

import com.careerpilot.auth.infrastructure.JwtAuthenticationFilter;
import com.careerpilot.auth.infrastructure.RestAccessDeniedHandler;
import com.careerpilot.auth.infrastructure.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * The application's security chain.
 *
 * <p>Every decision here is either a deliberate reduction in attack surface or
 * a deliberate acceptance of risk. Both are annotated.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * BCrypt strength 12.
     *
     * <p>The strength is a work factor, and each increment doubles the cost. At
     * 12 a single hash takes roughly 100 ms on typical hardware — imperceptible
     * on a login, and brutal for an attacker testing a stolen hash dump offline,
     * where the arithmetic turns billions of guesses per second into thousands.
     *
     * <p>Spring's default is 10. Raising it is the single cheapest security
     * improvement available here. Raising it much further starts to matter: the
     * cost lands on our own login endpoint too, and becomes a denial-of-service
     * lever against ourselves.
     *
     * <p>BCrypt stores its work factor inside the hash, so this can be increased
     * later and old hashes keep verifying — they are simply re-hashed at the new
     * strength on next successful login, if we choose to add that.
     *
     * @return the password encoder used for hashing and verification
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Builds the filter chain.
     *
     * @param http                     the builder
     * @param jwtAuthenticationFilter  populates the context from a bearer token
     * @param authenticationEntryPoint renders 401 in our envelope
     * @param accessDeniedHandler      renders 403 in our envelope
     * @param corsConfigurationSource  origins from {@code app.cors.allowed-origins}
     * @return the configured chain
     * @throws Exception if the builder fails
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtAuthenticationFilter,
                                           RestAuthenticationEntryPoint authenticationEntryPoint,
                                           RestAccessDeniedHandler accessDeniedHandler,
                                           CorsConfigurationSource corsConfigurationSource) throws Exception {

        http
                /*
                 * CSRF disabled — and this is safe ONLY because of how tokens are
                 * transported.
                 *
                 * CSRF exploits the browser automatically attaching ambient
                 * credentials (cookies) to cross-site requests. Our credentials
                 * travel in an Authorization header that JavaScript must set
                 * explicitly, and a cross-site attacker cannot make the browser
                 * add it. There is nothing for CSRF to forge.
                 *
                 * ⚠ IF PHASE 11 MOVES THE REFRESH TOKEN INTO AN httpOnly COOKIE,
                 * THIS LINE MUST BE REVISITED. A cookie is ambient credential,
                 * and /auth/refresh would immediately become CSRF-vulnerable:
                 * any site could silently mint a fresh token pair for a logged-in
                 * visitor. The architecture document flags the cookie option as
                 * open; this comment is the tripwire attached to it.
                 */
                .csrf(csrf -> csrf.disable())

                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                /*
                 * Stateless. No HttpSession is created or consulted.
                 *
                 * This is what allows any container to serve any request, which
                 * is what makes horizontal scaling work without sticky sessions
                 * or shared session storage. It also removes session fixation
                 * as a category of attack.
                 */
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                .authorizeHttpRequests(auth -> auth

                        // Pre-flight requests carry no credentials by design.
                        // Requiring authentication here breaks CORS entirely
                        // and produces an error the browser reports as a CORS
                        // problem rather than an auth one — a genuinely
                        // confusing afternoon.
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Authentication endpoints must be reachable without
                        // authentication. Note that /auth/me is NOT in this list.
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/verify-email",
                                "/api/v1/auth/resend-verification",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password").permitAll()

                        // Liveness and readiness only. The platform polls these
                        // before any credential exists, so they cannot be
                        // protected. Everything else under /actuator stays closed.
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info").permitAll()

                        // Disabled entirely in production by configuration; this
                        // permits them in dev, where they are enabled.
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()

                        .requestMatchers("/api/v1/admin/**").hasAuthority("ROLE_ADMIN")

                        /*
                         * Deny by default.
                         *
                         * The most important line in this file. With it, an
                         * endpoint added in a later phase is closed until someone
                         * deliberately opens it. Without it — or with a trailing
                         * permitAll — a forgotten rule means a silently public
                         * endpoint, and nothing fails to announce it.
                         */
                        .anyRequest().authenticated())

                /*
                 * Placed before Spring's form-login filter so the context is
                 * populated by the time authorisation runs. Registering it after
                 * would leave every request unauthenticated regardless of a
                 * perfectly valid token.
                 */
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())

                .headers(headers -> headers
                        // Prevents the API being framed by another origin.
                        .frameOptions(frame -> frame.deny())
                        // HSTS. Only meaningful over HTTPS; harmless locally.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        .contentTypeOptions(Customizer.withDefaults()));

        return http.build();
    }
}
