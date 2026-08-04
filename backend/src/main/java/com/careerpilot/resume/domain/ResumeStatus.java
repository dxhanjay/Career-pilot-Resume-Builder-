package com.careerpilot.resume.domain;

/**
 * Lifecycle of an uploaded resume.
 *
 * <p>{@code PARSE_FAILED} is a first-class state rather than an error condition.
 * A scanned-image CV with no selectable text is a common real-world upload, and
 * the correct response is an explanation plus a route forward — not a red toast
 * that leaves the user with a resume the product silently ignores.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public enum ResumeStatus {

    /** Stored successfully; parsing not yet started. */
    UPLOADED,

    /** A parse job has claimed this resume. */
    PARSING,

    /** Text and entities extracted successfully. */
    PARSED,

    /**
     * Every parser failed. Usually a scanned image, a corrupt file, or a PDF
     * with no text layer. The user is told what happened and offered the
     * builder as an alternative.
     */
    PARSE_FAILED;

    /**
     * @return whether this resume can be analysed, matched, or used in an
     *         interview — all of which need extracted text
     */
    public boolean isAnalysable() {
        return this == PARSED;
    }

    /**
     * @return whether a parse job may be started for this resume
     */
    public boolean canStartParsing() {
        return this == UPLOADED || this == PARSE_FAILED;
    }
}
