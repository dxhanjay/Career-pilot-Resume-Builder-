package com.careerpilot.ats.application.dto;

import com.careerpilot.ats.domain.AtsAnalysis;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The score over time for one resume, oldest first, plus the movement between
 * the first and latest run.
 *
 * <p>This is the closing beat of the product loop — "fix it and watch the score
 * move" — so the delta is computed on the server rather than left to each client
 * to derive differently.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Schema(name = "AtsHistoryResponse", description = "Score history for one resume")
public record AtsHistoryResponse(
        UUID resumeId,
        List<Point> points,
        int firstScore,
        int latestScore,
        int delta
) {

    public record Point(
            UUID analysisId,
            int overallScore,
            String band,
            int parseability,
            int structure,
            int content,
            int skills,
            int contact,
            Instant createdAt
    ) {
        public static Point from(AtsAnalysis analysis) {
            return new Point(
                    analysis.getId(),
                    analysis.getOverallScore(),
                    analysis.getBand().name(),
                    analysis.getParseabilityScore(),
                    analysis.getStructureScore(),
                    analysis.getContentScore(),
                    analysis.getSkillsScore(),
                    analysis.getContactScore(),
                    analysis.getCreatedAt());
        }
    }

    /**
     * @param analyses newest first, as the repository returns them
     */
    public static AtsHistoryResponse of(UUID resumeId, List<AtsAnalysis> analyses) {
        if (analyses.isEmpty()) {
            return new AtsHistoryResponse(resumeId, List.of(), 0, 0, 0);
        }
        List<AtsAnalysis> chronological = analyses.reversed();
        int first = chronological.get(0).getOverallScore();
        int latest = chronological.get(chronological.size() - 1).getOverallScore();
        return new AtsHistoryResponse(
                resumeId,
                chronological.stream().map(Point::from).toList(),
                first,
                latest,
                latest - first);
    }
}
