package com.careerpilot.parsing.domain;

import com.careerpilot.parsing.domain.extract.ExperienceEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * One role extracted from a parse.
 *
 * <p>{@code description} keeps the candidate's achievement bullets verbatim.
 * {@code FR-JD-05} rewrites them, and {@code PRD §7.2} requires that every
 * rewrite be diffable against its source — without the original text there is
 * no way to prove the model did not invent a responsibility.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Entity
@Table(name = "parsed_experience")
public class ParsedExperience {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "parse_id", nullable = false, updatable = false)
    private UUID parseId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "company", length = 200)
    private String company;

    @Column(name = "job_title", length = 200)
    private String jobTitle;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "confidence", nullable = false)
    private short confidence;

    @Column(name = "source_line_start")
    private Integer sourceLineStart;

    @Column(name = "source_line_end")
    private Integer sourceLineEnd;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected ParsedExperience() {
    }

    /**
     * Builds a persistable row from an extraction result.
     *
     * @param parseId the parse this belongs to
     * @param userId  the owner
     * @param entry   what the extractor found
     * @return the entity
     */
    public static ParsedExperience from(UUID parseId, UUID userId, ExperienceEntry entry) {
        ParsedExperience experience = new ParsedExperience();
        experience.parseId = parseId;
        experience.userId = userId;
        experience.company = ColumnWidths.fit(entry.company(), 200);
        experience.jobTitle = ColumnWidths.fit(entry.jobTitle(), 200);
        experience.startDate = entry.startDate();
        experience.endDate = entry.endDate();
        experience.current = entry.current();
        // TEXT column: kept whole, because a rewrite is diffed against it.
        experience.description = entry.description();
        experience.confidence = (short) entry.confidence();
        experience.sourceLineStart = entry.lineStart();
        experience.sourceLineEnd = entry.lineEnd();
        return experience;
    }

    // --- accessors ---------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public UUID getParseId() {
        return parseId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCompany() {
        return company;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isCurrent() {
        return current;
    }

    public String getDescription() {
        return description;
    }

    public short getConfidence() {
        return confidence;
    }

    public Integer getSourceLineStart() {
        return sourceLineStart;
    }

    public Integer getSourceLineEnd() {
        return sourceLineEnd;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ParsedExperience experience)) {
            return false;
        }
        return id != null && id.equals(experience.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ParsedExperience.class);
    }

    /**
     * Diagnostic representation.
     *
     * <p>Names neither the employer nor the description. Employment history is
     * personal data, and this string reaches log lines that leave the process.
     */
    @Override
    public String toString() {
        return "ParsedExperience{id=" + id + ", hasCompany=" + (company != null)
                + ", hasTitle=" + (jobTitle != null) + ", current=" + current
                + ", confidence=" + confidence + "}";
    }
}
