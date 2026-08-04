package com.careerpilot.resume.application.dto;

import com.careerpilot.resume.domain.Resume;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * The public view of an uploaded resume.
 *
 * <p>Omits {@code storagePublicId} and {@code storageUrl} deliberately. Those
 * are the handles used to fetch the file's bytes; exposing them would let a
 * client construct requests to the storage provider directly, bypassing the
 * signed-URL flow that is the actual access control. Downloads go through
 * {@code GET /resumes/{id}/download}, which mints a short-lived URL after
 * verifying ownership.
 *
 * <p>{@code checksum} is included: it is derived from content the user supplied,
 * discloses nothing they do not have, and lets a client explain a 409 on a
 * duplicate upload without a second request.
 *
 * @param id               resume identifier
 * @param originalFilename the name the user uploaded it under
 * @param mimeType         type determined from the bytes
 * @param sizeBytes        size in bytes
 * @param checksum         SHA-256 of the content
 * @param status           parsing lifecycle state
 * @param version          this user's version number for the resume
 * @param primary          whether this is the user's primary resume
 * @param analysable       whether it can be analysed, matched, or interviewed against
 * @param createdAt        upload time
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Schema(name = "ResumeResponse", description = "An uploaded resume")
public record ResumeResponse(
        UUID id,
        String originalFilename,
        String mimeType,
        int sizeBytes,
        String checksum,
        String status,
        short version,
        boolean primary,
        boolean analysable,
        Instant createdAt
) {

    /**
     * Maps an entity to its public view.
     *
     * @param resume the entity
     * @return the response DTO
     */
    public static ResumeResponse from(Resume resume) {
        return new ResumeResponse(
                resume.getId(),
                resume.getOriginalFilename(),
                resume.getMimeType(),
                resume.getSizeBytes(),
                resume.getChecksumSha256(),
                resume.getStatus().name(),
                resume.getVersion(),
                resume.isPrimary(),
                // Derived here rather than left to the client. Two clients would
                // otherwise each reimplement "which statuses allow analysis",
                // and would disagree the first time a status is added.
                resume.getStatus().isAnalysable(),
                resume.getCreatedAt());
    }
}
