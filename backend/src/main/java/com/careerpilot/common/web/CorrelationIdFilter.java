package com.careerpilot.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns every request a correlation ID, publishes it to the logging context,
 * and echoes it back to the caller.
 *
 * <p>This is the mechanism behind NFR-OBS-01, and it is the difference between
 * a support ticket that can be investigated and one that cannot. Without it,
 * diagnosing "my upload failed around 3pm" means grepping a log stream shared
 * by every concurrent user and guessing which interleaved lines belong
 * together. With it, the user quotes one identifier and every log line for that
 * request — across filters, controller, service, repository, and the async job
 * it spawned — is a single query.
 *
 * <p>An inbound {@code X-Correlation-Id} is honoured so that a trace begun by
 * the frontend, or by an upstream proxy, continues through the backend instead
 * of restarting. A generated value is used otherwise.
 *
 * <p><strong>The {@code finally} block is not optional.</strong> Tomcat reuses
 * worker threads, and {@link MDC} is thread-local. Failing to clear it leaves
 * the previous request's correlation ID attached to the next request handled by
 * that thread — which produces logs that are not merely unhelpful but actively
 * misleading, attributing one user's activity to another's trace.
 *
 * <p>Ordered {@link Ordered#HIGHEST_PRECEDENCE} so that it wraps everything,
 * including the security filter chain added in Phase 3. An authentication
 * failure is exactly the kind of event that needs a correlation ID.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** MDC key; referenced in the log pattern in {@code application.yml}. */
    public static final String CORRELATION_ID_KEY = "correlationId";

    /** Request and response header carrying the identifier. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String correlationId = resolveCorrelationId(request);

        try {
            MDC.put(CORRELATION_ID_KEY, correlationId);
            // Set before the chain runs: if a downstream filter or handler
            // commits the response, a header added afterwards is discarded.
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_KEY);
        }
    }

    /**
     * Uses the inbound correlation ID when present and plausible, otherwise
     * generates one.
     *
     * <p>The length check matters: this value is written into every log line for
     * the request, and the header is entirely caller-controlled. Accepting an
     * unbounded string would let a caller inject megabytes into the log stream,
     * or embed newlines to forge log entries. Bounding the length and generating
     * a fresh ID when it is exceeded removes both.
     *
     * @param request the inbound request
     * @return a correlation ID safe to log
     */
    private String resolveCorrelationId(HttpServletRequest request) {
        String provided = request.getHeader(CORRELATION_ID_HEADER);
        if (StringUtils.hasText(provided) && provided.length() <= 64 && isSafe(provided)) {
            return provided;
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Rejects anything outside a conservative identifier alphabet, which
     * excludes the CR and LF characters used for log-injection.
     *
     * @param value the candidate correlation ID
     * @return {@code true} if the value is safe to write to a log line
     */
    private boolean isSafe(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = Character.isLetterOrDigit(c) || c == '-' || c == '_';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}
