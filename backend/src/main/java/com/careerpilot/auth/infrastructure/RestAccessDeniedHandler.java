package com.careerpilot.auth.infrastructure;

import com.careerpilot.common.dto.ApiResponse;
import com.careerpilot.common.exception.ErrorCode;
import com.careerpilot.common.exception.ErrorResponse;
import com.careerpilot.common.web.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns 403 in the application's response envelope.
 *
 * <p>The 403 counterpart to {@link RestAuthenticationEntryPoint}: this fires
 * when the caller <em>is</em> authenticated but lacks the required authority —
 * a standard user reaching an admin endpoint, for instance.
 *
 * <p>Logged at WARN, unlike a 401. An unauthenticated request is ordinary
 * background noise on a public API; an authenticated user attempting something
 * they are not entitled to is either a client bug or someone probing, and both
 * are worth seeing.
 *
 * <p>Note this handles <em>role</em> failures only. Attempting to read another
 * user's resource does not reach here — those return 404 from the service layer,
 * because distinguishing "exists but forbidden" from "does not exist" would
 * confirm the existence of other users' data. See {@code ResourceNotFoundException}.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(RestAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        log.warn("Access denied to {} {}", request.getMethod(), request.getRequestURI());

        ErrorResponse error = ErrorResponse.of(
                ErrorCode.FORBIDDEN,
                ErrorCode.FORBIDDEN.getDefaultMessage(),
                MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY));

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(error));
    }
}
