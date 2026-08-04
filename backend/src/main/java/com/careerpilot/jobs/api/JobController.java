package com.careerpilot.jobs.api;

import com.careerpilot.common.dto.ApiResponse;
import com.careerpilot.common.dto.PageResponse;
import com.careerpilot.common.security.AuthenticatedUser;
import com.careerpilot.jobs.application.JobService;
import com.careerpilot.jobs.application.dto.JobStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Job status polling.
 *
 * <p>Every long-running operation in the product — parsing, ATS analysis, JD
 * matching, interview evaluation — returns {@code 202} with a job id and is then
 * tracked here. One endpoint rather than four bespoke status routes, so the
 * client has one polling implementation and one retry policy.
 *
 * <p>Suggested cadence: 2s for the first 30s, then 5s, with a 5-minute ceiling.
 * Server-Sent Events would fit better and are a Phase 14 candidate, but polling
 * has no connection state to manage across a platform that restarts containers
 * freely.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Jobs", description = "Status of asynchronous work")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Returns a job's status.
     *
     * @param id        the job
     * @param principal the authenticated caller
     * @return 200 with the status
     */
    @GetMapping("/{id}")
    @Operation(summary = "Poll a job",
            description = """
                    Poll until `terminal` is true. On success, `resultRef` names the row the \
                    job produced. Returns 404 for another user's job, not 403.""")
    public ResponseEntity<ApiResponse<JobStatusResponse>> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return ResponseEntity.ok(ApiResponse.ok(jobService.getStatus(id, principal.getId())));
    }

    /**
     * Lists the caller's jobs, newest first.
     *
     * @param pageable  page request
     * @param principal the authenticated caller
     * @return 200 with one page of statuses
     */
    @GetMapping
    @Operation(summary = "List your jobs")
    public ResponseEntity<ApiResponse<PageResponse<JobStatusResponse>>> list(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return ResponseEntity.ok(ApiResponse.ok(jobService.list(principal.getId(), pageable)));
    }

    /**
     * Cancels a job that has not started.
     *
     * @param id        the job
     * @param principal the authenticated caller
     * @return 204
     */
    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a queued job",
            description = "Returns 409 if the job has already started or finished.")
    public ResponseEntity<Void> cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        jobService.cancel(id, principal.getId());
        return ResponseEntity.noContent().build();
    }
}
