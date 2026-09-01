package com.careerpilot.interview.domain;

import com.careerpilot.interview.domain.InterviewEnums.Focus;
import com.careerpilot.interview.domain.InterviewEnums.PerformanceBand;
import com.careerpilot.interview.domain.InterviewEnums.SessionStatus;
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
 * One mock interview.
 *
 * <p>State transitions live here rather than in the service, so the rules about
 * when a session may be answered or completed are enforced wherever it is loaded
 * from.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Entity
@Table(name = "interview_sessions")
public class InterviewSession {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "resume_id", updatable = false)
    private UUID resumeId;

    @Column(name = "job_description_id", updatable = false)
    private UUID jobDescriptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "focus", nullable = false, length = 30, updatable = false)
    private Focus focus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status = SessionStatus.IN_PROGRESS;

    @Column(name = "question_count", nullable = false, updatable = false)
    private short questionCount;

    @Column(name = "answered_count", nullable = false)
    private short answeredCount;

    @Column(name = "overall_score")
    private Short overallScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "band", length = 20)
    private PerformanceBand band;

    @Column(name = "blueprint_version", nullable = false, length = 20, updatable = false)
    private String blueprintVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected InterviewSession() {
    }

    public InterviewSession(UUID userId, UUID resumeId, UUID jobDescriptionId, Focus focus,
                            int questionCount, String blueprintVersion) {
        this.userId = userId;
        this.resumeId = resumeId;
        this.jobDescriptionId = jobDescriptionId;
        this.focus = focus;
        this.questionCount = (short) questionCount;
        this.answeredCount = 0;
        this.blueprintVersion = blueprintVersion;
    }

    /**
     * Records that a question was answered for the first time.
     *
     * <p>Re-answering an already-answered question does not move this counter —
     * the session would otherwise report more answers than it has questions.
     *
     * @param firstAnswerForQuestion whether this replaced nothing
     */
    public void recordAnswer(boolean firstAnswerForQuestion) {
        if (!status.isOpen()) {
            throw new IllegalStateException("This interview has already been completed");
        }
        if (firstAnswerForQuestion && answeredCount < questionCount) {
            answeredCount++;
        }
    }

    /**
     * Closes the session with its final score.
     *
     * @throws IllegalStateException if it is already closed
     */
    public void complete(int score) {
        if (!status.isOpen()) {
            throw new IllegalStateException("This interview has already been completed");
        }
        this.overallScore = (short) score;
        this.band = PerformanceBand.of(score);
        this.status = SessionStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    /** Abandoned rather than deleted: a walked-away interview is still a data point. */
    public void abandon() {
        if (status.isOpen()) {
            this.status = SessionStatus.ABANDONED;
            this.completedAt = Instant.now();
        }
    }

    public boolean isComplete() {
        return answeredCount >= questionCount;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getResumeId() {
        return resumeId;
    }

    public UUID getJobDescriptionId() {
        return jobDescriptionId;
    }

    public Focus getFocus() {
        return focus;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public short getQuestionCount() {
        return questionCount;
    }

    public short getAnsweredCount() {
        return answeredCount;
    }

    public Short getOverallScore() {
        return overallScore;
    }

    public PerformanceBand getBand() {
        return band;
    }

    public String getBlueprintVersion() {
        return blueprintVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InterviewSession session)) {
            return false;
        }
        return id != null && id.equals(session.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(InterviewSession.class);
    }

    @Override
    public String toString() {
        return "InterviewSession{id=" + id + ", focus=" + focus + ", status=" + status
                + ", " + answeredCount + "/" + questionCount + "}";
    }
}
