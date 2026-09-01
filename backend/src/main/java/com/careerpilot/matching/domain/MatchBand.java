package com.careerpilot.matching.domain;

/**
 * A coarse verdict on a resume against one posting.
 *
 * <p>Phrased as advice about whether to apply, because that is the only decision
 * the number is actually feeding. "62%" tells a student nothing; "apply, and
 * close these two gaps first" tells them what to do this afternoon.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public enum MatchBand {

    WEAK("Weak match",
            "This posting asks for a different profile. Applying costs little, but your "
                    + "effort is better spent on roles closer to what you have."),
    PARTIAL("Partial match",
            "Some overlap, several hard gaps. Worth applying only if you can close a gap "
                    + "or two first."),
    PROMISING("Promising match",
            "You clear most of what this asks for. Close the top gaps and this becomes a "
                    + "strong application."),
    STRONG("Strong match",
            "You cover what this posting asks for. Make sure the resume says so in the "
                    + "posting's own words.");

    private final String displayName;
    private final String advice;

    MatchBand(String displayName, String advice) {
        this.displayName = displayName;
        this.advice = advice;
    }

    public String displayName() {
        return displayName;
    }

    public String advice() {
        return advice;
    }

    public static MatchBand of(int score) {
        if (score >= 80) {
            return STRONG;
        }
        if (score >= 60) {
            return PROMISING;
        }
        if (score >= 35) {
            return PARTIAL;
        }
        return WEAK;
    }
}
