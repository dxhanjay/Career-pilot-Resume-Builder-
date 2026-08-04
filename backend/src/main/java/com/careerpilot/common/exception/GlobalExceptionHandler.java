package com.careerpilot.common.exception;

import com.careerpilot.common.dto.ApiResponse;
import com.careerpilot.common.web.CorrelationIdFilter;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Objects;

/**
 * Translates every exception that escapes a controller into a consistent,
 * client-safe response.
 *
 * <p><strong>This class is a security control, not a convenience.</strong>
 * Without it, Spring Boot's default error handling returns its own JSON body,
 * and in several common misconfigurations that body includes the exception
 * message and stack trace. A stack trace tells an attacker the framework
 * versions in use, the package structure, and often the SQL that failed.
 * NFR-SEC-05 depends on this class existing and on every branch below returning
 * a curated message.
 *
 * <p>The governing rule throughout: <em>log everything, return nothing.</em>
 * The exception, its cause, and its stack trace go to the log with the
 * correlation ID attached. The client receives a stable error code, a safe
 * message, and that same correlation ID — enough for a support conversation to
 * be productive, and not enough to be useful to anyone probing the system.
 *
 * <p>Handlers are ordered most-specific first. Spring resolves by closest type
 * match rather than declaration order, but keeping them ordered makes the file
 * readable and makes an accidentally-unreachable handler visible.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles every exception the application raises deliberately.
     *
     * <p>These are expected outcomes, not defects — a duplicate email, an
     * exhausted quota, a missing resource. They are logged at WARN with no stack
     * trace, because a stack trace for an anticipated branch is noise that makes
     * real defects harder to find.
     *
     * @param ex the raised exception
     * @return response carrying the exception's own error code and message
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleApiException(ApiException ex) {
        ErrorCode code = ex.getErrorCode();
        log.warn("Handled API exception [{}]: {}", code, ex.getMessage());
        return build(code, ex.getMessage());
    }

    /**
     * Handles {@code @Valid} failures on a request body.
     *
     * <p>This is the one case where field-level detail is returned. The client
     * needs to know which input to highlight, and the field names are ones it
     * sent in the first place — so echoing them back discloses nothing.
     *
     * @param ex the validation failure
     * @return 422 with a per-field breakdown
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ErrorResponse.FieldError(
                        fieldError.getField(),
                        Objects.requireNonNullElse(fieldError.getDefaultMessage(), "is invalid")))
                .toList();

        log.warn("Request validation failed on {} field(s)", details.size());

        ErrorResponse body = ErrorResponse.of(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                details,
                traceId());

        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiResponse.error(body));
    }

    /**
     * Handles {@code @Validated} failures on path variables, request parameters,
     * and method arguments — the constraint-violation counterpart to
     * {@link #handleValidation}.
     *
     * @param ex the constraint violation
     * @return 422 with a per-property breakdown
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException ex) {
        List<ErrorResponse.FieldError> details = ex.getConstraintViolations().stream()
                .map(violation -> new ErrorResponse.FieldError(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()))
                .toList();

        log.warn("Constraint violation on {} property/properties", details.size());

        ErrorResponse body = ErrorResponse.of(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
                details,
                traceId());

        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiResponse.error(body));
    }

    /**
     * Handles unparseable request bodies — malformed JSON, a string where a
     * number was expected, a bad enum value.
     *
     * <p>The exception message is <em>not</em> forwarded. Jackson's messages
     * name internal class and field paths, which is a small structural
     * disclosure for no benefit to a legitimate client.
     *
     * @param ex the parse failure
     * @return 400 with a generic message
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("Unreadable request body: {}", ex.getMessage());
        return build(ErrorCode.MALFORMED_REQUEST, "Request body is malformed or unreadable");
    }

    /**
     * Handles a path variable or query parameter of the wrong type — for
     * example a non-UUID where a UUID is required.
     *
     * @param ex the type mismatch
     * @return 400 naming only the offending parameter
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Parameter type mismatch on '{}'", ex.getName());
        return build(ErrorCode.MALFORMED_REQUEST,
                "Parameter '" + ex.getName() + "' has an invalid format");
    }

    /**
     * Handles a missing required query parameter.
     *
     * @param ex the missing-parameter failure
     * @return 400 naming the required parameter
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingParameter(MissingServletRequestParameterException ex) {
        log.warn("Missing required parameter '{}'", ex.getParameterName());
        return build(ErrorCode.MALFORMED_REQUEST,
                "Required parameter '" + ex.getParameterName() + "' is missing");
    }

    /**
     * Handles uploads exceeding the configured multipart limit.
     *
     * <p>The limit is enforced by the servlet container before the controller
     * runs, so this fires even though no application code has executed — which
     * is the point. FR-RES-01's 5 MB cap should not depend on a check that a
     * future controller might forget.
     *
     * @param ex the size violation
     * @return 413
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("Upload rejected: exceeds configured size limit");
        return build(ErrorCode.PAYLOAD_TOO_LARGE, ErrorCode.PAYLOAD_TOO_LARGE.getDefaultMessage());
    }

    /**
     * Handles an unsupported {@code Content-Type} on the request.
     *
     * @param ex the media-type failure
     * @return 415
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        log.warn("Unsupported media type: {}", ex.getContentType());
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getDefaultMessage());
    }

    /**
     * Handles a database constraint violation that reached the persistence layer
     * — most often a unique-index conflict.
     *
     * <p>The database message is logged and never returned: it names the table,
     * the column, and the index, which is a schema disclosure. The client is
     * told only that a conflict occurred.
     *
     * <p>Reaching this handler usually means an application-level pre-check is
     * missing. It is the safety net, not the intended path — a duplicate email
     * should be caught by a lookup in {@code AuthService} and surfaced as a
     * clear {@code CONFLICT}, with this handler catching only the genuine race.
     *
     * @param ex the integrity violation
     * @return 409 with a generic message
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("Database constraint violated — this usually indicates a missing application-level check", ex);
        return build(ErrorCode.CONFLICT, "This operation conflicts with existing data");
    }

    /**
     * Handles requests for static resources or unmapped paths.
     *
     * <p>Logged at DEBUG rather than WARN: on a public API, scanners requesting
     * {@code /.env} and {@code /wp-admin} are constant background noise, and
     * logging each at WARN buries real warnings.
     *
     * @param ex the unmapped-path failure
     * @return 404
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResource(NoResourceFoundException ex) {
        log.debug("No handler for path: {}", ex.getResourcePath());
        return build(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getDefaultMessage());
    }

    /**
     * Last-resort handler for anything unanticipated.
     *
     * <p>Logged at ERROR <em>with</em> the full stack trace, because reaching
     * here means a defect that needs investigating. The response contains a
     * fixed generic message and the correlation ID — nothing else. Every
     * production incident starts as a line in this log, so it must carry
     * everything, and the response must carry nothing.
     *
     * @param ex the unhandled exception
     * @return 500 with a generic message and the correlation ID
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getDefaultMessage());
    }

    // --- helpers -----------------------------------------------------------

    private ResponseEntity<ApiResponse<Object>> build(ErrorCode code, String message) {
        ErrorResponse body = ErrorResponse.of(code, message, traceId());
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.error(body));
    }

    /**
     * Reads the correlation ID that {@link CorrelationIdFilter} placed in the
     * logging context for this request.
     *
     * @return the correlation ID, or {@code null} if the filter did not run
     *         (which happens only for failures raised before the filter chain)
     */
    private String traceId() {
        return MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY);
    }
}
