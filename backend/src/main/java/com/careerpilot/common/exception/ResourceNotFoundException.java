package com.careerpilot.common.exception;

/**
 * Raised when a requested resource does not exist, <em>or</em> exists but does
 * not belong to the caller.
 *
 * <p><strong>Both cases produce the same 404 deliberately.</strong> If a resume
 * owned by another user returned 403 while a non-existent one returned 404, the
 * API would answer the question "does this identifier exist?" for anyone willing
 * to iterate. Every resource here is user-owned and addressed by UUID, so that
 * distinction is a slow but real enumeration oracle. Making both cases
 * indistinguishable closes it.
 *
 * <p>The correct implementation is a repository call that includes the owner in
 * the query — {@code findByIdAndUserId(...)} — rather than fetching by ID and
 * then comparing ownership afterwards. A code review can miss a forgotten
 * comparison; a method that does not exist cannot be called by accident.
 *
 * <p>The message deliberately names only the resource type, never the
 * identifier. Echoing a caller-supplied UUID back is harmless but pointless,
 * and echoing an internal one is a leak.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public class ResourceNotFoundException extends ApiException {

    /**
     * @param resourceType human-readable resource name, e.g. {@code "Resume"}
     */
    public ResourceNotFoundException(String resourceType) {
        super(ErrorCode.RESOURCE_NOT_FOUND, resourceType + " not found");
    }
}
