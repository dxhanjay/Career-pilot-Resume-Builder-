package com.careerpilot.matching.domain;

import com.careerpilot.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A job posting the user saved.
 *
 * <p>Soft-deleted, like resumes, so an accidental delete is recoverable and any
 * match already computed against it keeps its foreign key.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Entity
@Table(name = "job_descriptions")
public class JobDescription extends AuditableEntity {

    private static final int MAX_TITLE = 200;
    private static final int MAX_COMPANY = 200;
    private static final int MAX_LOCATION = 150;
    private static final int MAX_URL = 1000;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "title", nullable = false, length = MAX_TITLE)
    private String title;

    @Column(name = "company", length = MAX_COMPANY)
    private String company;

    @Column(name = "location", length = MAX_LOCATION)
    private String location;

    @Column(name = "source_url", length = MAX_URL)
    private String sourceUrl;

    @Column(name = "raw_text", nullable = false, columnDefinition = "text")
    private String rawText;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected JobDescription() {
    }

    public JobDescription(UUID userId, String title, String company, String location,
                          String sourceUrl, String rawText) {
        this.userId = userId;
        this.title = fit(title, MAX_TITLE);
        this.company = fit(company, MAX_COMPANY);
        this.location = fit(location, MAX_LOCATION);
        this.sourceUrl = fit(sourceUrl, MAX_URL);
        this.rawText = rawText;
    }

    /**
     * Applies an edit. Null means "leave alone", not "clear" — a PATCH that
     * omits a field must not silently erase it.
     */
    public void update(String title, String company, String location, String sourceUrl,
                       String rawText) {
        if (title != null) {
            this.title = fit(title, MAX_TITLE);
        }
        if (company != null) {
            this.company = fit(company, MAX_COMPANY);
        }
        if (location != null) {
            this.location = fit(location, MAX_LOCATION);
        }
        if (sourceUrl != null) {
            this.sourceUrl = fit(sourceUrl, MAX_URL);
        }
        if (rawText != null) {
            this.rawText = rawText;
        }
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private static String fit(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getCompany() {
        return company;
    }

    public String getLocation() {
        return location;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getRawText() {
        return rawText;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JobDescription jobDescription)) {
            return false;
        }
        return id != null && id.equals(jobDescription.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(JobDescription.class);
    }

    @Override
    public String toString() {
        return "JobDescription{id=" + id + ", title=" + title + "}";
    }
}
