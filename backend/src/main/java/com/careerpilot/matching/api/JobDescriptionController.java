package com.careerpilot.matching.api;

import com.careerpilot.common.dto.ApiResponse;
import com.careerpilot.common.dto.PageResponse;
import com.careerpilot.common.security.AuthenticatedUser;
import com.careerpilot.matching.application.JobMatchingService;
import com.careerpilot.matching.application.dto.JobDescriptionRequests;
import com.careerpilot.matching.application.dto.JobDescriptionResponse;
import com.careerpilot.matching.application.dto.MatchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Job postings and the matches run against them.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/job-descriptions")
@Tag(name = "Job matching", description = "Save postings and match a resume against them")
public class JobDescriptionController {

    /** Bounded so a client cannot ask for every row in one request. */
    private static final int MAX_PAGE_SIZE = 50;

    private final JobMatchingService matchingService;

    public JobDescriptionController(JobMatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @PostMapping
    @Operation(summary = "Save a job description")
    public ResponseEntity<ApiResponse<JobDescriptionResponse>> create(
            @Valid @RequestBody JobDescriptionRequests.Create request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                matchingService.create(principal.getId(), request), "Job description saved"));
    }

    @GetMapping
    @Operation(summary = "List saved job descriptions",
            description = "Newest first. Each entry carries its most recent match score, if any.")
    public ResponseEntity<ApiResponse<PageResponse<JobDescriptionResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(matchingService.list(
                principal.getId(),
                PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE),
                        Sort.by(Sort.Direction.DESC, "createdAt")))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one job description, including its text")
    public ResponseEntity<ApiResponse<JobDescriptionResponse>> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(matchingService.get(id, principal.getId())));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Edit a job description",
            description = "Omitted fields are left unchanged.")
    public ResponseEntity<ApiResponse<JobDescriptionResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody JobDescriptionRequests.Update request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                matchingService.update(id, principal.getId(), request), "Job description updated"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a job description")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        matchingService.delete(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.ok("Job description deleted"));
    }

    @PostMapping("/{id}/match")
    @Operation(summary = "Match a resume against this posting",
            description = """
                    Returns the report synchronously. Each call stores a new run, so re-matching \
                    after editing the resume shows whether the edit helped. Returns 409 if the \
                    chosen resume has not been parsed successfully.""")
    public ResponseEntity<ApiResponse<MatchResponse>> match(
            @PathVariable UUID id,
            @Valid @RequestBody JobDescriptionRequests.RunMatch request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                matchingService.match(id, request.resumeId(), principal.getId()),
                "Match complete"));
    }

    @GetMapping("/{id}/match")
    @Operation(summary = "Get the latest match for this posting",
            description = "Returns 404 if this posting has never been matched.")
    public ResponseEntity<ApiResponse<MatchResponse>> latestMatch(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                matchingService.getLatest(id, principal.getId())));
    }

    @GetMapping("/{id}/match/history")
    @Operation(summary = "Every match run against this posting, newest first")
    public ResponseEntity<ApiResponse<List<MatchResponse>>> matchHistory(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                matchingService.history(id, principal.getId())));
    }
}
