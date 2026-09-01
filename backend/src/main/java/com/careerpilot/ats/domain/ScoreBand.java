package com.careerpilot.ats.domain;

/**
 * A coarse label for an overall score.
 *
 * <p>ADR-0049: bands, not decimals. "68.4" implies a precision the rubric does
 * not have, and invites users to chase a point rather than fix a problem. Four
 * bands say the only thing the number honestly supports — roughly where you
 * stand, and whether it is worth applying yet.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public enum ScoreBand {

    NEEDS_WORK("Needs work", "Likely to be filtered out before a human reads it."),
    FAIR("Fair", "Readable, but leaving opportunities on the table."),
    GOOD("Good", "Solid. A few specific fixes away from strong."),
    STRONG("Strong", "Parses cleanly and reads as evidence. Apply.");

    private final String displayName;
    private final String summary;

    ScoreBand(String displayName, String summary) {
        this.displayName = displayName;
        this.summary = summary;
    }

    public String displayName() {
        return displayName;
    }

    public String summary() {
        return summary;
    }

    /**
     * @param score an overall score, 0-100
     * @return the band it falls in
     */
    public static ScoreBand of(int score) {
        if (score >= 85) {
            return STRONG;
        }
        if (score >= 70) {
            return GOOD;
        }
        if (score >= 50) {
            return FAIR;
        }
        return NEEDS_WORK;
    }
}
