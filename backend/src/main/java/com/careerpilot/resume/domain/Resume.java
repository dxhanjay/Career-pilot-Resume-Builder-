package com.careerpilot.resume.domain;

import com.careerpilot.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An uploaded resume file.
 *
 * <p>Holds a reference to the stored object, never the bytes. Storing file
 * content in a database column would bloat every backup, defeat the connection
 * pool on large reads, and make the row unusable in a list query — and the
 * bytes are only ever needed by the parser, which fetches them once.
 *
 * <p>Carries {@code userId} as a plain column rather than a {@code @ManyToOne}
 * association. That is the denormalisation described in the database design
 * §1.2, and it is what makes ownership checks a single-table predicate:
 * {@code findByIdAndUserId(...)} cannot be written incorrectly, whereas a
 * fetch-then-compare can silently omit the comparison. It also makes account
 * deletion a flat fan-out of independent deletes.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Entity
@Table(name = "resumes")
public class Resume extends AuditableEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false, length = 20)
    private StorageProvider storageProvider;

    @Column(name = "storage_public_id", nullable = false, length = 255)
    private String storagePublicId;

    @Column(name = "storage_url", nullable = false, columnDefinition = "text")
    private String storageUrl;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "checksum_sha256", nullable = false, length = 64, updatable = false)
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ResumeStatus status = ResumeStatus.UPLOADED;

    @Column(name = "version", nullable = false)
    private short version;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Required by JPA. Not for application use. */
    protected Resume() {
    }

    /**
     * Records a newly stored resume.
     *
     * @param userId           the owner
     * @param originalFilename the client-supplied name, already sanitised
     * @param storageProvider  which backend holds the object
     * @param storagePublicId  provider handle for fetch and delete
     * @param storageUrl       base URL; signed at read time
     * @param mimeType         type determined from the bytes, not from the client
     * @param sizeBytes        size in bytes
     * @param checksumSha256   SHA-256 of the content
     * @param version          this user's next resume version number
     */
    public Resume(UUID userId,
                  String originalFilename,
                  StorageProvider storageProvider,
                  String storagePublicId,
                  String storageUrl,
                  String mimeType,
                  int sizeBytes,
                  String checksumSha256,
                  short version) {
        this.userId = userId;
        this.originalFilename = originalFilename;
        this.storageProvider = storageProvider;
        this.storagePublicId = storagePublicId;
        this.storageUrl = storageUrl;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksumSha256;
        this.version = version;
        this.status = ResumeStatus.UPLOADED;
        this.primary = false;
    }

    // --- behaviour ---------------------------------------------------------

    /**
     * Marks this resume as the user's primary one.
     *
     * <p>Clearing the previous primary is the caller's responsibility, and the
     * database enforces the invariant with a partial unique index — so a bug
     * that forgot to clear the old one fails loudly rather than leaving two.
     */
    public void makePrimary() {
        this.primary = true;
    }

    /**
     * Clears the primary flag.
     */
    public void clearPrimary() {
        this.primary = false;
    }

    /**
     * Moves this resume into {@link ResumeStatus#PARSING}.
     *
     * @throws IllegalStateException if parsing is not valid from the current state
     */
    public void markParsing() {
        if (!status.canStartParsing()) {
            throw new IllegalStateException(
                    "Cannot start parsing from status " + status);
        }
        this.status = ResumeStatus.PARSING;
    }

    /**
     * Records a successful parse.
     */
    public void markParsed() {
        this.status = ResumeStatus.PARSED;
    }

    /**
     * Records that every parser failed.
     *
     * <p>A terminal but recoverable state: the user may upload a different file,
     * and a future parser improvement can retry this one.
     */
    public void markParseFailed() {
        this.status = ResumeStatus.PARSE_FAILED;
    }

    /**
     * Soft-deletes the resume.
     *
     * <p>The row and the stored object survive for 30 days so an accidental
     * deletion can be reversed, then a scheduled job removes both
     * (NFR-PRIV-01). Clearing the primary flag matters: the partial unique
     * index excludes deleted rows, so leaving it set would block the user from
     * making another resume primary.
     */
    public void softDelete() {
        this.deletedAt = Instant.now();
        this.primary = false;
    }

    /**
     * @return whether this resume has been soft-deleted
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    // --- accessors ---------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public StorageProvider getStorageProvider() {
        return storageProvider;
    }

    public String getStoragePublicId() {
        return storagePublicId;
    }

    public String getStorageUrl() {
        return storageUrl;
    }

    public String getMimeType() {
        return mimeType;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public ResumeStatus getStatus() {
        return status;
    }

    public short getVersion() {
        return version;
    }

    public boolean isPrimary() {
        return primary;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Resume resume)) {
            return false;
        }
        return id != null && id.equals(resume.id);
    }

    /** Type-constant, for the reason documented on {@code Role#hashCode()}. */
    @Override
    public int hashCode() {
        return Objects.hash(Resume.class);
    }

    /**
     * Diagnostic representation.
     *
     * <p>Excludes the filename. A student's CV is commonly named
     * "Aditi_Sharma_Resume_Final.pdf", which puts a real person's name into
     * every log line that touches this object.
     */
    @Override
    public String toString() {
        return "Resume{id=" + id + ", status=" + status + ", version=" + version + "}";
    }
}
