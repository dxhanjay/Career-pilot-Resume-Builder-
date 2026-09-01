package com.careerpilot.ats.domain;

/**
 * How much a finding costs, and how loudly to say it.
 *
 * <p>{@link #PASS} is a severity rather than an absence. A report consisting
 * only of failures reads as an accusation and gets closed; naming what already
 * works is what makes the criticism credible.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public enum AtsSeverity {

    /** The resume is likely to be discarded or scrambled before a human sees it. */
    CRITICAL(4),

    /** A real, common reason for rejection. Fix before applying again. */
    HIGH(3),

    /** Costs credibility or matches. Worth an evening. */
    MEDIUM(2),

    /** Polish. Fix when the rest is done. */
    LOW(1),

    /** Already correct. Shown so the report is honest about what is working. */
    PASS(0);

    private final int rank;

    AtsSeverity(int rank) {
        this.rank = rank;
    }

    /** Higher is more urgent. Drives the order findings are presented in. */
    public int rank() {
        return rank;
    }

    public boolean isProblem() {
        return this != PASS;
    }
}
