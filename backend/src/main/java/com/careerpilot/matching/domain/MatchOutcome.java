package com.careerpilot.matching.domain;

import com.careerpilot.parsing.domain.extract.SkillCategory;

import java.util.List;

/**
 * The result of comparing one resume against one posting.
 *
 * <p>Pure data produced by {@link MatchEngine}. Everything a client renders —
 * the percentage, the ranked gaps, the suggestions — is here, and every element
 * of it carries the text on both sides that produced it.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public record MatchOutcome(
        int overallScore,
        MatchBand band,
        int requiredSkillScore,
        int optionalSkillScore,
        int titleScore,
        int experienceScore,
        List<SkillComparison> skills,
        List<Suggestion> suggestions,
        String rubricVersion
) {

    public MatchOutcome {
        skills = skills == null ? List.of() : List.copyOf(skills);
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }

    public List<SkillComparison> matched() {
        return skills.stream().filter(s -> s.verdict() == SkillVerdict.MATCHED).toList();
    }

    public List<SkillComparison> missing() {
        return skills.stream().filter(s -> s.verdict() == SkillVerdict.MISSING).toList();
    }

    public List<SkillComparison> extra() {
        return skills.stream().filter(s -> s.verdict() == SkillVerdict.EXTRA).toList();
    }

    /**
     * One skill, seen from both documents.
     *
     * @param resumeEvidence the resume line that showed it, or null when missing
     * @param jdEvidence     the posting line that asked for it, or null when extra
     * @param priority       ranking weight; higher gaps are worth closing first
     */
    public record SkillComparison(
            String normalizedName,
            String displayName,
            SkillCategory category,
            SkillVerdict verdict,
            boolean required,
            int priority,
            String resumeEvidence,
            Integer resumeLine,
            String jdEvidence,
            Integer jdLine
    ) {
    }

    /**
     * A grounded improvement suggestion.
     *
     * <p>Standing commitment 2: the system improves how the truth is expressed
     * and never invents experience. Every suggestion therefore quotes text that
     * already exists in the resume ({@code before}) and proposes a rewrite of
     * <em>that text</em> ({@code after}), with any figure the candidate must
     * supply left as an explicit placeholder rather than guessed at.
     *
     * @param kind     what class of change this is, safe to branch on
     * @param before   the candidate's own words, quoted, or null for an addition
     * @param after    the proposed wording, placeholders included
     * @param rationale why this helps against this particular posting
     */
    public record Suggestion(
            String kind,
            String title,
            String rationale,
            String before,
            String after,
            Integer line
    ) {
        public static final String KIND_SURFACE_SKILL = "SURFACE_SKILL";
        public static final String KIND_REPHRASE = "REPHRASE";
        public static final String KIND_QUANTIFY = "QUANTIFY";
        public static final String KIND_MIRROR_TITLE = "MIRROR_TITLE";
        public static final String KIND_LEARN = "LEARN";
    }
}
