package com.careerpilot.parsing.domain;

/**
 * Outcome of a single parse attempt.
 *
 * <p>Only two values, and no {@code PARTIAL}. A parse that produced text but too
 * little to analyse is recorded as {@link #FAILED} with a warning explaining
 * why — because from the user's point of view the outcome is the same (nothing
 * downstream can run) and a third state would force every consumer to decide
 * what "partial" means to them.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public enum ParseStatus {

    /** Usable text was extracted. */
    SUCCEEDED,

    /** No usable text: the document could not be read, or contained none. */
    FAILED
}
