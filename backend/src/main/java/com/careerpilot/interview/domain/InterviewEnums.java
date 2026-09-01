package com.careerpilot.interview.domain;

/**
 * Holder for the interview module's small enumerations.
 *
 * <p>Not a class anyone instantiates — it exists so four short, tightly related
 * enums live in one readable file rather than four almost-empty ones.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class InterviewEnums {

    private InterviewEnums() {
    }

    /** What the session is for. Drives which questions get generated. */
    public enum Focus {

        GENERAL("General practice",
                "A spread across your background, your projects, and how you work."),

        RESUME_DEEP_DIVE("Resume deep dive",
                "Questions drawn from what is actually on your resume — the ones an "
                        + "interviewer who read it would ask."),

        JOB_SPECIFIC("Targeted at a posting",
                "Built around the gaps between your resume and one job description, "
                        + "including the ones you would rather not be asked about."),

        BEHAVIOURAL("Behavioural",
                "Situation questions about conflict, failure, ownership, and working "
                        + "with other people.");

        private final String displayName;
        private final String description;

        Focus(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String displayName() {
            return displayName;
        }

        public String description() {
            return description;
        }
    }

    /** Where a session is in its life. */
    public enum SessionStatus {
        IN_PROGRESS,
        COMPLETED,
        ABANDONED;

        public boolean isOpen() {
            return this == IN_PROGRESS;
        }
    }

    /** What kind of question this is, so the UI can group and label them. */
    public enum QuestionKind {

        TECHNICAL("Technical"),
        BEHAVIOURAL("Behavioural"),
        GAP_PROBE("Gap"),
        PROJECT_DEEP_DIVE("Project"),
        EXPERIENCE_PROBE("Experience"),
        MOTIVATION("Motivation");

        private final String displayName;

        QuestionKind(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /**
     * A coarse verdict on a session or a single answer.
     *
     * <p>Bands rather than decimals, for the same reason the ATS score uses
     * them: the rubric is not precise enough to justify a decimal, and a
     * candidate chasing one point is not practising.
     */
    public enum PerformanceBand {

        NEEDS_WORK("Needs work",
                "Answers are too thin for an interviewer to judge you on. Add the "
                        + "specifics."),
        DEVELOPING("Developing",
                "The material is there; the structure is not. Say what you did before "
                        + "you say what you learned."),
        SOLID("Solid",
                "Clear, structured answers. Sharpen the results and this is interview-ready."),
        STRONG("Strong",
                "Structured, specific, and measurable. This is what a good interview "
                        + "sounds like.");

        private final String displayName;
        private final String summary;

        PerformanceBand(String displayName, String summary) {
            this.displayName = displayName;
            this.summary = summary;
        }

        public String displayName() {
            return displayName;
        }

        public String summary() {
            return summary;
        }

        public static PerformanceBand of(int score) {
            if (score >= 80) {
                return STRONG;
            }
            if (score >= 62) {
                return SOLID;
            }
            if (score >= 40) {
                return DEVELOPING;
            }
            return NEEDS_WORK;
        }
    }
}
