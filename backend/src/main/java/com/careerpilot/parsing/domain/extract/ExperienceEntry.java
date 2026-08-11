package com.careerpilot.parsing.domain.extract;

import java.time.LocalDate;

/**
 * One job or internship found in a resume's experience section.
 *
 * <p>{@code description} holds the achievement bullets verbatim. It is the
 * input to {@code FR-JD-05}'s rewriting, and the reason it is stored unaltered
 * is {@code PRD §7.2}: a rewrite has to be diffable against what the candidate
 * actually wrote, or there is no way to detect that the model invented a
 * responsibility they never had.
 *
 * @param company     employer name
 * @param jobTitle    role as written
 * @param startDate   first day of the start period
 * @param endDate     first day of the end period, {@code null} when current
 * @param current     whether the role is ongoing
 * @param description the achievement bullets, newline-joined and unaltered
 * @param confidence  0–100
 * @param lineStart   first line of the entry
 * @param lineEnd     last line of the entry
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public record ExperienceEntry(
        String company,
        String jobTitle,
        LocalDate startDate,
        LocalDate endDate,
        boolean current,
        String description,
        int confidence,
        int lineStart,
        int lineEnd
) {

    /**
     * @return {@code true} if nothing identifiable was extracted
     */
    public boolean isEmpty() {
        return company == null && jobTitle == null && startDate == null
                && endDate == null && description == null;
    }
}
