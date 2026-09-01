package com.careerpilot.interview.application;

import com.careerpilot.common.dto.PageResponse;
import com.careerpilot.common.exception.BusinessRuleViolationException;
import com.careerpilot.common.exception.ResourceNotFoundException;
import com.careerpilot.interview.application.dto.InterviewDtos;
import com.careerpilot.interview.domain.AnswerRubric;
import com.careerpilot.interview.domain.InterviewAnswer;
import com.careerpilot.interview.domain.InterviewEnums;
import com.careerpilot.interview.domain.InterviewQuestion;
import com.careerpilot.interview.domain.InterviewSession;
import com.careerpilot.interview.domain.QuestionBlueprint;
import com.careerpilot.interview.infrastructure.InterviewAnswerRepository;
import com.careerpilot.interview.infrastructure.InterviewQuestionRepository;
import com.careerpilot.interview.infrastructure.InterviewSessionRepository;
import com.careerpilot.matching.application.JobMatchingService;
import com.careerpilot.matching.application.dto.MatchResponse;
import com.careerpilot.parsing.application.ResumeSnapshotProvider;
import com.careerpilot.parsing.domain.snapshot.ResumeSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs mock interviews: generates a session, scores each answer as it arrives,
 * and closes with a report.
 *
 * <p>Questions are generated once, at session creation, and stored. Regenerating
 * them per request would mean a candidate who reloads mid-answer loses the
 * question they were part-way through, and a report that claims to be about
 * questions that were never asked.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Service
public class InterviewService {

    private static final Logger log = LoggerFactory.getLogger(InterviewService.class);

    /** Concurrent unfinished sessions per user. Practice, not a backlog. */
    private static final int MAX_OPEN_SESSIONS = 3;

    /** How many gap skills a job-specific session probes. */
    private static final int MAX_GAPS_CONSIDERED = 6;

    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewAnswerRepository answerRepository;
    private final ResumeSnapshotProvider snapshotProvider;
    private final JobMatchingService matchingService;

    public InterviewService(InterviewSessionRepository sessionRepository,
                            InterviewQuestionRepository questionRepository,
                            InterviewAnswerRepository answerRepository,
                            ResumeSnapshotProvider snapshotProvider,
                            JobMatchingService matchingService) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.snapshotProvider = snapshotProvider;
        this.matchingService = matchingService;
    }

    // ------------------------------------------------------------------
    // Session lifecycle
    // ------------------------------------------------------------------

    /**
     * Creates a session and generates its questions.
     *
     * @throws BusinessRuleViolationException if too many sessions are already open
     */
    @Transactional
    public InterviewDtos.SessionView start(UUID userId, InterviewDtos.StartSession request) {
        List<InterviewSession> open = sessionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                userId, InterviewEnums.SessionStatus.IN_PROGRESS);
        if (open.size() >= MAX_OPEN_SESSIONS) {
            throw new BusinessRuleViolationException(
                    "You have " + open.size() + " interviews still in progress. Finish or "
                            + "abandon one before starting another.");
        }

        ResumeSnapshot resume = loadResume(request.resumeId(), userId);
        List<String> gaps = loadGaps(request, userId);

        if (request.focus() == InterviewEnums.Focus.JOB_SPECIFIC
                && request.jobDescriptionId() == null) {
            throw new BusinessRuleViolationException(
                    "A job-specific interview needs a job description to target.");
        }

        int count = request.resolvedCount();
        // Seeded from the session inputs rather than the clock: two runs of the
        // same request produce the same interview, which makes a bug report
        // about a bad question reproducible.
        long seed = java.util.Objects.hash(userId, request.resumeId(),
                request.jobDescriptionId(), request.focus(), count);

        List<QuestionBlueprint.GeneratedQuestion> generated =
                QuestionBlueprint.generate(resume, gaps, request.focus(), count, seed);

        InterviewSession session = sessionRepository.save(new InterviewSession(
                userId, request.resumeId(), request.jobDescriptionId(), request.focus(),
                generated.size(), QuestionBlueprint.VERSION));

        List<InterviewQuestion> questions = new ArrayList<>(generated.size());
        for (int i = 0; i < generated.size(); i++) {
            questions.add(InterviewQuestion.from(session.getId(), userId, i, generated.get(i)));
        }
        questionRepository.saveAll(questions);

        log.info("Started {} interview {} for user {} with {} question(s)",
                request.focus(), session.getId(), userId, questions.size());

        return InterviewDtos.SessionView.of(session,
                questions.stream()
                        .map(question -> InterviewDtos.QuestionView.from(question, null, false))
                        .toList(),
                null);
    }

    @Transactional(readOnly = true)
    public InterviewDtos.SessionView get(UUID sessionId, UUID userId) {
        InterviewSession session = require(sessionId, userId);
        return assemble(session);
    }

    @Transactional(readOnly = true)
    public PageResponse<InterviewDtos.SessionSummary> list(UUID userId, Pageable pageable) {
        return PageResponse.from(
                sessionRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable),
                InterviewDtos.SessionSummary::from);
    }

    /**
     * Scores an answer and stores it.
     *
     * <p>Answering the same question twice replaces the previous answer rather
     * than adding one, so a candidate can rewrite and see the score move.
     */
    @Transactional
    public InterviewDtos.AnswerView answer(UUID sessionId, UUID questionId, UUID userId,
                                           String answerText) {
        InterviewSession session = require(sessionId, userId);
        if (!session.getStatus().isOpen()) {
            throw new BusinessRuleViolationException(
                    "This interview is finished. Start a new one to practise again.");
        }

        InterviewQuestion question = questionRepository.findByIdAndUserId(questionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Question"));
        if (!question.getSessionId().equals(sessionId)) {
            throw new ResourceNotFoundException("Question");
        }

        AnswerRubric.AnswerAssessment assessment = AnswerRubric.evaluate(
                answerText, question.getKind(), question.expectedPointList(),
                question.getFocusSkill());

        InterviewAnswer existing = answerRepository.findByQuestionId(questionId).orElse(null);
        boolean first = existing == null;
        InterviewAnswer answer = first
                ? new InterviewAnswer(questionId, sessionId, userId)
                : existing;
        answer.record(answerText.strip(), assessment);
        answerRepository.save(answer);
        session.recordAnswer(first);

        log.debug("Answer to question {} scored {}", questionId, assessment.overallScore());
        return InterviewDtos.AnswerView.from(answer);
    }

    /**
     * Closes a session and produces its report.
     *
     * @throws BusinessRuleViolationException if nothing has been answered
     */
    @Transactional
    public InterviewDtos.SessionView complete(UUID sessionId, UUID userId) {
        InterviewSession session = require(sessionId, userId);
        if (!session.getStatus().isOpen()) {
            return assemble(session);
        }

        List<InterviewAnswer> answers = answerRepository.findBySessionId(sessionId);
        if (answers.isEmpty()) {
            throw new BusinessRuleViolationException(
                    "Answer at least one question before finishing the interview.");
        }

        // Unanswered questions score zero. Averaging only what was answered would
        // let a candidate answer one question well and be told the interview went
        // strongly, which is the opposite of useful.
        int total = answers.stream().mapToInt(InterviewAnswer::getScore).sum();
        int score = Math.round((float) total / session.getQuestionCount());
        session.complete(Math.max(0, Math.min(100, score)));

        log.info("Completed interview {} for user {}: {} ({} of {} answered)",
                sessionId, userId, session.getOverallScore(),
                answers.size(), session.getQuestionCount());

        return assemble(session);
    }

    @Transactional
    public void abandon(UUID sessionId, UUID userId) {
        require(sessionId, userId).abandon();
    }

    @Transactional(readOnly = true)
    public long countSessions(UUID userId) {
        return sessionRepository.countByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Double averageScore(UUID userId) {
        return sessionRepository.averageScore(userId);
    }

    // ------------------------------------------------------------------
    // Assembly
    // ------------------------------------------------------------------

    private InterviewDtos.SessionView assemble(InterviewSession session) {
        List<InterviewQuestion> questions =
                questionRepository.findBySessionIdOrderByPositionAsc(session.getId());
        Map<UUID, InterviewAnswer> answers = new LinkedHashMap<>();
        answerRepository.findBySessionId(session.getId())
                .forEach(answer -> answers.put(answer.getQuestionId(), answer));

        List<InterviewDtos.QuestionView> views = questions.stream()
                .map(question -> {
                    InterviewAnswer answer = answers.get(question.getId());
                    // Expected points are revealed once the question is answered,
                    // or once the session is closed. Showing them beforehand turns
                    // the exercise into transcription.
                    boolean reveal = answer != null || !session.getStatus().isOpen();
                    return InterviewDtos.QuestionView.from(question, answer, reveal);
                })
                .toList();

        InterviewDtos.Report report = session.getStatus().isOpen()
                ? null
                : buildReport(session, questions, answers);

        return InterviewDtos.SessionView.of(session, views, report);
    }

    private InterviewDtos.Report buildReport(InterviewSession session,
                                             List<InterviewQuestion> questions,
                                             Map<UUID, InterviewAnswer> answers) {
        if (answers.isEmpty()) {
            return null;
        }
        List<InterviewAnswer> scored = new ArrayList<>(answers.values());

        int structure = average(scored, InterviewAnswer::getStructureScore);
        int specificity = average(scored, InterviewAnswer::getSpecificityScore);
        int relevance = average(scored, InterviewAnswer::getRelevanceScore);
        int clarity = average(scored, InterviewAnswer::getClarityScore);

        InterviewEnums.PerformanceBand band = session.getBand() == null
                ? InterviewEnums.PerformanceBand.of(0)
                : session.getBand();

        InterviewAnswer best = scored.stream()
                .max(Comparator.comparingInt(InterviewAnswer::getScore)).orElse(null);
        InterviewAnswer worst = scored.stream()
                .min(Comparator.comparingInt(InterviewAnswer::getScore)).orElse(null);

        return new InterviewDtos.Report(
                session.getOverallScore() == null ? 0 : session.getOverallScore(),
                band.name(),
                band.displayName(),
                band.summary(),
                List.of(
                        new InterviewDtos.AxisScore("STRUCTURE", "Structure", structure,
                                "Whether your answers move from situation to action to result."),
                        new InterviewDtos.AxisScore("SPECIFICITY", "Specificity", specificity,
                                "Whether anything you said could be checked — numbers, names, "
                                        + "artefacts."),
                        new InterviewDtos.AxisScore("RELEVANCE", "Relevance", relevance,
                                "Whether you covered what each question was actually asking for."),
                        new InterviewDtos.AxisScore("CLARITY", "Clarity", clarity,
                                "Length, hedging, and how easy the answer is to follow aloud.")),
                mostCommon(scored.stream().flatMap(a -> a.strengthList().stream()).toList(), 3),
                mostCommon(scored.stream().flatMap(a -> a.improvementList().stream()).toList(), 4),
                best == null ? null : best.getQuestionId(),
                worst == null ? null : worst.getQuestionId());
    }

    /**
     * The most frequently repeated pieces of feedback.
     *
     * <p>Frequency is the signal: advice that appeared once is about one answer,
     * advice that appeared four times is about how the candidate interviews.
     */
    private List<String> mostCommon(List<String> lines, int limit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        lines.forEach(line -> counts.merge(line, 1, Integer::sum));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private int average(List<InterviewAnswer> answers,
                        java.util.function.ToIntFunction<InterviewAnswer> field) {
        return (int) Math.round(answers.stream().mapToInt(field).average().orElse(0));
    }

    private ResumeSnapshot loadResume(UUID resumeId, UUID userId) {
        if (resumeId == null) {
            return null;
        }
        try {
            return snapshotProvider.forResume(resumeId, userId).resume();
        } catch (RuntimeException e) {
            // An unparsed resume is not a reason to refuse an interview — it just
            // means the questions will be generic rather than personal.
            log.debug("No usable parse for resume {}; generating generic questions", resumeId);
            return null;
        }
    }

    private List<String> loadGaps(InterviewDtos.StartSession request, UUID userId) {
        if (request.jobDescriptionId() == null || request.resumeId() == null) {
            return List.of();
        }
        try {
            MatchResponse match = matchingService.match(
                    request.jobDescriptionId(), request.resumeId(), userId);
            return match.missing().stream()
                    .filter(MatchResponse.SkillLine::required)
                    .limit(MAX_GAPS_CONSIDERED)
                    .map(MatchResponse.SkillLine::name)
                    .toList();
        } catch (RuntimeException e) {
            log.debug("Could not compute gaps for interview: {}", e.toString());
            return List.of();
        }
    }

    private InterviewSession require(UUID sessionId, UUID userId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview session"));
    }
}
