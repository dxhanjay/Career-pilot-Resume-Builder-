package com.careerpilot.common.exception;

/**
 * Base class for every failure the application raises deliberately.
 *
 * <p>Carrying an {@link ErrorCode} on the exception means
 * {@link GlobalExceptionHandler} can map any of these to a response with one
 * handler method, rather than an {@code instanceof} ladder that grows a branch
 * per exception type and eventually misses one.
 *
 * <p>Extends {@link RuntimeException} rather than {@code Exception} on purpose.
 * A checked exception would force {@code throws} declarations up through every
 * service signature to the controller, where the only thing anyone does with it
 * is let it propagate — all cost, no benefit. It would also silently break
 * Spring's declarative rollback, which by default rolls back on unchecked
 * exceptions only.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * Creates an exception using the error code's default message.
     *
     * @param errorCode the failure classification
     */
    public ApiException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /**
     * Creates an exception with a specific message.
     *
     * <p>The message is returned to the client, so it must contain nothing the
     * caller is not entitled to know — no SQL, no internal identifiers, no
     * confirmation that another user's data exists.
     *
     * @param errorCode the failure classification
     * @param message   client-safe description
     */
    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Creates an exception wrapping an underlying cause.
     *
     * <p>The cause is logged and never serialised — that distinction is what
     * keeps stack traces out of HTTP responses (NFR-SEC-05).
     *
     * @param errorCode the failure classification
     * @param message   client-safe description
     * @param cause     the underlying failure, for logs only
     */
    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * @return the failure classification, used to derive the HTTP status
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
