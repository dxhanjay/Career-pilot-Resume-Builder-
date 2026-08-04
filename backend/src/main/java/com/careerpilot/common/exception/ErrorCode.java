package com.careerpilot.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes paired with their HTTP status.
 *
 * <p><strong>Why an enum rather than free-text messages.</strong> Clients need
 * to branch on failures — show a "resend verification" button on one error, a
 * "you're out of credits" upsell on another. Branching on an English message
 * string means the frontend breaks the day someone improves the wording. The
 * code is the contract; the message is for humans and may change freely.
 *
 * <p>Pairing the status with the code here rather than at each throw site means
 * a given failure always produces the same status. Scattered
 * {@code ResponseEntity.status(...)} calls are how the same logical error ends
 * up as a 400 in one endpoint and a 422 in another.
 *
 * <p>Several codes below are not thrown until a later phase. They are declared
 * now so the full failure surface of the API is visible in one file, and so the
 * status mapping is decided once rather than improvised under deadline.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public enum ErrorCode {

    // --- Client errors -----------------------------------------------------

    /** Request body could not be parsed, or a path/query parameter had the wrong type. */
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Request could not be processed"),

    /**
     * Bean Validation rejected the request. 422 rather than 400: the request was
     * syntactically valid JSON that the server understood, and failed on
     * semantics. Field-level detail is carried in {@link ErrorResponse#details()}.
     */
    VALIDATION_ERROR(HttpStatus.UNPROCESSABLE_ENTITY, "Request validation failed"),

    /** No credentials, or credentials that are invalid or expired. */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),

    /** Access token has expired; the client should refresh rather than re-login. */
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Access token has expired"),

    /**
     * A revoked refresh token was presented — meaning it was stolen and replayed.
     * Handling this revokes the entire token family. See user-flows §2.
     */
    TOKEN_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "Session invalidated for security reasons"),

    /** Authenticated, but not permitted to perform this action. */
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),

    /** Account exists but the email address has not been verified. */
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Email address has not been verified"),

    /**
     * Resource does not exist — <em>or</em> is not owned by the caller.
     *
     * <p>Both cases return 404 deliberately. Returning 403 for another user's
     * resource would confirm that the resource exists to anyone enumerating
     * identifiers, which is an information disclosure vulnerability dressed up
     * as a helpful status code.
     */
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),

    /** Request conflicts with current state — duplicate email, duplicate upload. */
    CONFLICT(HttpStatus.CONFLICT, "Request conflicts with the current state"),

    /** A domain rule forbids this operation, e.g. analysing a resume that failed to parse. */
    BUSINESS_RULE_VIOLATION(HttpStatus.CONFLICT, "Operation is not permitted in the current state"),

    /** Upload exceeded the configured size limit. */
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the maximum permitted size"),

    /** Uploaded file is not an accepted type, verified by content rather than extension. */
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "File type is not supported"),

    /** Repeated failed logins; the account is temporarily locked. */
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Account is temporarily locked"),

    /**
     * The user's AI credit allowance is exhausted (NFR-COST-01).
     *
     * <p>402 rather than 429: 429 implies that waiting briefly will help, and it
     * will not — the allowance resets monthly. 403 would imply a permissions
     * problem the user cannot resolve. 402 is the honest code for "permitted,
     * but you have no budget".
     */
    AI_QUOTA_EXCEEDED(HttpStatus.PAYMENT_REQUIRED, "AI usage limit reached for this period"),

    /** Rate limit exceeded; the response carries {@code Retry-After}. */
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"),

    // --- Server errors -----------------------------------------------------

    /** Unhandled failure. The response never carries detail; the log always does. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"),

    /** A required third party (AI provider, storage, mail) is unavailable. */
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "A required service is temporarily unavailable");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    /**
     * @return the HTTP status this error always maps to
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * @return a safe, generic message suitable for returning to a client
     */
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
