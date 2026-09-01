package com.careerpilot.dashboard.api;

import com.careerpilot.common.dto.ApiResponse;
import com.careerpilot.common.security.AuthenticatedUser;
import com.careerpilot.dashboard.application.DashboardService;
import com.careerpilot.dashboard.application.dto.DashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The home screen summary.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Home screen summary")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(summary = "Get the signed-in home screen",
            description = """
                    Recent resumes, the primary resume's latest ATS score, and totals. Empty \
                    fields are the normal state for a new account rather than an error.""")
    public ResponseEntity<ApiResponse<DashboardResponse>> get(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.forUser(principal.getId())));
    }
}
