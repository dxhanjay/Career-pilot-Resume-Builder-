package com.careerpilot.dashboard.application.dto;

import com.careerpilot.resume.application.dto.ResumeResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything the signed-in home screen needs, in one response.
 *
 * <p>Nullable fields are the normal case, not an error case: a user who has just
 * signed up has no resume, no score, and no matches, and the empty state is a
 * first-class screen rather than a failure.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "DashboardResponse", description = "Home screen summary")
public record DashboardResponse(
        List<ResumeResponse> recentResumes,
        ResumeResponse focusResume,
        ScoreSummary latestScore,
        Counts counts,
        Double averageInterviewScore
) {

    /** The headline ATS number for the user's primary resume. */
    public record ScoreSummary(
            UUID analysisId,
            int overallScore,
            String band,
            String bandLabel,
            String bandSummary,
            int problemCount,
            Instant createdAt
    ) {
    }

    public record Counts(
            long resumes,
            long jobDescriptions,
            long matches,
            long interviews
    ) {
    }
}
