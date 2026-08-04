package com.careerpilot.common.exception;

import com.careerpilot.common.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 *
 * <p>No Spring context. The handler is a plain object with plain methods, so
 * testing it directly runs in milliseconds and can be part of the fast suite
 * developers actually run on every save. Loading a web context to assert a
 * status code would be twenty times slower for no additional confidence.
 *
 * <p>The assertions here fall into two groups, and the second matters more.
 * Mapping tests check that an exception produces the right status. <em>Leakage</em>
 * tests check that the response body contains nothing it should not — no
 * database identifiers, no stack traces, no internal messages. That is NFR-SEC-05,
 * and it is the kind of property that silently regresses the day someone adds a
 * helpful {@code ex.getMessage()} to a handler while debugging.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Nested
    @DisplayName("maps exceptions to the correct status")
    class StatusMapping {

        @Test
        @DisplayName("ResourceNotFoundException → 404")
        void resourceNotFound() {
            ResponseEntity<ApiResponse<Object>> response =
                    handler.handleApiException(new ResourceNotFoundException("Resume"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(errorOf(response).code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.name());
        }

        @Test
        @DisplayName("BusinessRuleViolationException → 409")
        void businessRuleViolation() {
            ResponseEntity<ApiResponse<Object>> response =
                    handler.handleApiException(
                            new BusinessRuleViolationException("Resume has not been parsed yet"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("AI quota exhaustion → 402, not 429")
        void quotaExceeded() {
            ResponseEntity<ApiResponse<Object>> response =
                    handler.handleApiException(new ApiException(ErrorCode.AI_QUOTA_EXCEEDED));

            // 402 is deliberate. 429 would tell the client that retrying shortly
            // helps, and it does not — the allowance resets monthly.
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
        }

        @Test
        @DisplayName("oversized upload → 413")
        void uploadTooLarge() {
            ResponseEntity<ApiResponse<Object>> response =
                    handler.handleUploadTooLarge(new MaxUploadSizeExceededException(5_242_880L));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        }

        @Test
        @DisplayName("unhandled exception → 500")
        void unexpected() {
            ResponseEntity<ApiResponse<Object>> response =
                    handler.handleUnexpected(new IllegalStateException("boom"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Nested
    @DisplayName("does not leak internal detail (NFR-SEC-05)")
    class NoLeakage {

        @Test
        @DisplayName("a 500 response never carries the exception message")
        void unexpectedErrorHidesMessage() {
            String secret = "Connection to db-prod-7.internal:5432 refused for user careerpilot_rw";

            ResponseEntity<ApiResponse<Object>> response =
                    handler.handleUnexpected(new IllegalStateException(secret));

            ErrorResponse error = errorOf(response);

            assertThat(error.message())
                    .as("the client receives the generic message, never the cause")
                    .isEqualTo(ErrorCode.INTERNAL_ERROR.getDefaultMessage())
                    .doesNotContain("db-prod-7", "5432", "careerpilot_rw");
        }

        @Test
        @DisplayName("a constraint violation never carries the database message")
        void dataIntegrityHidesDatabaseDetail() {
            String dbMessage =
                    "ERROR: duplicate key value violates unique constraint \"ux_users_email_lower\"";

            ResponseEntity<ApiResponse<Object>> response =
                    handler.handleDataIntegrity(new DataIntegrityViolationException(dbMessage));

            assertThat(errorOf(response).message())
                    .as("index and column names are schema disclosure")
                    .doesNotContain("ux_users_email_lower", "unique constraint");
        }

        @Test
        @DisplayName("'not found' names the resource type but never the identifier")
        void notFoundRevealsNothingAboutOwnership() {
            ResponseEntity<ApiResponse<Object>> response =
                    handler.handleApiException(new ResourceNotFoundException("Resume"));

            // The same 404 must be returned whether the resource does not exist
            // or belongs to another user. Any difference between the two —
            // including a message that echoes an identifier — reintroduces the
            // enumeration oracle the 404-not-403 rule exists to close.
            assertThat(errorOf(response).message()).isEqualTo("Resume not found");
        }
    }

    @Nested
    @DisplayName("response envelope")
    class Envelope {

        @Test
        @DisplayName("marks the response unsuccessful and stamps a timestamp")
        void envelopeShape() {
            ResponseEntity<ApiResponse<Object>> response =
                    handler.handleApiException(new ResourceNotFoundException("Resume"));

            ApiResponse<Object> body = response.getBody();

            assertThat(body).isNotNull();
            assertThat(body.success()).isFalse();
            assertThat(body.timestamp()).isNotNull();
            assertThat(body.data()).isInstanceOf(ErrorResponse.class);
        }

        @Test
        @DisplayName("carries a stable machine-readable code, not just prose")
        void carriesStableCode() {
            ResponseEntity<ApiResponse<Object>> response =
                    handler.handleApiException(new ApiException(ErrorCode.EMAIL_NOT_VERIFIED));

            // Clients branch on this. If it ever changes, that is a breaking API
            // change and this test is the thing that says so out loud.
            assertThat(errorOf(response).code()).isEqualTo("EMAIL_NOT_VERIFIED");
        }
    }

    // --- helpers -----------------------------------------------------------

    private ErrorResponse errorOf(ResponseEntity<ApiResponse<Object>> response) {
        ApiResponse<Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.data()).isInstanceOf(ErrorResponse.class);
        return (ErrorResponse) body.data();
    }
}
