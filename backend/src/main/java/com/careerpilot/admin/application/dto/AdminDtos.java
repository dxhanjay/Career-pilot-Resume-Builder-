package com.careerpilot.admin.application.dto;

import com.careerpilot.auth.domain.Role;
import com.careerpilot.auth.domain.User;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin request and response bodies.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    /**
     * A user as an administrator sees them.
     *
     * <p>No password hash, no token, and no resume content. An admin console is
     * a high-value target, and the least it can expose the better.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "AdminUserResponse")
    public record UserRow(
            UUID id,
            String email,
            String fullName,
            String status,
            boolean emailVerified,
            boolean locked,
            List<String> roles,
            Instant lastLoginAt,
            Instant createdAt
    ) {
        public static UserRow from(User user) {
            return new UserRow(
                    user.getId(),
                    user.getEmail(),
                    user.getFullName(),
                    user.getStatus().name(),
                    user.isEmailVerified(),
                    user.isLocked(),
                    user.getRoles().stream().map(Role::getName).map(Enum::name).sorted().toList(),
                    user.getLastLoginAt(),
                    user.getCreatedAt());
        }
    }

    /**
     * Platform totals.
     *
     * <p>Counts only. Nothing here identifies a user or quotes their resume:
     * operational visibility should not require reading anybody's documents.
     */
    @Schema(name = "AdminStatsResponse")
    public record Stats(
            long totalUsers,
            long activeUsers,
            long pendingUsers,
            long suspendedUsers,
            long newUsersLast7Days,
            long totalResumes,
            long totalAnalyses,
            long totalMatches,
            long totalInterviews,
            long queuedJobs,
            long runningJobs,
            long failedJobs
    ) {
    }

    /** Body for the status change endpoint. */
    @Schema(name = "UpdateUserStatusRequest")
    public record UpdateStatus(
            @jakarta.validation.constraints.NotNull(message = "An action is required")
            Action action
    ) {
        public enum Action {
            SUSPEND,
            REACTIVATE
        }
    }
}
