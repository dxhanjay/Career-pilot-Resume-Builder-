package com.careerpilot.admin.api;

import com.careerpilot.admin.application.AdminService;
import com.careerpilot.admin.application.dto.AdminDtos;
import com.careerpilot.common.dto.ApiResponse;
import com.careerpilot.common.dto.PageResponse;
import com.careerpilot.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The administrator console API.
 *
 * <p>{@code /api/v1/admin/**} already requires {@code ROLE_ADMIN} in
 * {@link com.careerpilot.config.SecurityConfig}. The class-level
 * {@code @PreAuthorize} is deliberate duplication: a future refactor of the URL
 * scheme that moved these endpoints would otherwise silently unprotect them, and
 * defence in depth is cheap when it is one annotation.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin", description = "Platform operations. Requires ROLE_ADMIN.")
public class AdminController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    @Operation(summary = "Platform totals",
            description = "Counts only. Nothing here identifies a user or quotes a document.")
    public ResponseEntity<ApiResponse<AdminDtos.Stats>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.stats()));
    }

    @GetMapping("/users")
    @Operation(summary = "List or search users",
            description = "A blank query lists everyone. Matching is on email and full name.")
    public ResponseEntity<ApiResponse<PageResponse<AdminDtos.UserRow>>> users(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(adminService.users(
                query,
                PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE),
                        Sort.by(Sort.Direction.DESC, "createdAt")))));
    }

    @PatchMapping("/users/{id}/status")
    @Operation(summary = "Suspend or reinstate an account",
            description = "An administrator cannot change their own status.")
    public ResponseEntity<ApiResponse<AdminDtos.UserRow>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody AdminDtos.UpdateStatus request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                adminService.updateStatus(id, principal.getId(), request.action()),
                "User status updated"));
    }
}
