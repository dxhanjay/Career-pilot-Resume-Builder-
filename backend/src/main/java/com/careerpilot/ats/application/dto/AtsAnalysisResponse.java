package com.careerpilot.ats.application.dto;

import com.careerpilot.ats.domain.AtsAnalysis;
import com.careerpilot.ats.domain.AtsCategory;
import com.careerpilot.ats.domain.AtsFinding;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An ATS report as the client sees it.
 *
 * <p>Carries the rubric's own description of each category alongside the score,
 * so the frontend never has to hardcode an explanation of what "Parseability"
 * means and the two can never disagree.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AtsAnalysisResponse", description = "ATS score with the evidence behind it")
public record AtsAnalysisResponse(
        UUID id,
        UUID resumeId,
        UUID parseId,
        int overallScore,
        String band,
        String bandLabel,
        String bandSummary,
        List<CategoryScore> categories,
        List<Finding> findings,
        int problemCount,
        int passCount,
        String rubricVersion,
        Integer durationMs,
        Instant createdAt
) {

    public record CategoryScore(
            String category,
            String displayName,
            String description,
            int score,
            int weight
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Finding(
            UUID id,
            String code,
            String category,
            String categoryLabel,
            String severity,
            String title,
            String detail,
            String recommendation,
            String evidence,
            Integer lineStart,
            Integer lineEnd,
            int pointsLost
    ) {
        public static Finding from(AtsFinding finding) {
            return new Finding(
                    finding.getId(),
                    finding.getCode(),
                    finding.getCategory().name(),
                    finding.getCategory().displayName(),
                    finding.getSeverity().name(),
                    finding.getTitle(),
                    finding.getDetail(),
                    finding.getRecommendation(),
                    finding.getEvidence(),
                    finding.getEvidenceLineStart(),
                    finding.getEvidenceLineEnd(),
                    finding.getPointsLost());
        }
    }

    public static AtsAnalysisResponse from(AtsAnalysis analysis, List<AtsFinding> findings) {
        List<Finding> mapped = findings.stream().map(Finding::from).toList();

        List<CategoryScore> categories = List.of(
                category(AtsCategory.PARSEABILITY, analysis.getParseabilityScore()),
                category(AtsCategory.STRUCTURE, analysis.getStructureScore()),
                category(AtsCategory.CONTENT, analysis.getContentScore()),
                category(AtsCategory.SKILLS, analysis.getSkillsScore()),
                category(AtsCategory.CONTACT, analysis.getContactScore()));

        int passes = (int) findings.stream()
                .filter(finding -> !finding.getSeverity().isProblem()).count();

        return new AtsAnalysisResponse(
                analysis.getId(),
                analysis.getResumeId(),
                analysis.getParseId(),
                analysis.getOverallScore(),
                analysis.getBand().name(),
                analysis.getBand().displayName(),
                analysis.getBand().summary(),
                categories,
                mapped,
                mapped.size() - passes,
                passes,
                analysis.getRubricVersion(),
                analysis.getDurationMs(),
                analysis.getCreatedAt());
    }

    private static CategoryScore category(AtsCategory category, short score) {
        return new CategoryScore(category.name(), category.displayName(),
                category.description(), score, category.weight());
    }
}
