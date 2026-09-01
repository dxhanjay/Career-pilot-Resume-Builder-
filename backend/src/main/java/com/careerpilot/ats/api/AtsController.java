package com.careerpilot.ats.api;

import com.careerpilot.ats.application.AtsAnalysisService;
import com.careerpilot.ats.application.dto.AtsAnalysisResponse;
import com.careerpilot.ats.application.dto.AtsHistoryResponse;
import com.careerpilot.common.dto.ApiResponse;
import com.careerpilot.common.security.AuthenticatedUser;
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
 * ATS scoring endpoints.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/resumes/{resumeId}/ats")
@Tag(name = "ATS analysis", description = "Score a parsed resume and show the evidence")
public class AtsController {

    private final AtsAnalysisService analysisService;

    public AtsController(AtsAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping
    @Operation(summary = "Run an ATS analysis",
            description = """
                    Scores the latest successful parse and returns the report synchronously. \
                    Each call stores a new run rather than replacing the previous one, so \
                    re-analysing after an edit builds the score history. Returns 409 if the \
                    resume has not been parsed successfully yet.""")
    public ResponseEntity<ApiResponse<AtsAnalysisResponse>> analyze(
            @PathVariable UUID resumeId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                analysisService.analyze(resumeId, principal.getId()), "Analysis complete"));
    }

    @GetMapping
    @Operation(summary = "Get the latest ATS report",
            description = "Returns 404 if the resume has never been analysed.")
    public ResponseEntity<ApiResponse<AtsAnalysisResponse>> latest(
            @PathVariable UUID resumeId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                analysisService.getLatest(resumeId, principal.getId())));
    }

    @GetMapping("/history")
    @Operation(summary = "Get the score history",
            description = """
                    Every analysis run for this resume, oldest first, with the movement between \
                    the first and the latest score.""")
    public ResponseEntity<ApiResponse<AtsHistoryResponse>> history(
            @PathVariable UUID resumeId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                analysisService.getHistory(resumeId, principal.getId())));
    }
}
