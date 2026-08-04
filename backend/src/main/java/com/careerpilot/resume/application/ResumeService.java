package com.careerpilot.resume.application;

import com.careerpilot.common.dto.PageResponse;
import com.careerpilot.common.exception.ApiException;
import com.careerpilot.common.exception.BusinessRuleViolationException;
import com.careerpilot.common.exception.ErrorCode;
import com.careerpilot.common.exception.ResourceNotFoundException;
import com.careerpilot.config.properties.ResumeProperties;
import com.careerpilot.config.properties.StorageProperties;
import com.careerpilot.resume.application.dto.DownloadUrlResponse;
import com.careerpilot.resume.application.dto.ResumeResponse;
import com.careerpilot.resume.domain.FileTypeDetector;
import com.careerpilot.resume.domain.Resume;
import com.careerpilot.resume.infrastructure.ResumeRepository;
import com.careerpilot.storage.FileStorage;
import com.careerpilot.storage.StoredFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Resume upload, listing, download, and deletion.
 *
 * <p>The upload path handles the least-trusted input in the entire application:
 * an arbitrary binary file from an unauthenticated-until-a-moment-ago user. The
 * validation order below is deliberate and is documented at each step.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    private final ResumeRepository resumeRepository;
    private final FileStorage fileStorage;
    private final ResumeProperties resumeProperties;
    private final StorageProperties storageProperties;

    public ResumeService(ResumeRepository resumeRepository,
                         FileStorage fileStorage,
                         ResumeProperties resumeProperties,
                         StorageProperties storageProperties) {
        this.resumeRepository = resumeRepository;
        this.fileStorage = fileStorage;
        this.resumeProperties = resumeProperties;
        this.storageProperties = storageProperties;
    }

    // =======================================================================
    // Upload
    // =======================================================================

    /**
     * Validates and stores an uploaded resume.
     *
     * <p><strong>The order of checks is the design.</strong> Each step is
     * cheaper than the one after it, and each rejects input the next step would
     * otherwise have to trust:
     *
     * <ol>
     *   <li><strong>Emptiness and size</strong> — pure arithmetic. Rejecting a
     *       6 MB file here costs nothing; hashing it first would cost real CPU
     *       on input we were always going to discard.</li>
     *   <li><strong>Magic bytes</strong> — the only property of an upload the
     *       sender cannot lie about. Before this point, "it's a PDF" is a claim.</li>
     *   <li><strong>Quota</strong> — a database read, so it comes after the
     *       free checks.</li>
     *   <li><strong>Checksum and duplicate detection</strong> — hashing the
     *       whole file, the most expensive local step.</li>
     *   <li><strong>Storage</strong> — a network call, and the only step that
     *       creates something needing cleanup if a later step fails.</li>
     * </ol>
     *
     * @param userId           the uploading user
     * @param content          the raw file bytes
     * @param originalFilename the client-supplied filename
     * @param makePrimary      whether to mark this the user's primary resume
     * @return the stored resume
     * @throws ApiException if any validation fails
     */
    @Transactional
    public ResumeResponse upload(UUID userId, byte[] content, String originalFilename, boolean makePrimary) {

        // --- 1. Size ---------------------------------------------------------
        if (content == null || content.length == 0) {
            throw new BusinessRuleViolationException("The uploaded file is empty");
        }
        if (content.length > resumeProperties.maxFileSizeBytes()) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "File exceeds the %d MB limit".formatted(resumeProperties.maxFileSizeBytes() / (1024 * 1024)));
        }

        // --- 2. Type, from the bytes ----------------------------------------
        String safeFilename = sanitiseFilename(originalFilename);

        byte[] header = new byte[Math.min(FileTypeDetector.SIGNATURE_LENGTH, content.length)];
        System.arraycopy(content, 0, header, 0, header.length);

        Optional<FileTypeDetector.FileType> detected = FileTypeDetector.detect(header, safeFilename);

        if (detected.isEmpty()) {
            // A distinct message for legacy .doc. Users whose word processor
            // considers the file perfectly normal deserve better than
            // "unsupported file type".
            if (FileTypeDetector.isLegacyWordDocument(header)) {
                throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                        "Legacy .doc files are not supported. Save as PDF or .docx and try again.");
            }
            log.warn("Rejected upload: content does not match any accepted format");
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Only PDF and .docx files are accepted");
        }

        FileTypeDetector.FileType fileType = detected.get();

        // --- 3. Quota --------------------------------------------------------
        long existing = resumeRepository.countByUserId(userId);
        if (existing >= resumeProperties.maxResumesPerUser()) {
            throw new BusinessRuleViolationException(
                    "You have reached the limit of %d resumes. Delete one to upload another."
                            .formatted(resumeProperties.maxResumesPerUser()));
        }

        // --- 4. Duplicate detection (FR-RES-05) ------------------------------
        String checksum = sha256(content);

        Optional<Resume> duplicate = resumeRepository.findByUserIdAndChecksum(userId, checksum);
        if (duplicate.isPresent()) {
            // 409 rather than silently returning the existing resume. Returning
            // 200 would make "upload" sometimes create and sometimes not, and
            // the client could not tell which happened.
            log.info("Rejected upload: identical content already stored as {}", duplicate.get().getId());
            throw new ApiException(ErrorCode.CONFLICT,
                    "You have already uploaded this exact file");
        }

        // --- 5. Store --------------------------------------------------------
        StoredFile stored = fileStorage.store(userId, content, safeFilename, fileType.getMimeType());

        short nextVersion = (short) (resumeRepository.findMaxVersionByUserId(userId).orElse((short) 0) + 1);

        Resume resume = new Resume(
                userId,
                safeFilename,
                stored.provider(),
                stored.publicId(),
                stored.url(),
                // The type we DETECTED, never the type the client claimed.
                fileType.getMimeType(),
                stored.sizeBytes(),
                checksum,
                nextVersion);

        // First upload becomes primary automatically. A user with exactly one
        // resume and no primary is a state every downstream feature would have
        // to special-case for no reason.
        if (makePrimary || existing == 0) {
            resumeRepository.clearPrimaryForUser(userId);
            resume.makePrimary();
        }

        resumeRepository.save(resume);

        log.info("Stored resume {} for user {} (version {}, {} bytes)",
                resume.getId(), userId, nextVersion, stored.sizeBytes());

        return ResumeResponse.from(resume);
    }

    // =======================================================================
    // Read
    // =======================================================================

    /**
     * Lists a user's resumes, newest first.
     *
     * @param userId   the owner
     * @param pageable page request
     * @return one page of resumes
     */
    @Transactional(readOnly = true)
    public PageResponse<ResumeResponse> list(UUID userId, Pageable pageable) {
        return PageResponse.from(resumeRepository.findAllByUserId(userId, pageable), ResumeResponse::from);
    }

    /**
     * Fetches one resume.
     *
     * @param resumeId the resume
     * @param userId   the requesting user
     * @return the resume
     * @throws ResourceNotFoundException if it does not exist or is not theirs
     */
    @Transactional(readOnly = true)
    public ResumeResponse get(UUID resumeId, UUID userId) {
        return ResumeResponse.from(requireOwned(resumeId, userId));
    }

    /**
     * Mints a short-lived signed URL for downloading a resume.
     *
     * <p>Ownership is verified here, before the URL exists. Once minted the URL
     * is a bearer credential that carries no identity of its own — which is
     * exactly why it must expire in minutes rather than hours.
     *
     * @param resumeId the resume
     * @param userId   the requesting user
     * @return a signed URL and its expiry
     * @throws ResourceNotFoundException if it does not exist or is not theirs
     */
    @Transactional(readOnly = true)
    public DownloadUrlResponse getDownloadUrl(UUID resumeId, UUID userId) {
        Resume resume = requireOwned(resumeId, userId);

        Duration ttl = Duration.ofSeconds(storageProperties.signedUrlTtlSeconds());
        String url = fileStorage.generateSignedUrl(resume.getStoragePublicId(), ttl);

        return new DownloadUrlResponse(url, Instant.now().plus(ttl), resume.getOriginalFilename());
    }

    // =======================================================================
    // Mutate
    // =======================================================================

    /**
     * Marks a resume as the user's primary one.
     *
     * @param resumeId the resume
     * @param userId   the owner
     * @return the updated resume
     */
    @Transactional
    public ResumeResponse makePrimary(UUID resumeId, UUID userId) {
        Resume resume = requireOwned(resumeId, userId);

        // Clear before set, within one transaction. The partial unique index
        // permits one primary per user, so doing this in the other order — or in
        // two transactions — violates the constraint.
        resumeRepository.clearPrimaryForUser(userId);
        resume.makePrimary();

        return ResumeResponse.from(resume);
    }

    /**
     * Soft-deletes a resume.
     *
     * <p>The row and the stored file survive for {@code app.resume.purge-after-days}
     * so an accidental deletion can be reversed, then a scheduled job removes
     * both (NFR-PRIV-01).
     *
     * <p>If the deleted resume was primary, the most recent survivor is promoted.
     * Leaving a user with resumes but no primary would make every downstream
     * feature handle a state that has no meaning to them.
     *
     * @param resumeId the resume
     * @param userId   the owner
     */
    @Transactional
    public void delete(UUID resumeId, UUID userId) {
        Resume resume = requireOwned(resumeId, userId);
        boolean wasPrimary = resume.isPrimary();

        resume.softDelete();

        if (wasPrimary) {
            resumeRepository.findAllByUserId(userId, Pageable.ofSize(1)).stream()
                    .findFirst()
                    .ifPresent(Resume::makePrimary);
        }

        log.info("Soft-deleted resume {} for user {}", resumeId, userId);
    }

    // =======================================================================
    // Scheduled purge
    // =======================================================================

    /**
     * Permanently removes resumes soft-deleted beyond the retention window.
     *
     * <p><strong>The file is deleted before the row.</strong> Doing it the other
     * way round would orphan the stored object permanently: once the row is
     * gone, nothing records that the file exists or whom it belonged to, so it
     * would sit in storage forever — accruing cost and, more seriously, breaching
     * the deletion commitment while appearing to satisfy it.
     *
     * <p>A storage failure on one file is logged and skipped rather than
     * aborting the batch. One unreachable object must not prevent every other
     * user's data from being deleted on time.
     *
     * @return how many resumes were purged
     */
    @Transactional
    public int purgeDeleted() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(resumeProperties.purgeAfterDays()));
        var purgeable = resumeRepository.findPurgeable(cutoff);

        int purged = 0;
        for (Resume resume : purgeable) {
            try {
                fileStorage.delete(resume.getStoragePublicId());
                resumeRepository.delete(resume);
                purged++;
            } catch (RuntimeException e) {
                log.error("Could not purge resume {}; it will be retried on the next run",
                        resume.getId(), e);
            }
        }

        if (purged > 0) {
            log.info("Purged {} resume(s) past the {}-day retention window",
                    purged, resumeProperties.purgeAfterDays());
        }
        return purged;
    }

    // =======================================================================
    // Helpers
    // =======================================================================

    private Resume requireOwned(UUID resumeId, UUID userId) {
        return resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume"));
    }

    /**
     * Reduces a client-supplied filename to something safe to store and display.
     *
     * <p>Three distinct problems, all originating in the same untrusted string:
     *
     * <ul>
     *   <li><strong>Path traversal.</strong> {@code ../../../etc/passwd} must not
     *       influence any path. Only the final path element is kept.</li>
     *   <li><strong>Injection into other contexts.</strong> A filename is echoed
     *       back in JSON and rendered in a browser; characters like {@code <}
     *       and {@code "} have meaning there.</li>
     *   <li><strong>Length.</strong> The column is 255 characters, and an
     *       oversized value would fail at insert rather than at validation.</li>
     * </ul>
     *
     * @param filename the client-supplied name
     * @return a safe name, never blank
     */
    private String sanitiseFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "resume.pdf";
        }

        // Keep only the final path element, handling both separator conventions
        // because the client's platform is unknown.
        //
        // Done with plain string operations rather than java.nio.file.Paths.
        // Paths.get() applies the HOST filesystem's rules, and on Windows it
        // throws InvalidPathException for characters that are perfectly legal in
        // a filename elsewhere - '<', '>', '|', '"', '*', '?'. A Linux user
        // uploading report<final>.pdf would therefore crash a developer's
        // machine but not the Railway container, or the reverse after a platform
        // change. Sanitisation of untrusted input must not depend on where the
        // code happens to be running, and must never throw.
        String base = filename.replace('\\', '/');
        int lastSeparator = base.lastIndexOf('/');
        if (lastSeparator >= 0) {
            base = base.substring(lastSeparator + 1);
        }

        String cleaned = base
                .replaceAll("[^A-Za-z0-9._\\- ]", "_")
                .replaceAll("_{2,}", "_")
                .trim();

        if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..")) {
            return "resume.pdf";
        }

        return cleaned.length() > 200
                ? cleaned.substring(0, 200)
                : cleaned;
    }

    /**
     * Computes the SHA-256 of the file's content.
     *
     * <p>Used for duplicate detection only, never as a security boundary. A
     * collision would cause a false "already uploaded", not a data leak.
     *
     * @param content the file bytes
     * @return lowercase hex digest, 64 characters
     */
    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable on this JVM", e);
        }
    }
}
