package com.careerpilot.parsing.api;

import com.careerpilot.common.dto.ApiResponse;
import com.careerpilot.common.security.AuthenticatedUser;
import com.careerpilot.jobs.application.dto.JobStatusResponse;
import com.careerpilot.jobs.domain.Job;
import com.careerpilot.parsing.application.ResumeParsingService;
import com.careerpilot.parsing.application.dto.ParseResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Resume text extraction.
 *
 * <p>Parsing is asynchronous: {@code POST} returns {@code 202} with a job id,
 * and the client polls {@code GET /api/v1/jobs/{id}} until terminal. Extraction
 * takes seconds and considerably longer on a cold container, which does not fit
 * inside an HTTP request — on Railway that would surface as a timeout rather
 * than a slow response.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/resumes/{resumeId}/parse")
@Tag(name = "Resume parsing", description = "Extract text from an uploaded resume")
public class ParseController {

    private final ResumeParsingService parsingService;

    public ParseController(ResumeParsingService parsingService) {
        this.parsingService = parsingService;
    }

    /**
     * Starts a parse.
     *
     * @param resumeId  the resume
     * @param principal the authenticated caller
     * @return 202 with the job to poll
     */
    @PostMapping
    @Operation(summary = "Start parsing a resume",
            description = """
                    Returns 202 with a job id. Poll GET /api/v1/jobs/{id} until `terminal` is \
                    true, then read the result here. Enqueuing twice for the same resume \
                    returns the job already in flight rather than duplicating the work.""")
    public ResponseEntity<ApiResponse<JobStatusResponse>> startParse(
            @PathVariable UUID resumeId,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        Job job = parsingService.requestParse(resumeId, principal.getId());

        return ResponseEntity.accepted()
                .body(ApiResponse.ok(JobStatusResponse.from(job), "Parsing started"));
    }

    /**
     * Returns the parse result, without the extracted text.
     *
     * @param resumeId  the resume
     * @param principal the authenticated caller
     * @return 200 with the result summary and any warnings
     */
    @GetMapping
    @Operation(summary = "Get the parse result",
            description = """
                    Returns the latest successful parse, or the latest failure with its reason. \
                    Excludes the extracted text - use /raw-text for that.""")
    public ResponseEntity<ApiResponse<ParseResultResponse>> getResult(
            @PathVariable UUID resumeId,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return ResponseEntity.ok(ApiResponse.ok(
                parsingService.getResult(resumeId, principal.getId())));
    }

    /**
     * Returns the extracted text.
     *
     * <p>Backs the "here's what the machine saw" screen — the moment that makes
     * the product credible, because showing a student their parsed resume
     * explains a bad score in a way no number can.
     *
     * @param resumeId  the resume
     * @param principal the authenticated caller
     * @return 200 with the extracted text
     */
    @GetMapping("/raw-text")
    @Operation(summary = "Get the extracted text",
            description = """
                    The text an applicant tracking system would see. Returns 409 if the resume \
                    has not been parsed successfully.""")
    public ResponseEntity<ApiResponse<ParseResultResponse>> getRawText(
            @PathVariable UUID resumeId,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        return ResponseEntity.ok(ApiResponse.ok(
                parsingService.getRawText(resumeId, principal.getId())));
    }
}
