package com.careerpilot.auth.api;

import com.careerpilot.auth.api.dto.AuthRequests;
import com.careerpilot.auth.application.AuthService;
import com.careerpilot.auth.application.dto.TokenResponse;
import com.careerpilot.auth.application.dto.UserResponse;
import com.careerpilot.common.dto.ApiResponse;
import com.careerpilot.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Authentication endpoints.
 *
 * <p><strong>This class is deliberately thin.</strong> Every method does the
 * same four things and nothing else: bind and validate the request, pull the
 * client's user agent and address from the servlet request, call one use case,
 * and wrap the result in the response envelope. There is no business logic here,
 * no conditionals on domain state, and no entity ever enters this file.
 *
 * <p>That thinness is what lets the same use cases be driven by something other
 * than HTTP later — a scheduled job, an admin tool, an integration test that
 * calls the service directly. Logic that leaks into a controller can only ever
 * be reached through a request.
 *
 * <h2>Enumeration-resistant endpoints</h2>
 *
 * <p>{@code /forgot-password} and {@code /resend-verification} always return
 * 202, whether or not the address exists. That is not an oversight and must not
 * be "improved" into a 404 — see {@code AuthService} for why an honest answer
 * here would be a free, unauthenticated user-enumeration API.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, sign-in, tokens, and password recovery")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // =======================================================================
    // Registration and verification
    // =======================================================================

    /**
     * Registers a new account and sends a verification email.
     *
     * @param request the registration payload
     * @param uriBuilder injected builder for the {@code Location} header
     * @return 201 with the created account
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new account",
            description = "Creates an account in PENDING status and emails a verification link.")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409", description = "Email already registered"))
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody AuthRequests.Register request,
            UriComponentsBuilder uriBuilder) {

        UserResponse user = authService.register(
                request.email(), request.password(), request.fullName());

        URI location = uriBuilder.path("/api/v1/users/{id}").buildAndExpand(user.id()).toUri();

        return ResponseEntity.created(location)
                .body(ApiResponse.ok(user, "Account created. Check your email to confirm it."));
    }

    /**
     * Redeems an email verification token.
     *
     * @param request the token payload
     * @return 200 on success
     */
    @PostMapping("/verify-email")
    @Operation(summary = "Confirm an email address")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @Valid @RequestBody AuthRequests.Token request) {

        authService.verifyEmail(request.token());
        return ResponseEntity.ok(ApiResponse.ok("Email address confirmed. You can now sign in."));
    }

    /**
     * Re-sends a verification email.
     *
     * <p>Always 202, regardless of whether the address exists or is already
     * verified.
     *
     * @param request the address payload
     * @return 202
     */
    @PostMapping("/resend-verification")
    @Operation(summary = "Re-send the verification email",
            description = "Always returns 202, whether or not the address is registered.")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody AuthRequests.EmailOnly request) {

        authService.resendVerification(request.email());
        return ResponseEntity.accepted()
                .body(ApiResponse.ok("If that address needs confirming, a new link is on its way."));
    }

    // =======================================================================
    // Sign-in and tokens
    // =======================================================================

    /**
     * Authenticates and issues a token pair.
     *
     * @param request     credentials
     * @param httpRequest used only to record user agent and address on the session
     * @return 200 with the token pair
     */
    @PostMapping("/login")
    @Operation(summary = "Sign in",
            description = "Returns an access token and a single-use refresh token.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Invalid credentials"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "Email address not confirmed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "423", description = "Account temporarily locked")})
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody AuthRequests.Login request,
            HttpServletRequest httpRequest) {

        TokenResponse tokens = authService.authenticate(
                request.email(), request.password(),
                httpRequest.getHeader("User-Agent"), clientAddress(httpRequest));

        return ResponseEntity.ok(ApiResponse.ok(tokens, "Signed in"));
    }

    /**
     * Exchanges a refresh token for a new pair.
     *
     * @param request     the refresh token
     * @param httpRequest used only to record user agent and address
     * @return 200 with the new token pair
     */
    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token",
            description = """
                    Rotates the refresh token. Presenting an already-used token is treated as \
                    theft: every session in that token's family is revoked.""")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401", description = "Token unknown, expired, or replayed"))
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody AuthRequests.Refresh request,
            HttpServletRequest httpRequest) {

        TokenResponse tokens = authService.refresh(
                request.refreshToken(),
                httpRequest.getHeader("User-Agent"), clientAddress(httpRequest));

        return ResponseEntity.ok(ApiResponse.ok(tokens));
    }

    /**
     * Ends the current session.
     *
     * @param request the refresh token identifying the session
     * @return 204
     */
    @PostMapping("/logout")
    @Operation(summary = "Sign out of this session")
    public ResponseEntity<Void> logout(@Valid @RequestBody AuthRequests.Refresh request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * Ends every session for the authenticated user.
     *
     * @param principal the authenticated caller
     * @return 204
     */
    @PostMapping("/logout-all")
    @Operation(summary = "Sign out of every session")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal AuthenticatedUser principal) {
        authService.logoutAll(principal.getId());
        return ResponseEntity.noContent().build();
    }

    // =======================================================================
    // Password recovery
    // =======================================================================

    /**
     * Starts a password reset.
     *
     * <p>Always 202. See the class documentation.
     *
     * @param request the address payload
     * @return 202
     */
    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset",
            description = "Always returns 202, whether or not the address is registered.")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody AuthRequests.EmailOnly request) {

        authService.requestPasswordReset(request.email());
        return ResponseEntity.accepted()
                .body(ApiResponse.ok("If that address has an account, a reset link is on its way."));
    }

    /**
     * Completes a password reset and revokes every existing session.
     *
     * @param request the token and new password
     * @return 200
     */
    @PostMapping("/reset-password")
    @Operation(summary = "Complete a password reset",
            description = "Sets a new password and signs the account out of every device.")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody AuthRequests.ResetPassword request) {

        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.ok("Password updated. Please sign in again."));
    }

    /**
     * Changes the password of the authenticated user.
     *
     * @param request   current and new password
     * @param principal the authenticated caller
     * @return 200
     */
    @PostMapping("/change-password")
    @Operation(summary = "Change password while signed in",
            description = "Requires the current password, and signs the account out of every device.")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody AuthRequests.ChangePassword request,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        authService.changePassword(
                principal.getId(), request.currentPassword(), request.newPassword());

        return ResponseEntity.ok(ApiResponse.ok("Password updated. Please sign in again."));
    }

    // =======================================================================
    // Current user
    // =======================================================================

    /**
     * Returns the authenticated user.
     *
     * @param principal the authenticated caller
     * @return 200 with the current account
     */
    @GetMapping("/me")
    @Operation(summary = "Get the signed-in user",
            description = "Reads current state from the database, not from the token's claims.")
    public ResponseEntity<ApiResponse<UserResponse>> me(
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return ResponseEntity.ok(ApiResponse.ok(authService.getCurrentUser(principal.getId())));
    }

    // =======================================================================
    // Helpers
    // =======================================================================

    /**
     * Best-effort client address for the session record.
     *
     * <p>Prefers the first entry of {@code X-Forwarded-For}, because Railway
     * terminates TLS at its edge and every request arrives from a proxy —
     * {@code getRemoteAddr()} alone would record the proxy's address for every
     * user, making the "active sessions" view useless.
     *
     * <p><strong>This value is not trustworthy and is never used for a security
     * decision.</strong> {@code X-Forwarded-For} is a client-supplied header and
     * can say anything. It is recorded so a user can recognise their own
     * sessions; it must never gate access, drive rate limiting, or appear in an
     * audit trail as fact.
     *
     * @param request the inbound request
     * @return the apparent client address
     */
    private String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = comma > 0 ? forwarded.substring(0, comma) : forwarded;
            return first.trim();
        }
        return request.getRemoteAddr();
    }
}
