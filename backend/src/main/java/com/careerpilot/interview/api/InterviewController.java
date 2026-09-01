package com.careerpilot.interview.api;

import com.careerpilot.common.dto.ApiResponse;
import com.careerpilot.common.dto.PageResponse;
import com.careerpilot.common.security.AuthenticatedUser;
import com.careerpilot.interview.application.InterviewService;
import com.careerpilot.interview.application.dto.InterviewDtos;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Mock interview endpoints.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/interviews")
@Tag(name = "Mock interview", description = "Practise the interview a specific job will give you")
public class InterviewController {

    private static final int MAX_PAGE_SIZE = 50;

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @PostMapping
    @Operation(summary = "Start a mock interview",
            description = """
                    Generates the questions up front from your resume and, for a job-specific \
                    session, from the gaps against a posting. Questions are fixed for the life \
                    of the session.""")
    public ResponseEntity<ApiResponse<InterviewDtos.SessionView>> start(
            @Valid @RequestBody InterviewDtos.StartSession request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                interviewService.start(principal.getId(), request), "Interview started"));
    }

    @GetMapping
    @Operation(summary = "List your interview sessions, newest first")
    public ResponseEntity<ApiResponse<PageResponse<InterviewDtos.SessionSummary>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(interviewService.list(
                principal.getId(),
                PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE),
                        Sort.by(Sort.Direction.DESC, "createdAt")))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a session with its questions and any answers so far",
            description = """
                    The cues a good answer covers are withheld until a question has been \
                    answered, or until the session is finished.""")
    public ResponseEntity<ApiResponse<InterviewDtos.SessionView>> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(interviewService.get(id, principal.getId())));
    }

    @PostMapping("/{id}/questions/{questionId}/answer")
    @Operation(summary = "Submit an answer",
            description = """
                    Scored immediately on structure, specificity, relevance and clarity. \
                    Answering again replaces the previous answer, so you can rewrite and watch \
                    the score move.""")
    public ResponseEntity<ApiResponse<InterviewDtos.AnswerView>> answer(
            @PathVariable UUID id,
            @PathVariable UUID questionId,
            @Valid @RequestBody InterviewDtos.SubmitAnswer request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                interviewService.answer(id, questionId, principal.getId(), request.answer()),
                "Answer scored"));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Finish the interview and get the report",
            description = """
                    Unanswered questions count as zero, so the score reflects the interview \
                    you actually sat. Returns 409 if nothing has been answered.""")
    public ResponseEntity<ApiResponse<InterviewDtos.SessionView>> complete(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                interviewService.complete(id, principal.getId()), "Interview complete"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Abandon an interview",
            description = "Marks it abandoned and keeps the record. Nothing is deleted.")
    public ResponseEntity<ApiResponse<Void>> abandon(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        interviewService.abandon(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.ok("Interview abandoned"));
    }
}
