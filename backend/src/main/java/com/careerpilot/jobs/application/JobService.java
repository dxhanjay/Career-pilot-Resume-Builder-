package com.careerpilot.jobs.application;

import com.careerpilot.common.exception.BusinessRuleViolationException;
import com.careerpilot.common.exception.ResourceNotFoundException;
import com.careerpilot.jobs.application.dto.JobStatusResponse;
import com.careerpilot.jobs.domain.Job;
import com.careerpilot.jobs.domain.JobStatus;
import com.careerpilot.jobs.domain.JobType;
import com.careerpilot.jobs.infrastructure.JobRepository;
import com.careerpilot.common.dto.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Enqueues, claims, and completes jobs.
 *
 * <p>Holds the transactional boundaries that make the engine correct. The poller
 * decides <em>when</em> to look for work; this class decides what "claiming" and
 * "completing" mean, and does each in its own transaction.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private static final short DEFAULT_MAX_ATTEMPTS = 3;

    private final JobRepository jobRepository;

    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /**
     * Enqueues a job, refusing to duplicate work already pending.
     *
     * <p>The idempotency check is a cost control as much as a correctness one.
     * Without it, a user double-clicking "Analyse" enqueues two identical AI
     * jobs and we pay for both.
     *
     * @param userId      owner
     * @param jobType     what to run
     * @param referenceId target
     * @return the new job, or the pending one if work was already queued
     */
    @Transactional
    public Job enqueue(UUID userId, JobType jobType, UUID referenceId) {
        if (jobRepository.existsActiveForReference(referenceId, jobType)) {
            Job existing = jobRepository.findLatestByReference(referenceId, jobType)
                    .orElseThrow(() -> new IllegalStateException("Active job vanished mid-check"));
            log.debug("Reusing pending {} job {} for {}", jobType, existing.getId(), referenceId);
            return existing;
        }

        Job job = jobRepository.save(new Job(userId, jobType, referenceId, DEFAULT_MAX_ATTEMPTS));
        log.info("Enqueued {} job {} for {}", jobType, job.getId(), referenceId);
        return job;
    }

    /**
     * Claims up to {@code batchSize} queued jobs for this instance.
     *
     * <p>{@code REQUIRES_NEW} so the claim commits on its own, immediately. The
     * row lock taken by {@code SELECT ... FOR UPDATE SKIP LOCKED} lives only as
     * long as its transaction; holding it open for the duration of the work
     * would block the claim query for every other worker and defeat the point of
     * {@code SKIP LOCKED}.
     *
     * <p>So: claim and commit here, execute afterwards, outside this
     * transaction. The gap between commit and execution is exactly the window
     * the stale-lock reaper covers.
     *
     * @param workerId  identifies this instance in {@code locked_by}
     * @param batchSize how many to take
     * @return the claimed jobs
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Job> claimBatch(String workerId, int batchSize) {
        List<Job> claimed = jobRepository.claimQueued(batchSize);
        claimed.forEach(job -> job.markRunning(workerId));
        return claimed;
    }

    /**
     * Records a successful execution.
     *
     * @param jobId     the job
     * @param resultRef id of the row produced, or {@code null}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(UUID jobId, UUID resultRef) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.markSucceeded(resultRef);
            log.info("Job {} succeeded", jobId);
        });
    }

    /**
     * Records a failure, requeuing if the cause was transient and attempts remain.
     *
     * <p>{@code REQUIRES_NEW} matters here more than anywhere: the work's own
     * transaction has usually just rolled back, and recording the failure inside
     * a doomed transaction would roll the record back too — leaving the job
     * stuck in {@code RUNNING} with no explanation anywhere.
     *
     * @param jobId            the job
     * @param message          client-safe description
     * @param transientFailure whether a retry could succeed
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID jobId, String message, boolean transientFailure) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.markFailed(message, transientFailure);
            if (job.getStatus() == JobStatus.QUEUED) {
                log.warn("Job {} failed transiently, requeued (attempt {}/{}): {}",
                        jobId, job.getAttempts(), job.getMaxAttempts(), message);
            } else {
                log.error("Job {} failed permanently after {} attempt(s): {}",
                        jobId, job.getAttempts(), message);
            }
        });
    }

    /**
     * Returns orphaned jobs to the queue.
     *
     * <p>A {@code RUNNING} job whose lock is older than {@code staleAfter} means
     * the container holding it is gone. On Railway that happens on every deploy,
     * so this is ordinary operation rather than an exceptional path.
     *
     * @param staleAfter how long a lock may be held before it is presumed dead
     * @return how many jobs were requeued
     */
    @Transactional
    public int reclaimStale(Duration staleAfter) {
        List<Job> stale = jobRepository.findStale(Instant.now().minus(staleAfter));

        stale.forEach(Job::requeueAfterStaleLock);

        if (!stale.isEmpty()) {
            log.warn("Reclaimed {} job(s) orphaned by a stopped worker", stale.size());
        }
        return stale.size();
    }

    // =======================================================================
    // Read API
    // =======================================================================

    /**
     * Returns a job's status.
     *
     * @param jobId  the job
     * @param userId the requesting user
     * @return the status
     * @throws ResourceNotFoundException if it does not exist or is not theirs
     */
    @Transactional(readOnly = true)
    public JobStatusResponse getStatus(UUID jobId, UUID userId) {
        return jobRepository.findByIdAndUserId(jobId, userId)
                .map(JobStatusResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Job"));
    }

    /**
     * Lists a user's jobs, newest first.
     *
     * @param userId   the owner
     * @param pageable page request
     * @return one page of statuses
     */
    @Transactional(readOnly = true)
    public PageResponse<JobStatusResponse> list(UUID userId, Pageable pageable) {
        return PageResponse.from(jobRepository.findAllByUserId(userId, pageable), JobStatusResponse::from);
    }

    /**
     * Finds the newest job of a type for a target, scoped to its owner.
     *
     * @param referenceId the target
     * @param jobType     the kind of work
     * @param userId      the requesting user
     * @return the status, if such a job exists and belongs to them
     */
    @Transactional(readOnly = true)
    public Optional<JobStatusResponse> findLatestFor(UUID referenceId, JobType jobType, UUID userId) {
        return jobRepository.findLatestByReference(referenceId, jobType)
                .filter(job -> job.getUserId().equals(userId))
                .map(JobStatusResponse::from);
    }

    /**
     * Cancels a queued job.
     *
     * @param jobId  the job
     * @param userId the owner
     * @throws BusinessRuleViolationException if it has already started
     */
    @Transactional
    public void cancel(UUID jobId, UUID userId) {
        Job job = jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Job"));
        try {
            job.cancel();
        } catch (IllegalStateException e) {
            // A domain rule, not a server error: the job is running or finished.
            throw new BusinessRuleViolationException(e.getMessage());
        }
    }
}
