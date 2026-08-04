package com.careerpilot.jobs.application.dto;

import com.careerpilot.jobs.domain.Job;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * The polling response for an async job.
 *
 * <p>{@code resultRef} is the field that makes polling useful: on success it
 * names the row the job produced, so the client fetches the result with a
 * type→path mapping it already has rather than needing per-job-type knowledge
 * of where output lands.
 *
 * <p>{@code terminal} is derived server-side so the client's polling loop is a
 * single check rather than a list of statuses it must keep in sync with ours.
 *
 * @param id          job identifier
 * @param type        what kind of work
 * @param status      current state
 * @param terminal    whether polling can stop
 * @param attempts    attempts made so far
 * @param maxAttempts attempts before giving up
 * @param referenceId the target this job operates on
 * @param resultRef   id of the produced row, once succeeded
 * @param error       client-safe failure description, once failed
 * @param createdAt   enqueue time
 * @param startedAt   first execution time
 * @param finishedAt  completion time
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Schema(name = "JobStatusResponse", description = "Status of an asynchronous job")
public record JobStatusResponse(
        UUID id,
        String type,
        String status,
        boolean terminal,
        short attempts,
        short maxAttempts,
        UUID referenceId,
        UUID resultRef,
        String error,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {

    /**
     * Maps a job to its polling view.
     *
     * @param job the entity
     * @return the response DTO
     */
    public static JobStatusResponse from(Job job) {
        return new JobStatusResponse(
                job.getId(),
                job.getJobType().name(),
                job.getStatus().name(),
                job.getStatus().isTerminal(),
                job.getAttempts(),
                job.getMaxAttempts(),
                job.getReferenceId(),
                job.getResultRef(),
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt());
    }
}
