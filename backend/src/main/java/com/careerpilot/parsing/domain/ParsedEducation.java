package com.careerpilot.parsing.domain;

import com.careerpilot.parsing.domain.extract.EducationEntry;
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
 * One qualification extracted from a parse.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Entity
@Table(name = "parsed_education")
public class ParsedEducation {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "parse_id", nullable = false, updatable = false)
    private UUID parseId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "institution", length = 200)
    private String institution;

    @Column(name = "degree", length = 150)
    private String degree;

    @Column(name = "field_of_study", length = 150)
    private String fieldOfStudy;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "grade", length = 20)
    private String grade;

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
    protected ParsedEducation() {
    }

    /**
     * Builds a persistable row from an extraction result.
     *
     * @param parseId the parse this belongs to
     * @param userId  the owner
     * @param entry   what the extractor found
     * @return the entity
     */
    public static ParsedEducation from(UUID parseId, UUID userId, EducationEntry entry) {
        ParsedEducation education = new ParsedEducation();
        education.parseId = parseId;
        education.userId = userId;
        education.institution = ColumnWidths.fit(entry.institution(), 200);
        education.degree = ColumnWidths.fit(entry.degree(), 150);
        education.fieldOfStudy = ColumnWidths.fit(entry.fieldOfStudy(), 150);
        education.startDate = entry.startDate();
        education.endDate = entry.endDate();
        education.grade = ColumnWidths.fit(entry.grade(), 20);
        education.confidence = (short) entry.confidence();
        education.sourceLineStart = entry.lineStart();
        education.sourceLineEnd = entry.lineEnd();
        return education;
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

    public String getInstitution() {
        return institution;
    }

    public String getDegree() {
        return degree;
    }

    public String getFieldOfStudy() {
        return fieldOfStudy;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getGrade() {
        return grade;
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
        if (!(other instanceof ParsedEducation education)) {
            return false;
        }
        return id != null && id.equals(education.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ParsedEducation.class);
    }

    /**
     * Diagnostic representation.
     *
     * <p>Names no institution and no field of study. Together with a user id
     * those identify a real person's academic record, and this string reaches
     * log lines that leave the process.
     */
    @Override
    public String toString() {
        return "ParsedEducation{id=" + id + ", hasInstitution=" + (institution != null)
                + ", hasDegree=" + (degree != null) + ", confidence=" + confidence + "}";
    }
}
