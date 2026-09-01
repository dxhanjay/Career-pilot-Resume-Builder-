package com.careerpilot.ats.domain;

/**
 * The five things an applicant tracking system does to a resume, in the order
 * it does them.
 *
 * <p>The weights are the rubric. They are published deliberately (ADR-0053): a
 * score whose derivation is secret cannot be argued with, and a student who
 * cannot argue with a score cannot learn anything from it.
 *
 * <p>Parseability carries the most weight because it is the only category that
 * can zero the others. Content that a screener never sees scores nothing,
 * however well written it is.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public enum AtsCategory {

    /** Can the machine read the file at all, and in the right order? */
    PARSEABILITY("Parseability", 30,
            "Whether screening software can read the file, and read it in the intended order."),

    /** Are the expected sections present and correctly labelled? */
    STRUCTURE("Structure", 20,
            "Whether the expected sections exist and are labelled in a way a parser recognises."),

    /** Is the writing evidence-bearing, or a list of duties? */
    CONTENT("Content", 25,
            "Whether bullets describe measurable results rather than assigned duties."),

    /** Is there enough concrete, matchable skill vocabulary? */
    SKILLS("Skills", 15,
            "Whether concrete, matchable technical vocabulary is present and varied."),

    /** Can a recruiter who wants to reply actually do so? */
    CONTACT("Contact", 10,
            "Whether a recruiter who decides to reply can find a way to.");

    private final String displayName;
    private final int weight;
    private final String description;

    AtsCategory(String displayName, int weight, String description) {
        this.displayName = displayName;
        this.weight = weight;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    /** Share of the 100-point overall score. The five weights sum to 100. */
    public int weight() {
        return weight;
    }

    public String description() {
        return description;
    }
}
