package com.careerpilot.auth.infrastructure;

import com.careerpilot.common.dto.ApiResponse;
import com.careerpilot.common.exception.ErrorCode;
import com.careerpilot.common.exception.ErrorResponse;
import com.careerpilot.common.web.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns 401 in the application's response envelope.
 *
 * <p>Spring Security's default entry point sends a bare 401 with a
 * {@code WWW-Authenticate} header and an empty body — or, with some starters on
 * the classpath, an HTML error page. A client that parses every response as
 * {@code {success, data, error}} then fails to parse the one response it most
 * needs to understand, and typically surfaces "unexpected error" instead of
 * "please sign in".
 *
 * <p>This exists so that authentication failures are as machine-readable as
 * every other failure, and carry the same {@code traceId} for support.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        ErrorResponse error = ErrorResponse.of(
                ErrorCode.UNAUTHORIZED,
                // Fixed message. The exception's own text distinguishes "no
                // token" from "bad token" from "expired token", which is useful
                // in a log and is free reconnaissance in a response.
                "Authentication is required to access this resource",
                MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY));

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(error));
    }
}
