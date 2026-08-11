package com.careerpilot.parsing.domain.extract;

import java.time.LocalDate;

/**
 * One qualification found in a resume's education section.
 *
 * <p>Every field is nullable. A resume that lists only "B.Tech, 2026" yields a
 * degree and an end date and nothing else, and that partial row is more useful
 * than either a rejected entry or one padded with guesses — {@code FR-ATS-03}
 * can then tell the candidate their education section is missing an institution
 * name, which is a finding they can act on.
 *
 * @param institution  university, college, or school
 * @param degree       the qualification as written — "B.Tech", "MBA"
 * @param fieldOfStudy subject, where it is stated separately
 * @param startDate    first day of the start period
 * @param endDate      first day of the end period
 * @param grade        CGPA, GPA, or percentage as written
 * @param confidence   0–100
 * @param lineStart    first line of the entry
 * @param lineEnd      last line of the entry
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public record EducationEntry(
        String institution,
        String degree,
        String fieldOfStudy,
        LocalDate startDate,
        LocalDate endDate,
        String grade,
        int confidence,
        int lineStart,
        int lineEnd
) {

    /**
     * @return {@code true} if nothing identifiable was extracted
     */
    public boolean isEmpty() {
        return institution == null && degree == null && fieldOfStudy == null
                && startDate == null && endDate == null && grade == null;
    }
}
