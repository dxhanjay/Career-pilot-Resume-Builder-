package com.careerpilot.matching.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request bodies for the job-description endpoints.
 *
 * <p>Grouped in one file because they are a single cohesive contract; splitting
 * six-line records across six files buys nothing.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class JobDescriptionRequests {

    private JobDescriptionRequests() {
    }

    /**
     * @param rawText the posting as pasted. The lower bound matches the CHECK
     *                constraint in V8: forty characters is not a job description,
     *                and matching against one produces a confident, meaningless
     *                score.
     */
    @Schema(name = "CreateJobDescriptionRequest")
    public record Create(
            @NotBlank(message = "A job title is required")
            @Size(max = 200, message = "Title must be at most 200 characters")
            String title,

            @Size(max = 200, message = "Company must be at most 200 characters")
            String company,

            @Size(max = 150, message = "Location must be at most 150 characters")
            String location,

            @Size(max = 1000, message = "Source URL must be at most 1000 characters")
            String sourceUrl,

            @NotBlank(message = "Paste the job description text")
            @Size(min = 40, max = 40000,
                    message = "The job description must be between 40 and 40000 characters")
            String rawText
    ) {
    }

    /** Every field optional; null means "leave as it is". */
    @Schema(name = "UpdateJobDescriptionRequest")
    public record Update(
            @Size(max = 200, message = "Title must be at most 200 characters")
            String title,

            @Size(max = 200, message = "Company must be at most 200 characters")
            String company,

            @Size(max = 150, message = "Location must be at most 150 characters")
            String location,

            @Size(max = 1000, message = "Source URL must be at most 1000 characters")
            String sourceUrl,

            @Size(min = 40, max = 40000,
                    message = "The job description must be between 40 and 40000 characters")
            String rawText
    ) {
    }

    @Schema(name = "RunMatchRequest")
    public record RunMatch(
            @NotNull(message = "Choose a resume to match against")
            UUID resumeId
    ) {
    }
}
