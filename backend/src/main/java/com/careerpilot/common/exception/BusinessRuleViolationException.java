package com.careerpilot.common.exception;

/**
 * Raised when a request is well-formed and authorised, but a domain rule
 * forbids it in the current state.
 *
 * <p>Examples this will carry in later phases: requesting an ATS analysis for a
 * resume whose parse failed; fetching an interview report for a session still
 * in progress; uploading a file whose checksum matches an existing resume.
 *
 * <p>Maps to 409 Conflict, which is the accurate status — the request is not
 * malformed (400), not unauthorised (401/403), and the resource does exist
 * (not 404). It conflicts with state. Returning 400 for this class of failure
 * is common and misleading: it tells the client to fix its request when the
 * request was fine.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public class BusinessRuleViolationException extends ApiException {

    /**
     * @param message client-safe explanation of which rule was violated and why
     */
    public BusinessRuleViolationException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}
