package com.careerpilot.jobs.application;

import com.careerpilot.jobs.domain.Job;
import com.careerpilot.jobs.domain.JobType;

import java.util.UUID;

/**
 * Executes one kind of job.
 *
 * <p>Implementations are discovered by Spring and indexed by {@link #handles()},
 * so adding a job type means adding a handler and nothing else — no registry to
 * update, no switch statement to extend, and no possibility of the two drifting.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface JobHandler {

    /**
     * @return the job type this handler executes
     */
    JobType handles();

    /**
     * Runs the job.
     *
     * <p>Called outside any transaction the poller owns. A handler that needs
     * one opens its own, so a slow external call — an AI request, a file
     * download — does not hold a database connection for its duration.
     *
     * @param job the claimed job
     * @return id of the row produced, or {@code null} if the work produced none
     * @throws JobExecutionException to control whether the failure is retried
     */
    UUID execute(Job job);

    /**
     * Signals a failure, and whether retrying could plausibly help.
     *
     * <p>The distinction is the point of this exception existing. A 429 from a
     * provider or a dropped connection is worth another attempt; a corrupt PDF
     * or a missing row is not, and retrying it three times costs three times as
     * much for the same outcome — while delaying the honest failure the user is
     * waiting for.
     */
    class JobExecutionException extends RuntimeException {

        private final boolean transientFailure;

        /**
         * @param message          client-safe description
         * @param transientFailure whether a retry could succeed
         */
        public JobExecutionException(String message, boolean transientFailure) {
            super(message);
            this.transientFailure = transientFailure;
        }

        /**
         * @param message          client-safe description
         * @param cause            underlying failure, for logs only
         * @param transientFailure whether a retry could succeed
         */
        public JobExecutionException(String message, Throwable cause, boolean transientFailure) {
            super(message, cause);
            this.transientFailure = transientFailure;
        }

        /**
         * @return whether the job should be requeued
         */
        public boolean isTransient() {
            return transientFailure;
        }
    }
}
