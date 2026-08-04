package com.careerpilot.auth.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The token pair returned by login and refresh.
 *
 * <p>{@code expiresIn} is included so the client can schedule a refresh
 * <em>before</em> the access token dies, rather than discovering expiry through
 * a failed request. Without it a client either decodes the JWT itself, coupling
 * the frontend to the token format, or waits for a 401 and retries, which
 * inserts a visible failure into every fifteen-minute window.
 *
 * <p>{@code tokenType} is always {@code "Bearer"}. It is present because the
 * client concatenates it into the {@code Authorization} header, and hardcoding
 * the scheme on both sides is how the two eventually disagree.
 *
 * <p>The user object is embedded so that a successful login is a single round
 * trip. The alternative — login, then immediately fetch the current user — adds
 * a request to the most latency-sensitive moment in the product.
 *
 * <p><strong>Transport note.</strong> The refresh token is returned in the
 * response body, which makes the client responsible for storing it and is why
 * CSRF protection can safely be disabled (see {@code SecurityConfig}). If Phase
 * 11 moves it to an {@code httpOnly} cookie, this field leaves the body and CSRF
 * protection becomes mandatory on the refresh endpoint.
 *
 * @param accessToken  short-lived signed JWT for the {@code Authorization} header
 * @param refreshToken long-lived opaque token, single-use
 * @param tokenType    always {@code "Bearer"}
 * @param expiresIn    access-token lifetime in seconds
 * @param user         the authenticated user
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Schema(name = "TokenResponse", description = "Access and refresh token pair")
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {

    private static final String BEARER = "Bearer";

    /**
     * @param accessToken  the signed JWT
     * @param refreshToken the opaque refresh token
     * @param expiresIn    access-token lifetime in seconds
     * @param user         the authenticated user
     * @return a populated token response
     */
    public static TokenResponse of(String accessToken,
                                   String refreshToken,
                                   long expiresIn,
                                   UserResponse user) {
        return new TokenResponse(accessToken, refreshToken, BEARER, expiresIn, user);
    }
}
