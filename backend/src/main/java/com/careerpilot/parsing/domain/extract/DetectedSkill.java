package com.careerpilot.parsing.domain.extract;

/**
 * One skill found in a resume.
 *
 * <p>Carries both spellings deliberately. {@code name} is what the candidate
 * wrote and is what gets shown back to them — silently rewriting someone's own
 * resume in the UI reads as a bug, not a feature. {@code normalizedName} is the
 * lexicon's canonical form and is the only thing job matching joins on, so that
 * "ReactJS" on a resume matches "React" in a job description.
 *
 * @param name           verbatim, as written in the resume
 * @param normalizedName canonical lexicon form
 * @param category       what kind of skill this is
 * @param confidence     0–100
 * @param lineStart      first line it was found on
 * @param lineEnd        last line it was found on
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public record DetectedSkill(
        String name,
        String normalizedName,
        SkillCategory category,
        int confidence,
        int lineStart,
        int lineEnd
) {
}
