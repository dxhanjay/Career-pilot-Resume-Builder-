package com.careerpilot.ats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A persisted {@link RuleFinding}.
 *
 * <p>The evidence columns are the reason this table exists at all. Storing only
 * the score would make the report unreproducible the moment the resume changed,
 * and "your score was 61 last week" is not something a user can act on.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Entity
@Table(name = "ats_findings")
public class AtsFinding {

    /** Column widths from V7. Longer values are trimmed rather than rejected. */
    private static final int MAX_TITLE = 200;
    private static final int MAX_CODE = 60;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "analysis_id", nullable = false, updatable = false)
    private UUID analysisId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "code", nullable = false, length = 60, updatable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30, updatable = false)
    private AtsCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20, updatable = false)
    private AtsSeverity severity;

    @Column(name = "title", nullable = false, length = 200, updatable = false)
    private String title;

    @Column(name = "detail", nullable = false, columnDefinition = "text", updatable = false)
    private String detail;

    @Column(name = "recommendation", columnDefinition = "text", updatable = false)
    private String recommendation;

    @Column(name = "evidence", columnDefinition = "text", updatable = false)
    private String evidence;

    @Column(name = "evidence_line_start")
    private Integer evidenceLineStart;

    @Column(name = "evidence_line_end")
    private Integer evidenceLineEnd;

    @Column(name = "points_lost", nullable = false)
    private short pointsLost;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AtsFinding() {
    }

    public static AtsFinding from(UUID analysisId, UUID userId, RuleFinding finding, int order) {
        AtsFinding entity = new AtsFinding();
        entity.analysisId = analysisId;
        entity.userId = userId;
        entity.code = trim(finding.code(), MAX_CODE);
        entity.category = finding.category();
        entity.severity = finding.severity();
        entity.title = trim(finding.title(), MAX_TITLE);
        entity.detail = finding.detail();
        entity.recommendation = finding.recommendation();
        entity.evidence = finding.evidence();
        entity.evidenceLineStart = finding.lineStart();
        entity.evidenceLineEnd = finding.lineEnd();
        entity.pointsLost = (short) Math.max(0, finding.pointsLost());
        entity.displayOrder = (short) order;
        return entity;
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAnalysisId() {
        return analysisId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCode() {
        return code;
    }

    public AtsCategory getCategory() {
        return category;
    }

    public AtsSeverity getSeverity() {
        return severity;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public String getEvidence() {
        return evidence;
    }

    public Integer getEvidenceLineStart() {
        return evidenceLineStart;
    }

    public Integer getEvidenceLineEnd() {
        return evidenceLineEnd;
    }

    public short getPointsLost() {
        return pointsLost;
    }

    public short getDisplayOrder() {
        return displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AtsFinding finding)) {
            return false;
        }
        return id != null && id.equals(finding.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(AtsFinding.class);
    }

    @Override
    public String toString() {
        return "AtsFinding{code=" + code + ", severity=" + severity + "}";
    }
}
