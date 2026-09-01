package com.careerpilot.matching.application.dto;

import com.careerpilot.matching.domain.JdMatch;
import com.careerpilot.matching.domain.JdMatchSkill;
import com.careerpilot.matching.domain.MatchOutcome;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A match report: the percentage, what it was built from, the ranked gaps, and
 * the grounded suggestions.
 *
 * <p>Suggestions are computed rather than stored. They are a pure function of
 * the resume and the posting, both of which are already persisted, so a table
 * for them would only add a way for the two to disagree.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "MatchResponse", description = "Resume-to-posting match with ranked skill gaps")
public record MatchResponse(
        UUID id,
        UUID jobDescriptionId,
        UUID resumeId,
        String jobTitle,
        String company,
        int overallScore,
        String band,
        String bandLabel,
        String advice,
        int requiredSkillScore,
        int optionalSkillScore,
        int titleScore,
        int experienceScore,
        int matchedCount,
        int missingCount,
        List<SkillLine> matched,
        List<SkillLine> missing,
        List<SkillLine> extra,
        List<Suggestion> suggestions,
        String rubricVersion,
        Integer durationMs,
        Instant createdAt
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SkillLine(
            String name,
            String normalizedName,
            String category,
            boolean required,
            int priority,
            String resumeEvidence,
            Integer resumeLine,
            String jdEvidence,
            Integer jdLine
    ) {
        static SkillLine from(JdMatchSkill skill) {
            return new SkillLine(
                    skill.getDisplayName(),
                    skill.getNormalizedName(),
                    skill.getCategory().name(),
                    skill.isRequired(),
                    skill.getPriority(),
                    skill.getResumeEvidence(),
                    skill.getResumeLine(),
                    skill.getJdEvidence(),
                    skill.getJdLine());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Suggestion(
            String kind,
            String title,
            String rationale,
            String before,
            String after,
            Integer line
    ) {
        static Suggestion from(MatchOutcome.Suggestion suggestion) {
            return new Suggestion(
                    suggestion.kind(),
                    suggestion.title(),
                    suggestion.rationale(),
                    suggestion.before(),
                    suggestion.after(),
                    suggestion.line());
        }
    }

    public static MatchResponse of(JdMatch match,
                                   List<JdMatchSkill> skills,
                                   List<MatchOutcome.Suggestion> suggestions,
                                   String jobTitle,
                                   String company) {
        return new MatchResponse(
                match.getId(),
                match.getJobDescriptionId(),
                match.getResumeId(),
                jobTitle,
                company,
                match.getOverallScore(),
                match.getBand().name(),
                match.getBand().displayName(),
                match.getBand().advice(),
                match.getRequiredSkillScore(),
                match.getOptionalSkillScore(),
                match.getTitleScore(),
                match.getExperienceScore(),
                match.getMatchedCount(),
                match.getMissingCount(),
                filter(skills, com.careerpilot.matching.domain.SkillVerdict.MATCHED),
                filter(skills, com.careerpilot.matching.domain.SkillVerdict.MISSING),
                filter(skills, com.careerpilot.matching.domain.SkillVerdict.EXTRA),
                suggestions.stream().map(Suggestion::from).toList(),
                match.getRubricVersion(),
                match.getDurationMs(),
                match.getCreatedAt());
    }

    private static List<SkillLine> filter(List<JdMatchSkill> skills,
                                          com.careerpilot.matching.domain.SkillVerdict verdict) {
        return skills.stream()
                .filter(skill -> skill.getStatus() == verdict)
                .map(SkillLine::from)
                .toList();
    }
}
