package com.careerpilot.auth.application.dto;

import com.careerpilot.auth.domain.Role;
import com.careerpilot.auth.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The public representation of a user.
 *
 * <p><strong>What this record omits is the point of its existence.</strong> The
 * {@code User} entity carries {@code passwordHash}, {@code failedLoginAttempts},
 * {@code lockedUntil}, {@code aiCreditsUsedMonth}, and {@code deletedAt}. None
 * of those belong in an HTTP response: the first is a credential, and the rest
 * describe internal state that tells a caller more about our defences than they
 * need to know.
 *
 * <p>Serialising the entity directly would expose every one of them, and would
 * keep exposing each new field added in a later phase, silently, with no code
 * change and no review. An explicit DTO makes exposure opt-in.
 *
 * <h2>Why response DTOs live in the application layer</h2>
 *
 * <p>Mapping an entity to a DTO requires reading the entity, and an ArchUnit
 * rule forbids the API layer from importing anything annotated {@code @Entity}
 * (NFR-SEC-05, enforced structurally rather than by convention). Putting this
 * class in {@code api} would have forced that rule to be weakened.
 *
 * <p>The split that resolves it cleanly: <strong>request DTOs belong to the
 * transport</strong> and live in {@code api}, because they are pure input
 * structures that never touch a domain object; <strong>response DTOs are
 * produced by use cases</strong> and live here, because producing one is part of
 * executing the use case. The controller then becomes what it should be — HTTP
 * plumbing that never sees an entity at all.
 *
 * @param id            user identifier
 * @param email         registered address
 * @param fullName      display name
 * @param status        account lifecycle state
 * @param emailVerified whether the address has been confirmed
 * @param roles         granted role names
 * @param createdAt     registration time
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Schema(name = "UserResponse", description = "Public view of a user account")
public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String status,
        boolean emailVerified,
        Set<String> roles,
        Instant createdAt
) {

    /**
     * Maps an entity to its public representation.
     *
     * <p>A static factory on the DTO rather than a separate mapper class: the
     * mapping is a property of this record, and keeping it adjacent means a
     * field added to the record is impossible to leave unmapped without the
     * compiler saying so.
     *
     * @param user the entity
     * @return the response DTO
     */
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus().name(),
                user.isEmailVerified(),
                user.getRoles().stream()
                        .map(Role::getName)
                        .map(Enum::name)
                        .collect(Collectors.toSet()),
                user.getCreatedAt());
    }
}
