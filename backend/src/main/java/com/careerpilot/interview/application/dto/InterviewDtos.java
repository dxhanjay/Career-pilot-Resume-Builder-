package com.careerpilot.interview.application.dto;

import com.careerpilot.interview.domain.InterviewAnswer;
import com.careerpilot.interview.domain.InterviewEnums;
import com.careerpilot.interview.domain.InterviewQuestion;
import com.careerpilot.interview.domain.InterviewSession;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The interview module's request and response bodies.
 *
 * <p>One file because they form a single contract and are only ever read
 * together.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public final class InterviewDtos {

    private InterviewDtos() {
    }

    // ------------------------------------------------------------------
    // Requests
    // ------------------------------------------------------------------

    /**
     * @param resumeId         which resume to draw questions from; optional, but a
     *                         session without one can only ask generic questions
     * @param jobDescriptionId the posting to target; required for JOB_SPECIFIC
     * @param questionCount    3-12. Longer than twelve and candidates stop
     *                         answering seriously, which makes the report worse
     *                         rather than more thorough.
     */
    @Schema(name = "StartInterviewRequest")
    public record StartSession(
            UUID resumeId,
            UUID jobDescriptionId,

            @NotNull(message = "Choose what to focus on")
            InterviewEnums.Focus focus,

            @Min(value = 3, message = "An interview needs at least 3 questions")
            @Max(value = 12, message = "12 questions is the maximum")
            Integer questionCount
    ) {
        public int resolvedCount() {
            return questionCount == null ? 6 : questionCount;
        }
    }

    @Schema(name = "SubmitAnswerRequest")
    public record SubmitAnswer(
            @NotBlank(message = "Write an answer before submitting")
            @Size(max = 8000, message = "Answers are limited to 8000 characters")
            String answer
    ) {
    }

    // ------------------------------------------------------------------
    // Responses
    // ------------------------------------------------------------------

    /** A question as the candidate sees it while answering. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "InterviewQuestionResponse")
    public record QuestionView(
            UUID id,
            int position,
            String kind,
            String kindLabel,
            String prompt,
            String focusSkill,
            String rationale,
            List<String> expectedPoints,
            AnswerView answer
    ) {
        /**
         * @param revealExpected whether to include the cues a good answer covers.
         *                       Withheld until the question is answered — showing
         *                       them first turns practice into transcription.
         */
        public static QuestionView from(InterviewQuestion question, InterviewAnswer answer,
                                        boolean revealExpected) {
            return new QuestionView(
                    question.getId(),
                    question.getPosition(),
                    question.getKind().name(),
                    question.getKind().displayName(),
                    question.getPrompt(),
                    question.getFocusSkill(),
                    question.getRationale(),
                    revealExpected ? question.expectedPointList() : null,
                    answer == null ? null : AnswerView.from(answer));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "InterviewAnswerResponse")
    public record AnswerView(
            UUID id,
            UUID questionId,
            String answerText,
            int wordCount,
            int score,
            int structureScore,
            int specificityScore,
            int relevanceScore,
            int clarityScore,
            List<String> strengths,
            List<String> improvements,
            Instant createdAt
    ) {
        public static AnswerView from(InterviewAnswer answer) {
            return new AnswerView(
                    answer.getId(),
                    answer.getQuestionId(),
                    answer.getAnswerText(),
                    answer.getWordCount(),
                    answer.getScore(),
                    answer.getStructureScore(),
                    answer.getSpecificityScore(),
                    answer.getRelevanceScore(),
                    answer.getClarityScore(),
                    answer.strengthList(),
                    answer.improvementList(),
                    answer.getCreatedAt());
        }
    }

    /** A session with its questions, and answers where they exist. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "InterviewSessionResponse")
    public record SessionView(
            UUID id,
            UUID resumeId,
            UUID jobDescriptionId,
            String focus,
            String focusLabel,
            String focusDescription,
            String status,
            int questionCount,
            int answeredCount,
            Integer overallScore,
            String band,
            String bandLabel,
            String bandSummary,
            List<QuestionView> questions,
            Report report,
            Instant createdAt,
            Instant completedAt
    ) {
        public static SessionView of(InterviewSession session, List<QuestionView> questions,
                                     Report report) {
            InterviewEnums.PerformanceBand band = session.getBand();
            return new SessionView(
                    session.getId(),
                    session.getResumeId(),
                    session.getJobDescriptionId(),
                    session.getFocus().name(),
                    session.getFocus().displayName(),
                    session.getFocus().description(),
                    session.getStatus().name(),
                    session.getQuestionCount(),
                    session.getAnsweredCount(),
                    session.getOverallScore() == null ? null : (int) session.getOverallScore(),
                    band == null ? null : band.name(),
                    band == null ? null : band.displayName(),
                    band == null ? null : band.summary(),
                    questions,
                    report,
                    session.getCreatedAt(),
                    session.getCompletedAt());
        }
    }

    /**
     * The closing report.
     *
     * @param axisScores      the four rubric axes averaged across the session, so a
     *                        candidate can see whether their problem is structure
     *                        or evidence
     * @param topStrengths    recurring things done well
     * @param topImprovements recurring things to fix, most frequent first
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "InterviewReport")
    public record Report(
            int overallScore,
            String band,
            String bandLabel,
            String bandSummary,
            List<AxisScore> axisScores,
            List<String> topStrengths,
            List<String> topImprovements,
            UUID strongestQuestionId,
            UUID weakestQuestionId
    ) {
    }

    public record AxisScore(String axis, String displayName, int score, String description) {
    }

    /** One row in the session list. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "InterviewSessionSummary")
    public record SessionSummary(
            UUID id,
            String focus,
            String focusLabel,
            String status,
            int questionCount,
            int answeredCount,
            Integer overallScore,
            String band,
            Instant createdAt,
            Instant completedAt
    ) {
        public static SessionSummary from(InterviewSession session) {
            return new SessionSummary(
                    session.getId(),
                    session.getFocus().name(),
                    session.getFocus().displayName(),
                    session.getStatus().name(),
                    session.getQuestionCount(),
                    session.getAnsweredCount(),
                    session.getOverallScore() == null ? null : (int) session.getOverallScore(),
                    session.getBand() == null ? null : session.getBand().name(),
                    session.getCreatedAt(),
                    session.getCompletedAt());
        }
    }
}
