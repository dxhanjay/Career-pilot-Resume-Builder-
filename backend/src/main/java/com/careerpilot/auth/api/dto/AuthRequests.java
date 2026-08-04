package com.careerpilot.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payloads for the authentication endpoints.
 *
 * <p>Grouped into one file as nested records because they are small, closely
 * related, and always read together. Ten separate files of eight lines each
 * would add navigation cost without adding clarity.
 *
 * <p>All are {@code record}s: immutable, no Lombok, and impossible to mutate
 * after binding. A mutable request object invites a controller to "fix up" input
 * before passing it on, which quietly moves validation out of the declared
 * constraints and into code nobody reads.
 *
 * <h2>On password rules</h2>
 *
 * <p>Two limits deserve explanation, because both look arbitrary and neither is.
 *
 * <p><strong>The 72-character maximum is a BCrypt constraint, not a policy
 * choice.</strong> BCrypt hashes at most 72 bytes and <em>silently ignores</em>
 * everything beyond. Without this limit a user could set a 100-character
 * passphrase, and the last 28 characters would contribute nothing — while the
 * user believed they had a stronger password than they did. Rejecting the input
 * is honest; truncating it silently is not.
 *
 * <p><strong>There are deliberately no composition rules</strong> — no "must
 * contain an uppercase letter and a symbol". NIST SP 800-63B recommends against
 * them: they push users toward predictable patterns (<em>Password1!</em>),
 * measurably reduce memorability, and add far less entropy than simply requiring
 * more length. A 12-character minimum with no composition rules is stronger in
 * practice than 8 characters with four character-class requirements.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class AuthRequests {

    private AuthRequests() {
        // Namespace only; never instantiated.
    }

    /**
     * New account registration.
     *
     * @param email    desired address
     * @param password plaintext password, hashed before storage
     * @param fullName display name
     */
    @Schema(name = "RegisterRequest", description = "New account registration")
    public record Register(

            @NotBlank(message = "Email is required")
            @Email(message = "Must be a valid email address")
            @Size(max = 255, message = "Email must not exceed 255 characters")
            @Schema(example = "aditi@example.com")
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 12, max = 72, message = "Password must be between 12 and 72 characters")
            @Schema(description = "12-72 characters. Length matters more than symbols.",
                    example = "correct horse battery staple")
            String password,

            @NotBlank(message = "Name is required")
            @Size(min = 2, max = 120, message = "Name must be between 2 and 120 characters")
            @Schema(example = "Aditi Sharma")
            String fullName
    ) {
    }

    /**
     * Credentials for login.
     *
     * <p>Note the absence of a {@code @Size} constraint on the password. On
     * registration, length rules protect the user; on login they would leak
     * policy — an attacker learns the minimum length for free, and a legacy
     * password that predates a rule change would be rejected before it could
     * even be checked.
     *
     * @param email    registered address
     * @param password plaintext password
     */
    @Schema(name = "LoginRequest", description = "Credentials for sign-in")
    public record Login(

            @NotBlank(message = "Email is required")
            @Email(message = "Must be a valid email address")
            @Schema(example = "aditi@example.com")
            String email,

            @NotBlank(message = "Password is required")
            String password
    ) {
    }

    /**
     * A refresh token being exchanged, or a session being ended.
     *
     * @param refreshToken the token previously issued to this client
     */
    @Schema(name = "RefreshRequest", description = "Refresh token exchange")
    public record Refresh(

            @NotBlank(message = "Refresh token is required")
            String refreshToken
    ) {
    }

    /**
     * A token from an emailed link.
     *
     * @param token the raw token
     */
    @Schema(name = "TokenRequest", description = "Token from an emailed link")
    public record Token(

            @NotBlank(message = "Token is required")
            String token
    ) {
    }

    /**
     * An email address alone, for resend and forgot-password.
     *
     * @param email the address
     */
    @Schema(name = "EmailRequest", description = "Email address")
    public record EmailOnly(

            @NotBlank(message = "Email is required")
            @Email(message = "Must be a valid email address")
            @Schema(example = "aditi@example.com")
            String email
    ) {
    }

    /**
     * Completion of a password reset.
     *
     * @param token       the raw token from the emailed link
     * @param newPassword the replacement password
     */
    @Schema(name = "ResetPasswordRequest", description = "Complete a password reset")
    public record ResetPassword(

            @NotBlank(message = "Token is required")
            String token,

            @NotBlank(message = "New password is required")
            @Size(min = 12, max = 72, message = "Password must be between 12 and 72 characters")
            String newPassword
    ) {
    }

    /**
     * Password change by an already-authenticated user.
     *
     * @param currentPassword the existing password, re-proving identity
     * @param newPassword     the replacement
     */
    @Schema(name = "ChangePasswordRequest", description = "Change password while signed in")
    public record ChangePassword(

            @NotBlank(message = "Current password is required")
            String currentPassword,

            @NotBlank(message = "New password is required")
            @Size(min = 12, max = 72, message = "Password must be between 12 and 72 characters")
            String newPassword
    ) {
    }
}
