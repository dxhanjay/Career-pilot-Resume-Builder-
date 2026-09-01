package com.careerpilot.matching.domain;

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
 * One stored comparison of a resume against a posting.
 *
 * <p>Append-only, for the same reason {@code ats_analyses} is: re-matching after
 * an edit is how a candidate finds out whether the edit helped, and that needs
 * the previous number to still exist.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Entity
@Table(name = "jd_matches")
public class JdMatch {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "job_description_id", nullable = false, updatable = false)
    private UUID jobDescriptionId;

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
    private MatchBand band;

    @Column(name = "required_skill_score", nullable = false, updatable = false)
    private short requiredSkillScore;

    @Column(name = "optional_skill_score", nullable = false, updatable = false)
    private short optionalSkillScore;

    @Column(name = "title_score", nullable = false, updatable = false)
    private short titleScore;

    @Column(name = "experience_score", nullable = false, updatable = false)
    private short experienceScore;

    @Column(name = "matched_count", nullable = false, updatable = false)
    private short matchedCount;

    @Column(name = "missing_count", nullable = false, updatable = false)
    private short missingCount;

    @Column(name = "rubric_version", nullable = false, length = 20, updatable = false)
    private String rubricVersion;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected JdMatch() {
    }

    public static JdMatch from(UUID jobDescriptionId, UUID resumeId, UUID parseId, UUID userId,
                               MatchOutcome outcome, int durationMs) {
        JdMatch match = new JdMatch();
        match.jobDescriptionId = jobDescriptionId;
        match.resumeId = resumeId;
        match.parseId = parseId;
        match.userId = userId;
        match.overallScore = (short) outcome.overallScore();
        match.band = outcome.band();
        match.requiredSkillScore = (short) outcome.requiredSkillScore();
        match.optionalSkillScore = (short) outcome.optionalSkillScore();
        match.titleScore = (short) outcome.titleScore();
        match.experienceScore = (short) outcome.experienceScore();
        match.matchedCount = (short) outcome.matched().size();
        match.missingCount = (short) outcome.missing().size();
        match.rubricVersion = outcome.rubricVersion();
        match.durationMs = durationMs;
        return match;
    }

    public UUID getId() {
        return id;
    }

    public UUID getJobDescriptionId() {
        return jobDescriptionId;
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

    public MatchBand getBand() {
        return band;
    }

    public short getRequiredSkillScore() {
        return requiredSkillScore;
    }

    public short getOptionalSkillScore() {
        return optionalSkillScore;
    }

    public short getTitleScore() {
        return titleScore;
    }

    public short getExperienceScore() {
        return experienceScore;
    }

    public short getMatchedCount() {
        return matchedCount;
    }

    public short getMissingCount() {
        return missingCount;
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
        if (!(other instanceof JdMatch match)) {
            return false;
        }
        return id != null && id.equals(match.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(JdMatch.class);
    }

    @Override
    public String toString() {
        return "JdMatch{id=" + id + ", score=" + overallScore + ", band=" + band + "}";
    }
}
