package com.careerpilot.ats.application;

import com.careerpilot.ats.application.dto.AtsAnalysisResponse;
import com.careerpilot.ats.application.dto.AtsHistoryResponse;
import com.careerpilot.ats.domain.AtsAnalysis;
import com.careerpilot.ats.domain.AtsAssessment;
import com.careerpilot.ats.domain.AtsFinding;
import com.careerpilot.ats.domain.AtsRubric;
import com.careerpilot.ats.domain.RuleFinding;
import com.careerpilot.ats.infrastructure.AtsAnalysisRepository;
import com.careerpilot.ats.infrastructure.AtsFindingRepository;
import com.careerpilot.common.exception.BusinessRuleViolationException;
import com.careerpilot.common.exception.ResourceNotFoundException;
import com.careerpilot.parsing.application.ResumeSnapshotProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Scores a parsed resume against the ATS rubric and stores the result.
 *
 * <p>Runs synchronously rather than through the job engine. The rubric is pure
 * CPU over text already in the database — typically single-digit milliseconds —
 * and making the client poll for a result that is ready before the response
 * would have been written buys nothing but latency and a spinner.
 *
 * <p>Depends on the parsing module's <em>application</em> layer rather than its
 * repositories. Reaching into another module's tables is how a modular monolith
 * quietly becomes a single tangled one.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Service
public class AtsAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AtsAnalysisService.class);

    private final ResumeSnapshotProvider snapshotProvider;
    private final AtsAnalysisRepository analysisRepository;
    private final AtsFindingRepository findingRepository;

    public AtsAnalysisService(ResumeSnapshotProvider snapshotProvider,
                              AtsAnalysisRepository analysisRepository,
                              AtsFindingRepository findingRepository) {
        this.snapshotProvider = snapshotProvider;
        this.analysisRepository = analysisRepository;
        this.findingRepository = findingRepository;
    }

    /**
     * Runs the rubric and stores a new analysis.
     *
     * <p>Always inserts. Re-analysing the same resume after an edit is the
     * point, and an overwrite would erase the evidence that it improved.
     *
     * @throws BusinessRuleViolationException if the resume has no successful parse
     */
    @Transactional
    public AtsAnalysisResponse analyze(UUID resumeId, UUID userId) {
        long startedAt = System.nanoTime();

        ResumeSnapshotProvider.Snapshot snapshot = snapshotProvider.forResume(resumeId, userId);

        AtsAssessment assessment = AtsRubric.evaluate(snapshot.resume());
        int durationMs = (int) ((System.nanoTime() - startedAt) / 1_000_000);

        AtsAnalysis analysis = analysisRepository.save(AtsAnalysis.from(
                resumeId, snapshot.parseId(), userId, assessment, durationMs));

        List<AtsFinding> findings = new ArrayList<>(assessment.findings().size());
        int order = 0;
        for (RuleFinding finding : assessment.findings()) {
            findings.add(AtsFinding.from(analysis.getId(), userId, finding, order++));
        }
        findingRepository.saveAll(findings);

        log.info("ATS analysis {} for resume {}: score {} ({}), {} finding(s) in {}ms",
                analysis.getId(), resumeId, assessment.overallScore(),
                assessment.band(), findings.size(), durationMs);

        return AtsAnalysisResponse.from(analysis, findings);
    }

    /**
     * The most recent analysis for a resume.
     *
     * @throws ResourceNotFoundException if the resume has never been analysed
     */
    @Transactional(readOnly = true)
    public AtsAnalysisResponse getLatest(UUID resumeId, UUID userId) {
        AtsAnalysis analysis = analysisRepository.findLatest(resumeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("ATS analysis"));
        return AtsAnalysisResponse.from(analysis,
                findingRepository.findByAnalysisIdOrderByDisplayOrderAsc(analysis.getId()));
    }

    /** Whether a resume has ever been analysed, without loading the findings. */
    @Transactional(readOnly = true)
    public boolean hasAnalysis(UUID resumeId, UUID userId) {
        return analysisRepository.findLatest(resumeId, userId).isPresent();
    }

    @Transactional(readOnly = true)
    public AtsAnalysisResponse getById(UUID analysisId, UUID userId) {
        AtsAnalysis analysis = analysisRepository.findByIdAndUserId(analysisId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("ATS analysis"));
        return AtsAnalysisResponse.from(analysis,
                findingRepository.findByAnalysisIdOrderByDisplayOrderAsc(analysis.getId()));
    }

    @Transactional(readOnly = true)
    public AtsHistoryResponse getHistory(UUID resumeId, UUID userId) {
        return AtsHistoryResponse.of(resumeId,
                analysisRepository.findByResumeIdAndUserIdOrderByCreatedAtDesc(resumeId, userId));
    }

    /**
     * Analyses a resume immediately after it parses, swallowing any failure.
     *
     * <p>Called from the parse job so a report is waiting the moment the user
     * opens the resume. A failure here must never fail the parse: the extracted
     * text is valuable on its own, and the user can retry the analysis from the
     * UI.
     */
    @Transactional
    public void analyzeQuietly(UUID resumeId, UUID userId) {
        try {
            analyze(resumeId, userId);
        } catch (RuntimeException e) {
            log.warn("Automatic ATS analysis failed for resume {}: {}", resumeId, e.toString());
        }
    }

}
