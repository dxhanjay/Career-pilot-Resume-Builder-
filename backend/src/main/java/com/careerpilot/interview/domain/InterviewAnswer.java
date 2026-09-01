package com.careerpilot.interview.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A candidate's answer and its assessment.
 *
 * <p>Mutable, unlike the other scored records in this codebase. Re-answering a
 * question during practice is the whole point of practice, so the row is
 * updated in place and {@code uq_interview_answers_question} enforces that there
 * is exactly one per question.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Entity
@Table(name = "interview_answers")
public class InterviewAnswer {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "answer_text", nullable = false, columnDefinition = "text")
    private String answerText;

    @Column(name = "word_count", nullable = false)
    private int wordCount;

    @Column(name = "score", nullable = false)
    private short score;

    @Column(name = "structure_score", nullable = false)
    private short structureScore;

    @Column(name = "specificity_score", nullable = false)
    private short specificityScore;

    @Column(name = "relevance_score", nullable = false)
    private short relevanceScore;

    @Column(name = "clarity_score", nullable = false)
    private short clarityScore;

    @Column(name = "strengths", columnDefinition = "text")
    private String strengths;

    @Column(name = "improvements", columnDefinition = "text")
    private String improvements;

    @Column(name = "rubric_version", nullable = false, length = 20)
    private String rubricVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected InterviewAnswer() {
    }

    public InterviewAnswer(UUID questionId, UUID sessionId, UUID userId) {
        this.questionId = questionId;
        this.sessionId = sessionId;
        this.userId = userId;
    }

    /** Stores an answer and its assessment, replacing whatever was there before. */
    public void record(String answerText, AnswerRubric.AnswerAssessment assessment) {
        this.answerText = answerText;
        this.wordCount = assessment.wordCount();
        this.score = (short) assessment.overallScore();
        this.structureScore = (short) assessment.structureScore();
        this.specificityScore = (short) assessment.specificityScore();
        this.relevanceScore = (short) assessment.relevanceScore();
        this.clarityScore = (short) assessment.clarityScore();
        this.strengths = String.join("\n", assessment.strengths());
        this.improvements = String.join("\n", assessment.improvements());
        this.rubricVersion = assessment.rubricVersion();
        this.updatedAt = Instant.now();
    }

    public List<String> strengthList() {
        return split(strengths);
    }

    public List<String> improvementList() {
        return split(improvements);
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\n"))
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    public UUID getId() {
        return id;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getAnswerText() {
        return answerText;
    }

    public int getWordCount() {
        return wordCount;
    }

    public short getScore() {
        return score;
    }

    public short getStructureScore() {
        return structureScore;
    }

    public short getSpecificityScore() {
        return specificityScore;
    }

    public short getRelevanceScore() {
        return relevanceScore;
    }

    public short getClarityScore() {
        return clarityScore;
    }

    public String getRubricVersion() {
        return rubricVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InterviewAnswer answer)) {
            return false;
        }
        return id != null && id.equals(answer.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(InterviewAnswer.class);
    }

    @Override
    public String toString() {
        return "InterviewAnswer{questionId=" + questionId + ", score=" + score + "}";
    }
}
