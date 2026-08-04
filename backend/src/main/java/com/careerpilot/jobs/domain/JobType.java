package com.careerpilot.jobs.domain;

/**
 * The kinds of work the job engine runs.
 *
 * <p>Each value also determines how {@code Job.referenceId} is interpreted,
 * since that column is a polymorphic reference with no foreign key. Only
 * {@link #PARSE_RESUME} is implemented in Phase 6; the rest are declared now so
 * the CHECK constraint and the type→handler mapping are settled in one place
 * rather than accumulating.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public enum JobType {

    /** {@code referenceId} is a resume id. Extracts text and structure. */
    PARSE_RESUME,

    /** {@code referenceId} is a resume id. Produces an ATS analysis. Phase 7. */
    ANALYZE_ATS,

    /** {@code referenceId} is a job-description id. Produces a match. Phase 9. */
    MATCH_JD,

    /** {@code referenceId} is an interview answer id. Produces an evaluation. Phase 10. */
    EVALUATE_INTERVIEW
}
