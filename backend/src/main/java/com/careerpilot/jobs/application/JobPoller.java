package com.careerpilot.jobs.application;

import com.careerpilot.config.properties.JobProperties;
import com.careerpilot.jobs.domain.Job;
import com.careerpilot.jobs.domain.JobType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Drives the job engine: claims work, runs it, records the outcome.
 *
 * <p>The execution model is deliberately simple. Claim in one committed
 * transaction, then execute outside it. Holding the claim transaction open for
 * the duration of the work would block every other worker's claim query, which
 * is precisely what {@code SKIP LOCKED} exists to avoid.
 *
 * <p>The cost of that choice is a window between "claimed" and "finished" in
 * which a container can die, leaving the job in {@code RUNNING} with nobody
 * working on it. {@link #reclaimStaleJobs()} closes it.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Component
public class JobPoller {

    private static final Logger log = LoggerFactory.getLogger(JobPoller.class);

    private final JobService jobService;
    private final JobProperties jobProperties;
    private final Map<JobType, JobHandler> handlers;
    private final String workerId;

    /**
     * @param jobService  claim and completion operations
     * @param jobProperties polling configuration
     * @param handlerList every {@link JobHandler} Spring found
     */
    public JobPoller(JobService jobService, JobProperties jobProperties, List<JobHandler> handlerList) {
        this.jobService = jobService;
        this.jobProperties = jobProperties;

        // Indexed by type at startup. Two handlers claiming the same type is a
        // wiring mistake that must fail loudly at boot rather than silently
        // picking one - toMap throws on a duplicate key, which is the behaviour
        // we want.
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(JobHandler::handles, Function.identity()));

        // Identifies this instance in locked_by, so a stuck job can be traced to
        // the container that was holding it.
        this.workerId = System.getenv().getOrDefault("RAILWAY_REPLICA_ID",
                "local-" + UUID.randomUUID().toString().substring(0, 8));

        log.info("Job poller ready as '{}' with handlers for {}", workerId, handlers.keySet());
    }

    /**
     * Claims and runs a batch of queued jobs.
     *
     * <p>{@code fixedDelay} rather than {@code fixedRate}: the delay is measured
     * from the end of the previous run, so a slow batch cannot cause runs to
     * overlap and pile up. With {@code fixedRate}, a batch taking longer than
     * the interval would start the next one while the first was still going.
     */
    @Scheduled(fixedDelayString = "${app.jobs.poll-interval-ms:5000}")
    public void pollAndExecute() {
        List<Job> claimed;
        try {
            claimed = jobService.claimBatch(workerId, jobProperties.batchSize());
        } catch (RuntimeException e) {
            // Usually the database being briefly unavailable. Log and wait for
            // the next tick; throwing here would not help and the scheduler
            // continues regardless.
            log.error("Could not claim jobs; will retry on the next poll", e);
            return;
        }

        for (Job job : claimed) {
            execute(job);
        }
    }

    /**
     * Returns jobs orphaned by a stopped worker to the queue.
     *
     * <p>Not an exceptional path. Railway restarts containers on every deploy
     * and at its own discretion, so without this every deployment would strand
     * whatever was in flight and the affected users would poll a status that
     * never changed.
     */
    @Scheduled(fixedDelayString = "${app.jobs.reaper-interval-ms:60000}")
    public void reclaimStaleJobs() {
        try {
            jobService.reclaimStale(jobProperties.staleLockTimeout());
        } catch (RuntimeException e) {
            log.error("Stale-job reclaim failed; will retry", e);
        }
    }

    private void execute(Job job) {
        // Put the job id into the logging context so every line the handler
        // writes - including a stack trace from deep inside a parser - can be
        // traced back to the job and therefore to the user who triggered it.
        MDC.put("jobId", job.getId().toString());
        MDC.put("jobType", job.getJobType().name());

        try {
            JobHandler handler = handlers.get(job.getJobType());

            if (handler == null) {
                // A job type with no handler. Permanent by definition: retrying
                // cannot conjure one, and the job would otherwise cycle until
                // its attempts ran out, obscuring the real problem.
                jobService.markFailed(job.getId(),
                        "No handler is registered for " + job.getJobType(), false);
                log.error("No handler registered for job type {}", job.getJobType());
                return;
            }

            UUID resultRef = handler.execute(job);
            jobService.markSucceeded(job.getId(), resultRef);

        } catch (JobHandler.JobExecutionException e) {
            // The handler classified this itself.
            jobService.markFailed(job.getId(), e.getMessage(), e.isTransient());

        } catch (RuntimeException e) {
            // An unclassified failure. Treated as transient so it gets its
            // remaining attempts: an unexpected exception is more often a blip -
            // a dropped connection, a momentary resource limit - than a
            // permanent defect, and the attempt ceiling bounds the cost of
            // being wrong.
            log.error("Unhandled exception executing job {}", job.getId(), e);
            jobService.markFailed(job.getId(), "An unexpected error occurred", true);

        } finally {
            MDC.remove("jobId");
            MDC.remove("jobType");
        }
    }
}
