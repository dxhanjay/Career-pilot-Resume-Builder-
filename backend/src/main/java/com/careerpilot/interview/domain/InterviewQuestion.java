package com.careerpilot.interview.domain;

import com.careerpilot.interview.domain.InterviewEnums.QuestionKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One question inside a session, fixed at generation time.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Entity
@Table(name = "interview_questions")
public class InterviewQuestion {

    private static final int MAX_FOCUS_SKILL = 100;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "position", nullable = false, updatable = false)
    private short position;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30, updatable = false)
    private QuestionKind kind;

    @Column(name = "prompt", nullable = false, columnDefinition = "text", updatable = false)
    private String prompt;

    @Column(name = "focus_skill", length = MAX_FOCUS_SKILL, updatable = false)
    private String focusSkill;

    @Column(name = "rationale", columnDefinition = "text", updatable = false)
    private String rationale;

    /**
     * Newline-separated rather than a JSONB array.
     *
     * <p>These are display strings that are never queried, filtered, or joined
     * on. A JSONB column would buy indexing nothing reads and cost every caller
     * a serialisation step.
     */
    @Column(name = "expected_points", columnDefinition = "text", updatable = false)
    private String expectedPoints;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InterviewQuestion() {
    }

    public static InterviewQuestion from(UUID sessionId, UUID userId, int position,
                                         QuestionBlueprint.GeneratedQuestion generated) {
        InterviewQuestion question = new InterviewQuestion();
        question.sessionId = sessionId;
        question.userId = userId;
        question.position = (short) position;
        question.kind = generated.kind();
        question.prompt = generated.prompt();
        question.focusSkill = fit(generated.focusSkill());
        question.rationale = generated.rationale();
        question.expectedPoints = generated.expectedPoints() == null
                ? null
                : String.join("\n", generated.expectedPoints());
        return question;
    }

    private static String fit(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_FOCUS_SKILL ? value : value.substring(0, MAX_FOCUS_SKILL);
    }

    /** The expected points as a list, empty rather than null when unset. */
    public List<String> expectedPointList() {
        if (expectedPoints == null || expectedPoints.isBlank()) {
            return List.of();
        }
        return Arrays.stream(expectedPoints.split("\n"))
                .map(String::strip)
                .filter(point -> !point.isEmpty())
                .toList();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public short getPosition() {
        return position;
    }

    public QuestionKind getKind() {
        return kind;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getFocusSkill() {
        return focusSkill;
    }

    public String getRationale() {
        return rationale;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InterviewQuestion question)) {
            return false;
        }
        return id != null && id.equals(question.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(InterviewQuestion.class);
    }

    @Override
    public String toString() {
        return "InterviewQuestion{position=" + position + ", kind=" + kind + "}";
    }
}
