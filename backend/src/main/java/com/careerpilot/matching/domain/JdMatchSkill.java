package com.careerpilot.matching.domain;

import com.careerpilot.parsing.domain.extract.SkillCategory;
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
 * One skill verdict inside a stored match, with the quote from each document.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Entity
@Table(name = "jd_match_skills")
public class JdMatchSkill {

    private static final int MAX_NAME = 100;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "normalized_name", nullable = false, length = MAX_NAME, updatable = false)
    private String normalizedName;

    @Column(name = "display_name", nullable = false, length = MAX_NAME, updatable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30, updatable = false)
    private SkillCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, updatable = false)
    private SkillVerdict status;

    @Column(name = "required", nullable = false, updatable = false)
    private boolean required;

    @Column(name = "priority", nullable = false, updatable = false)
    private short priority;

    @Column(name = "resume_evidence", columnDefinition = "text", updatable = false)
    private String resumeEvidence;

    @Column(name = "resume_line")
    private Integer resumeLine;

    @Column(name = "jd_evidence", columnDefinition = "text", updatable = false)
    private String jdEvidence;

    @Column(name = "jd_line")
    private Integer jdLine;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected JdMatchSkill() {
    }

    public static JdMatchSkill from(UUID matchId, UUID userId,
                                    MatchOutcome.SkillComparison comparison) {
        JdMatchSkill skill = new JdMatchSkill();
        skill.matchId = matchId;
        skill.userId = userId;
        skill.normalizedName = fit(comparison.normalizedName());
        skill.displayName = fit(comparison.displayName());
        skill.category = comparison.category();
        skill.status = comparison.verdict();
        skill.required = comparison.required();
        skill.priority = (short) comparison.priority();
        skill.resumeEvidence = comparison.resumeEvidence();
        skill.resumeLine = comparison.resumeLine();
        skill.jdEvidence = comparison.jdEvidence();
        skill.jdLine = comparison.jdLine();
        return skill;
    }

    private static String fit(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_NAME ? value : value.substring(0, MAX_NAME);
    }

    public UUID getId() {
        return id;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public SkillCategory getCategory() {
        return category;
    }

    public SkillVerdict getStatus() {
        return status;
    }

    public boolean isRequired() {
        return required;
    }

    public short getPriority() {
        return priority;
    }

    public String getResumeEvidence() {
        return resumeEvidence;
    }

    public Integer getResumeLine() {
        return resumeLine;
    }

    public String getJdEvidence() {
        return jdEvidence;
    }

    public Integer getJdLine() {
        return jdLine;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JdMatchSkill skill)) {
            return false;
        }
        return id != null && id.equals(skill.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(JdMatchSkill.class);
    }

    @Override
    public String toString() {
        return "JdMatchSkill{" + normalizedName + "=" + status + "}";
    }
}
