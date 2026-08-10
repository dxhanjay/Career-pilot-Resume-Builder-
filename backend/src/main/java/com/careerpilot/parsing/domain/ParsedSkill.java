package com.careerpilot.parsing.domain;

import com.careerpilot.parsing.domain.extract.DetectedSkill;
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
 * One skill found in a resume.
 *
 * <p>Stores the candidate's spelling and the canonical form side by side.
 * {@code skillName} is shown back to them unaltered — correcting someone's own
 * resume in the UI reads as a bug. {@code normalizedName} is what
 * {@code FR-JD-03} joins on, and is the reason "ReactJS" on a resume satisfies
 * "React" in a job description.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Entity
@Table(name = "parsed_skills")
public class ParsedSkill {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "parse_id", nullable = false, updatable = false)
    private UUID parseId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "skill_name", nullable = false, length = 100)
    private String skillName;

    @Column(name = "normalized_name", nullable = false, length = 100)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private SkillCategory category;

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
    protected ParsedSkill() {
    }

    /**
     * Builds a persistable row from a detection.
     *
     * @param parseId  the parse this belongs to
     * @param userId   the owner
     * @param detected what the extractor found
     * @return the entity
     */
    public static ParsedSkill from(UUID parseId, UUID userId, DetectedSkill detected) {
        ParsedSkill skill = new ParsedSkill();
        skill.parseId = parseId;
        skill.userId = userId;
        skill.skillName = truncate(detected.name());
        skill.normalizedName = truncate(detected.normalizedName());
        skill.category = detected.category();
        skill.confidence = (short) detected.confidence();
        skill.sourceLineStart = detected.lineStart();
        skill.sourceLineEnd = detected.lineEnd();
        return skill;
    }

    /**
     * Guards the column width.
     *
     * <p>Both names come from a fixed lexicon and cannot realistically exceed
     * 100 characters, but the verbatim form is a substring of untrusted resume
     * text. Truncating here turns a hypothetical malformed input into a short
     * string rather than a failed transaction that loses the whole parse.
     */
    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 100 ? value : value.substring(0, 100);
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

    public String getSkillName() {
        return skillName;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public SkillCategory getCategory() {
        return category;
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
        if (!(other instanceof ParsedSkill skill)) {
            return false;
        }
        return id != null && id.equals(skill.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ParsedSkill.class);
    }

    /**
     * Diagnostic representation.
     *
     * <p>Uses the canonical name rather than the verbatim one. The canonical
     * form comes from our own lexicon and is safe in a log line; the verbatim
     * form is a slice of the candidate's resume.
     */
    @Override
    public String toString() {
        return "ParsedSkill{id=" + id + ", normalized=" + normalizedName
                + ", category=" + category + ", confidence=" + confidence + "}";
    }
}
