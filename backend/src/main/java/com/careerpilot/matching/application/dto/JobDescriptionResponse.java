package com.careerpilot.matching.application.dto;

import com.careerpilot.matching.domain.JobDescription;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * A saved job posting.
 *
 * <p>{@code rawText} is present only on the detail view. A list of twenty
 * postings, each carrying eight kilobytes of text nobody is reading yet, is a
 * slow list for no benefit.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "JobDescriptionResponse", description = "A saved job posting")
public record JobDescriptionResponse(
        UUID id,
        String title,
        String company,
        String location,
        String sourceUrl,
        String rawText,
        int characterCount,
        Integer latestScore,
        String latestBand,
        Instant latestMatchedAt,
        Instant createdAt,
        Instant updatedAt
) {

    /** Summary form, for lists. */
    public static JobDescriptionResponse summary(JobDescription posting,
                                                 Integer latestScore,
                                                 String latestBand,
                                                 Instant latestMatchedAt) {
        return new JobDescriptionResponse(
                posting.getId(),
                posting.getTitle(),
                posting.getCompany(),
                posting.getLocation(),
                posting.getSourceUrl(),
                null,
                posting.getRawText().length(),
                latestScore,
                latestBand,
                latestMatchedAt,
                posting.getCreatedAt(),
                posting.getUpdatedAt());
    }

    /** Detail form, including the posting text. */
    public static JobDescriptionResponse detail(JobDescription posting,
                                                Integer latestScore,
                                                String latestBand,
                                                Instant latestMatchedAt) {
        return new JobDescriptionResponse(
                posting.getId(),
                posting.getTitle(),
                posting.getCompany(),
                posting.getLocation(),
                posting.getSourceUrl(),
                posting.getRawText(),
                posting.getRawText().length(),
                latestScore,
                latestBand,
                latestMatchedAt,
                posting.getCreatedAt(),
                posting.getUpdatedAt());
    }
}
