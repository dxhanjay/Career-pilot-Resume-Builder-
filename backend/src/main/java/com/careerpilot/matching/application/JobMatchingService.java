package com.careerpilot.matching.application;

import com.careerpilot.common.dto.PageResponse;
import com.careerpilot.common.exception.BusinessRuleViolationException;
import com.careerpilot.common.exception.ResourceNotFoundException;
import com.careerpilot.matching.application.dto.JobDescriptionRequests;
import com.careerpilot.matching.application.dto.JobDescriptionResponse;
import com.careerpilot.matching.application.dto.MatchResponse;
import com.careerpilot.matching.domain.JdMatch;
import com.careerpilot.matching.domain.JdMatchSkill;
import com.careerpilot.matching.domain.JobDescription;
import com.careerpilot.matching.domain.JobPosting;
import com.careerpilot.matching.domain.MatchEngine;
import com.careerpilot.matching.domain.MatchOutcome;
import com.careerpilot.matching.infrastructure.JdMatchRepository;
import com.careerpilot.matching.infrastructure.JdMatchSkillRepository;
import com.careerpilot.matching.infrastructure.JobDescriptionRepository;
import com.careerpilot.parsing.application.ResumeSnapshotProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Saves job postings and matches resumes against them.
 *
 * <p>Like ATS scoring, matching runs synchronously. It is a token comparison
 * over two documents already in the database; the job engine exists for work
 * that can genuinely fail halfway, and this cannot.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Service
public class JobMatchingService {

    private static final Logger log = LoggerFactory.getLogger(JobMatchingService.class);

    /**
     * A cap on saved postings, for the same reason resumes have one: every
     * posting is a candidate for repeated matching, and an unbounded list is an
     * unbounded cost.
     */
    private static final int MAX_POSTINGS_PER_USER = 50;

    private final JobDescriptionRepository postingRepository;
    private final JdMatchRepository matchRepository;
    private final JdMatchSkillRepository matchSkillRepository;
    private final ResumeSnapshotProvider snapshotProvider;

    public JobMatchingService(JobDescriptionRepository postingRepository,
                              JdMatchRepository matchRepository,
                              JdMatchSkillRepository matchSkillRepository,
                              ResumeSnapshotProvider snapshotProvider) {
        this.postingRepository = postingRepository;
        this.matchRepository = matchRepository;
        this.matchSkillRepository = matchSkillRepository;
        this.snapshotProvider = snapshotProvider;
    }

    // ------------------------------------------------------------------
    // Postings
    // ------------------------------------------------------------------

    @Transactional
    public JobDescriptionResponse create(UUID userId, JobDescriptionRequests.Create request) {
        if (postingRepository.countActive(userId) >= MAX_POSTINGS_PER_USER) {
            throw new BusinessRuleViolationException(
                    "You have reached the limit of " + MAX_POSTINGS_PER_USER
                            + " saved job descriptions. Delete one to add another.");
        }
        JobDescription posting = postingRepository.save(new JobDescription(
                userId, request.title(), request.company(), request.location(),
                request.sourceUrl(), request.rawText()));
        log.info("Saved job description {} for user {}", posting.getId(), userId);
        return JobDescriptionResponse.detail(posting, null, null, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<JobDescriptionResponse> list(UUID userId, Pageable pageable) {
        return PageResponse.from(postingRepository.findAllActive(userId, pageable),
                posting -> withLatestScore(posting, userId, false));
    }

    @Transactional(readOnly = true)
    public JobDescriptionResponse get(UUID postingId, UUID userId) {
        return withLatestScore(require(postingId, userId), userId, true);
    }

    @Transactional
    public JobDescriptionResponse update(UUID postingId, UUID userId,
                                         JobDescriptionRequests.Update request) {
        JobDescription posting = require(postingId, userId);
        posting.update(request.title(), request.company(), request.location(),
                request.sourceUrl(), request.rawText());
        return withLatestScore(posting, userId, true);
    }

    @Transactional
    public void delete(UUID postingId, UUID userId) {
        require(postingId, userId).softDelete();
        log.info("Soft-deleted job description {} for user {}", postingId, userId);
    }

    @Transactional(readOnly = true)
    public long countPostings(UUID userId) {
        return postingRepository.countActive(userId);
    }

    // ------------------------------------------------------------------
    // Matching
    // ------------------------------------------------------------------

    /**
     * Compares a resume against a posting and stores the result.
     *
     * @throws BusinessRuleViolationException if the resume has no successful parse
     * @throws ResourceNotFoundException      if the posting is not the user's
     */
    @Transactional
    public MatchResponse match(UUID postingId, UUID resumeId, UUID userId) {
        long startedAt = System.nanoTime();

        JobDescription posting = require(postingId, userId);
        ResumeSnapshotProvider.Snapshot snapshot = snapshotProvider.forResume(resumeId, userId);

        JobPosting parsed = JobPosting.parse(posting.getRawText());
        MatchOutcome outcome = MatchEngine.match(snapshot.resume(), parsed);
        int durationMs = (int) ((System.nanoTime() - startedAt) / 1_000_000);

        JdMatch match = matchRepository.save(JdMatch.from(
                postingId, resumeId, snapshot.parseId(), userId, outcome, durationMs));

        List<JdMatchSkill> skills = new ArrayList<>(outcome.skills().size());
        for (MatchOutcome.SkillComparison comparison : outcome.skills()) {
            skills.add(JdMatchSkill.from(match.getId(), userId, comparison));
        }
        matchSkillRepository.saveAll(skills);

        log.info("Match {} for posting {} against resume {}: {}% ({}), {} matched / {} missing",
                match.getId(), postingId, resumeId, outcome.overallScore(), outcome.band(),
                outcome.matched().size(), outcome.missing().size());

        return MatchResponse.of(match, skills, outcome.suggestions(),
                posting.getTitle(), posting.getCompany());
    }

    /**
     * The most recent match for a posting.
     *
     * <p>Suggestions are recomputed from the stored posting and the resume's
     * current parse rather than stored, so re-reading an old match after the
     * resume changed shows advice about the resume as it is now.
     */
    @Transactional(readOnly = true)
    public MatchResponse getLatest(UUID postingId, UUID userId) {
        JobDescription posting = require(postingId, userId);
        JdMatch match = matchRepository.findLatestForPosting(postingId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Match"));
        return hydrate(match, posting, userId);
    }

    @Transactional(readOnly = true)
    public MatchResponse getById(UUID matchId, UUID userId) {
        JdMatch match = matchRepository.findByIdAndUserId(matchId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Match"));
        return hydrate(match, require(match.getJobDescriptionId(), userId), userId);
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> history(UUID postingId, UUID userId) {
        JobDescription posting = require(postingId, userId);
        return matchRepository
                .findByJobDescriptionIdAndUserIdOrderByCreatedAtDesc(postingId, userId).stream()
                .map(match -> MatchResponse.of(
                        match,
                        matchSkillRepository.findByMatchIdOrderByPriorityDesc(match.getId()),
                        List.of(),
                        posting.getTitle(),
                        posting.getCompany()))
                .toList();
    }

    @Transactional(readOnly = true)
    public long countMatches(UUID userId) {
        return matchRepository.countByUserId(userId);
    }

    // ------------------------------------------------------------------

    private MatchResponse hydrate(JdMatch match, JobDescription posting, UUID userId) {
        List<MatchOutcome.Suggestion> suggestions = List.of();
        try {
            ResumeSnapshotProvider.Snapshot snapshot =
                    snapshotProvider.forResume(match.getResumeId(), userId);
            suggestions = MatchEngine
                    .match(snapshot.resume(), JobPosting.parse(posting.getRawText()))
                    .suggestions();
        } catch (RuntimeException e) {
            // The resume may since have been deleted or re-uploaded. The stored
            // verdicts are still valid history; only the advice is unavailable.
            log.debug("Could not recompute suggestions for match {}: {}",
                    match.getId(), e.toString());
        }
        return MatchResponse.of(match,
                matchSkillRepository.findByMatchIdOrderByPriorityDesc(match.getId()),
                suggestions, posting.getTitle(), posting.getCompany());
    }

    private JobDescription require(UUID postingId, UUID userId) {
        return postingRepository.findActive(postingId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Job description"));
    }

    private JobDescriptionResponse withLatestScore(JobDescription posting, UUID userId,
                                                   boolean includeText) {
        Optional<JdMatch> latest =
                matchRepository.findLatestForPosting(posting.getId(), userId);
        Integer score = latest.map(match -> (int) match.getOverallScore()).orElse(null);
        String band = latest.map(match -> match.getBand().name()).orElse(null);
        Instant at = latest.map(JdMatch::getCreatedAt).orElse(null);
        return includeText
                ? JobDescriptionResponse.detail(posting, score, band, at)
                : JobDescriptionResponse.summary(posting, score, band, at);
    }
}
