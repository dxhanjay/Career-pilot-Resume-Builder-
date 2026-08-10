package com.careerpilot.parsing.domain.section;

/**
 * A section a resume can be divided into.
 *
 * <p>The set is deliberately closed. An open-ended set of section names would
 * push the problem downstream: the ATS analyser would have to decide at runtime
 * whether "Career Highlights" is an achievements section, and it would decide
 * differently on different resumes. A closed set means a heading either maps to
 * a known type or is reported as unrecognised, and unrecognised is a signal we
 * can act on rather than a silent guess.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public enum SectionType {

    /** The block above the first heading: name, email, phone, links. */
    CONTACT("Contact", true),

    /** Summary, objective, profile, or personal statement. */
    SUMMARY("Summary", false),

    /** Degrees, institutions, and academic history. */
    EDUCATION("Education", true),

    /** Jobs, internships, and employment history. */
    EXPERIENCE("Experience", true),

    /** Technical and professional skills. */
    SKILLS("Skills", true),

    /** Academic, personal, or professional projects. */
    PROJECTS("Projects", false),

    /** Certifications, licences, and completed courses. */
    CERTIFICATIONS("Certifications", false),

    /** Awards, honours, and accomplishments. */
    ACHIEVEMENTS("Achievements", false),

    /** Spoken and written languages. */
    LANGUAGES("Languages", false),

    /** Papers, research, and published work. */
    PUBLICATIONS("Publications", false),

    /** Hobbies, volunteering, and extracurricular activities. */
    INTERESTS("Interests", false),

    /** Referees, or a note that references are available. */
    REFERENCES("References", false),

    /**
     * Content that belongs to no recognised heading.
     *
     * <p>Produced when a resume has no detectable headings at all. Extraction
     * still runs across it — a skills lexicon finds skills wherever they are —
     * but with the confidence that an unstructured document deserves.
     */
    UNKNOWN("Unrecognised", false);

    private final String displayName;
    private final boolean core;

    SectionType(String displayName, boolean core) {
        this.displayName = displayName;
        this.core = core;
    }

    /**
     * @return a label safe to show a user
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Whether a resume missing this section has a real problem.
     *
     * <p>Drives the "missing information" finding in the ATS analyser. A resume
     * with no projects section is a stylistic choice; a resume with no
     * experience section that a screener can locate is a scoring problem.
     *
     * @return {@code true} if the section is expected on every resume
     */
    public boolean isCore() {
        return core;
    }
}
