package com.careerpilot.auth.infrastructure;

import com.careerpilot.common.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the {@code Authorization: Bearer} header, validates the token, and
 * populates the {@code SecurityContext}.
 *
 * <p><strong>This filter never rejects a request.</strong> An absent, malformed,
 * or expired token simply leaves the context empty and the chain continues.
 * Rejection is Spring Security's job, further along, where it applies the
 * authorisation rules declared in {@code SecurityConfig}.
 *
 * <p>That separation is what allows a single filter to serve both public and
 * protected endpoints. A filter that returned 401 on a missing token would break
 * every public route (login would demand a token in order to log in) and would
 * duplicate the decision about which paths need authentication in two places
 * that inevitably drift apart.
 *
 * <p>Extends {@link OncePerRequestFilter} rather than implementing
 * {@code Filter}: a plain filter runs again on every internal forward, so error
 * dispatches and async re-dispatches would re-parse the token and re-populate
 * the context redundantly.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractBearerToken(request);

        // The null check on the existing authentication matters: without it, a
        // forwarded request could overwrite an authentication established
        // earlier in the chain.
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            AuthenticatedUser principal = jwtTokenProvider.parseAccessToken(token);

            if (principal != null) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                principal,
                                // Credentials are null: the token has already been
                                // verified, and holding it here would keep a live
                                // credential in the context for the whole request.
                                null,
                                principal.getAuthorities());

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the bearer token, or {@code null} if the header is absent or does
     * not use the {@code Bearer} scheme.
     *
     * @param request the inbound request
     * @return the raw token, or {@code null}
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return StringUtils.hasText(token) ? token : null;
        }
        return null;
    }
}
