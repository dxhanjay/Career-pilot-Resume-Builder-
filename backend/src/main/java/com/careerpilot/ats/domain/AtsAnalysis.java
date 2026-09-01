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
 * A stored analysis run.
 *
 * <p>Immutable after construction. There is no setter and no update path: a new
 * analysis is a new row, which is what makes the score history a record of the
 * resume improving rather than of the last opinion held about it.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Entity
@Table(name = "ats_analyses")
public class AtsAnalysis {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "resume_id", nullable = false, updatable = false)
    private UUID resumeId;

    @Column(name = "parse_id", nullable = false, updatable = false)
    private UUID parseId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "overall_score", nullable = false, updatable = false)
    private short overallScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "band", nullable = false, length = 20, updatable = false)
    private ScoreBand band;

    @Column(name = "parseability_score", nullable = false, updatable = false)
    private short parseabilityScore;

    @Column(name = "structure_score", nullable = false, updatable = false)
    private short structureScore;

    @Column(name = "content_score", nullable = false, updatable = false)
    private short contentScore;

    @Column(name = "skills_score", nullable = false, updatable = false)
    private short skillsScore;

    @Column(name = "contact_score", nullable = false, updatable = false)
    private short contactScore;

    @Column(name = "rubric_version", nullable = false, length = 20, updatable = false)
    private String rubricVersion;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AtsAnalysis() {
    }

    public static AtsAnalysis from(UUID resumeId, UUID parseId, UUID userId,
                                   AtsAssessment assessment, int durationMs) {
        AtsAnalysis analysis = new AtsAnalysis();
        analysis.resumeId = resumeId;
        analysis.parseId = parseId;
        analysis.userId = userId;
        analysis.overallScore = (short) assessment.overallScore();
        analysis.band = assessment.band();
        analysis.parseabilityScore = (short) assessment.scoreFor(AtsCategory.PARSEABILITY);
        analysis.structureScore = (short) assessment.scoreFor(AtsCategory.STRUCTURE);
        analysis.contentScore = (short) assessment.scoreFor(AtsCategory.CONTENT);
        analysis.skillsScore = (short) assessment.scoreFor(AtsCategory.SKILLS);
        analysis.contactScore = (short) assessment.scoreFor(AtsCategory.CONTACT);
        analysis.rubricVersion = assessment.rubricVersion();
        analysis.durationMs = durationMs;
        return analysis;
    }

    public UUID getId() {
        return id;
    }

    public UUID getResumeId() {
        return resumeId;
    }

    public UUID getParseId() {
        return parseId;
    }

    public UUID getUserId() {
        return userId;
    }

    public short getOverallScore() {
        return overallScore;
    }

    public ScoreBand getBand() {
        return band;
    }

    public short getParseabilityScore() {
        return parseabilityScore;
    }

    public short getStructureScore() {
        return structureScore;
    }

    public short getContentScore() {
        return contentScore;
    }

    public short getSkillsScore() {
        return skillsScore;
    }

    public short getContactScore() {
        return contactScore;
    }

    public String getRubricVersion() {
        return rubricVersion;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AtsAnalysis analysis)) {
            return false;
        }
        return id != null && id.equals(analysis.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(AtsAnalysis.class);
    }

    @Override
    public String toString() {
        return "AtsAnalysis{id=" + id + ", score=" + overallScore + ", band=" + band + "}";
    }
}
