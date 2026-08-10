package com.careerpilot.parsing.domain.section;

/**
 * A contiguous run of lines identified as one section of a resume.
 *
 * <p>Holds line pointers rather than text. The text lives once, in the
 * {@link LineModel}; copying it here would let the two drift and would store a
 * second copy of a person's resume for no benefit.
 *
 * @param type        which section this is
 * @param headingText the heading as written, or {@code null} where there is no
 *                    heading — the contact block above the first heading, and
 *                    the whole-document fallback, both have none
 * @param headingLine index of the heading line, or {@code -1} if there is none
 * @param startLine   first content line, inclusive
 * @param endLine     last content line, inclusive; {@code startLine - 1} when
 *                    the section has a heading and no content under it
 * @param confidence  0–100, how strongly the heading was identified
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public record ResumeSection(
        SectionType type,
        String headingText,
        int headingLine,
        int startLine,
        int endLine,
        int confidence
) {

    /**
     * A section introduced by a detected heading.
     *
     * @param type        the resolved section type
     * @param headingText the heading as written
     * @param headingLine where the heading is
     * @param startLine   first content line
     * @param endLine     last content line
     * @param confidence  the heading score
     * @return the section
     */
    public static ResumeSection withHeading(SectionType type, String headingText, int headingLine,
                                            int startLine, int endLine, int confidence) {
        return new ResumeSection(type, headingText, headingLine, startLine, endLine, confidence);
    }

    /**
     * A section with no heading of its own.
     *
     * <p>Two cases produce this: the contact block that sits above the first
     * heading, and the whole-document fallback when no heading was found at all.
     *
     * @param type       the assumed section type
     * @param startLine  first line
     * @param endLine    last line
     * @param confidence how much to trust the assumption
     * @return the section
     */
    public static ResumeSection headless(SectionType type, int startLine, int endLine,
                                         int confidence) {
        return new ResumeSection(type, null, -1, startLine, endLine, confidence);
    }

    /**
     * @return {@code true} if a heading introduced this section
     */
    public boolean hasHeading() {
        return headingLine >= 0;
    }

    /**
     * @return the number of content lines, excluding the heading
     */
    public int lineCount() {
        return Math.max(0, endLine - startLine + 1);
    }

    /**
     * Whether the section has a heading but nothing underneath it.
     *
     * <p>Worth surfacing to the user: a "SKILLS" heading with no skills under it
     * usually means the content was in a text box or a sidebar the extractor
     * could not read — exactly the failure the product exists to make visible.
     *
     * @return {@code true} if there are no content lines
     */
    public boolean isEmpty() {
        return lineCount() == 0;
    }
}
