package com.careerpilot.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Structured error payload, carried inside {@link com.careerpilot.common.dto.ApiResponse}
 * when a request fails.
 *
 * <p>Three fields, each earning its place:
 *
 * <ul>
 *   <li>{@code code} — the stable {@link ErrorCode} name. Clients branch on
 *       this. It never changes for a given failure.</li>
 *   <li>{@code message} — human-readable, safe to display, free to be reworded.</li>
 *   <li>{@code traceId} — the request correlation ID (NFR-OBS-01). This is what
 *       turns a support ticket from "it broke" into a single log query. It is
 *       the reason this record exists rather than returning a bare string.</li>
 * </ul>
 *
 * <p>{@code details} is populated only for validation failures, where the client
 * needs to know <em>which field</em> was wrong in order to highlight it. For
 * every other error it is omitted entirely rather than serialised as an empty
 * array.
 *
 * @param code    stable error classification
 * @param message client-safe description
 * @param details per-field validation failures; {@code null} unless relevant
 * @param traceId request correlation identifier
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        List<FieldError> details,
        String traceId
) {

    /**
     * A single field-level validation failure.
     *
     * @param field   the rejected field's path, e.g. {@code "email"}
     * @param message why it was rejected
     */
    public record FieldError(String field, String message) {
    }

    /**
     * Builds an error response with no field-level detail.
     *
     * @param code    stable error classification
     * @param message client-safe description
     * @param traceId request correlation identifier
     * @return the error payload
     */
    public static ErrorResponse of(ErrorCode code, String message, String traceId) {
        return new ErrorResponse(code.name(), message, null, traceId);
    }

    /**
     * Builds an error response including field-level validation detail.
     *
     * @param code    stable error classification
     * @param message client-safe description
     * @param details the rejected fields
     * @param traceId request correlation identifier
     * @return the error payload
     */
    public static ErrorResponse of(ErrorCode code, String message, List<FieldError> details, String traceId) {
        return new ErrorResponse(code.name(), message, details, traceId);
    }
}
