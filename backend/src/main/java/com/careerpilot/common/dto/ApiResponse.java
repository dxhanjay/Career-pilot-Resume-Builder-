package com.careerpilot.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * The uniform response envelope used by every endpoint in the API.
 *
 * <p>Defined once, in one place, because 80 endpoints left to their own devices
 * produce 80 slightly different response shapes — and every frontend then needs
 * a special case per endpoint. A client that can rely on {@code success},
 * {@code data}, and {@code error} always meaning the same thing can write one
 * response handler.
 *
 * <p>{@code @JsonInclude(NON_NULL)} keeps absent fields out of the payload
 * entirely rather than serialising {@code "data": null}, so a success response
 * carries no {@code error} key and vice versa.
 *
 * <p>This is a {@code record}: immutable, no Lombok, no boilerplate. Response
 * DTOs have no reason to be mutable — nothing should be modifying a response
 * after the service layer has produced it.
 *
 * @param <T>       the payload type
 * @param success   whether the request succeeded
 * @param data      the payload; {@code null} for errors and for 204-style results
 * @param message   optional human-readable note; never the primary error channel
 * @param timestamp server-side instant the response was produced, always UTC
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        Instant timestamp
) {

    /**
     * Successful response carrying a payload.
     *
     * @param data the payload
     * @param <T>  payload type
     * @return an envelope with {@code success = true}
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    /**
     * Successful response carrying a payload and a message.
     *
     * @param data    the payload
     * @param message human-readable confirmation, e.g. "Resume uploaded"
     * @param <T>     payload type
     * @return an envelope with {@code success = true}
     */
    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, data, message, Instant.now());
    }

    /**
     * Successful response with no payload, for operations whose only outcome is
     * that they happened.
     *
     * @param message human-readable confirmation
     * @return an envelope with {@code success = true} and no {@code data}
     */
    public static ApiResponse<Void> ok(String message) {
        return new ApiResponse<>(true, null, message, Instant.now());
    }

    /**
     * Failed response. The {@link com.careerpilot.common.exception.ErrorResponse}
     * is carried in {@code data} so that the envelope shape stays constant
     * between success and failure — a client parsing the response does not need
     * to know which it got before it can read it.
     *
     * @param error structured error detail
     * @return an envelope with {@code success = false}
     */
    public static ApiResponse<Object> error(Object error) {
        return new ApiResponse<>(false, error, null, Instant.now());
    }
}
