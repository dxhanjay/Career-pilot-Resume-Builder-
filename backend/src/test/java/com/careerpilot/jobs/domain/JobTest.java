package com.careerpilot.jobs.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link Job} state machine.
 *
 * <p>The retry and reclaim rules are worth testing precisely because their
 * failure modes are quiet. A job that silently stops retrying looks like a
 * feature that occasionally does not work; a job that retries something
 * permanent burns money three times for the same outcome. Neither produces an
 * error anyone will see.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@DisplayName("Job")
class JobTest {

    private Job newJob() {
        return new Job(UUID.randomUUID(), JobType.PARSE_RESUME, UUID.randomUUID(), (short) 3);
    }

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("starts queued with no attempts")
        void startsQueued() {
            Job job = newJob();

            assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
            assertThat(job.getAttempts()).isZero();
            assertThat(job.getStartedAt()).isNull();
        }

        @Test
        @DisplayName("claiming records the worker and counts an attempt")
        void claimRecordsWorker() {
            Job job = newJob();

            job.markRunning("worker-1");

            assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
            assertThat(job.getLockedBy()).isEqualTo("worker-1");
            assertThat(job.getLockedAt()).isNotNull();
            assertThat(job.getAttempts()).isEqualTo((short) 1);
            assertThat(job.getStartedAt()).isNotNull();
        }

        @Test
        @DisplayName("success releases the lock and records the result")
        void successReleasesLock() {
            Job job = newJob();
            UUID resultRef = UUID.randomUUID();

            job.markRunning("worker-1");
            job.markSucceeded(resultRef);

            assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
            assertThat(job.getResultRef()).isEqualTo(resultRef);
            assertThat(job.getFinishedAt()).isNotNull();
            // A retained lock on a finished job would make the reaper think a
            // worker was still holding it.
            assertThat(job.getLockedBy()).isNull();
            assertThat(job.getLockedAt()).isNull();
        }

        @Test
        @DisplayName("startedAt records the FIRST attempt, not the latest")
        void startedAtIsFirstAttempt() {
            Job job = newJob();

            job.markRunning("worker-1");
            var firstStart = job.getStartedAt();
            job.markFailed("transient", true);
            job.markRunning("worker-2");

            // Otherwise "how long did this take?" measures only the last retry,
            // and a job that took four minutes across three attempts reports
            // forty seconds.
            assertThat(job.getStartedAt()).isEqualTo(firstStart);
        }
    }

    @Nested
    @DisplayName("retry policy")
    class Retries {

        @Test
        @DisplayName("a transient failure requeues while attempts remain")
        void transientFailureRequeues() {
            Job job = newJob();

            job.markRunning("worker-1");
            job.markFailed("provider unavailable", true);

            assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
            assertThat(job.getFinishedAt()).isNull();
            assertThat(job.getErrorMessage()).isEqualTo("provider unavailable");
        }

        @Test
        @DisplayName("a permanent failure does not requeue, even on the first attempt")
        void permanentFailureDoesNotRetry() {
            Job job = newJob();

            job.markRunning("worker-1");
            job.markFailed("corrupt file", false);

            // Retrying a corrupt PDF costs three times as much for the same
            // outcome, and delays telling the user something they can act on.
            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
            assertThat(job.getAttempts()).isEqualTo((short) 1);
            assertThat(job.getFinishedAt()).isNotNull();
        }

        @Test
        @DisplayName("a transient failure becomes permanent once attempts are exhausted")
        void exhaustedAttemptsFail() {
            Job job = newJob();

            for (int i = 0; i < 3; i++) {
                job.markRunning("worker-" + i);
                job.markFailed("still unavailable", true);
            }

            assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
            assertThat(job.getAttempts()).isEqualTo((short) 3);
        }
    }

    @Nested
    @DisplayName("stale-lock reclaim")
    class Reclaim {

        @Test
        @DisplayName("⭐ requeues without charging an attempt")
        void reclaimDoesNotChargeAnAttempt() {
            Job job = newJob();

            job.markRunning("worker-that-died");
            assertThat(job.getAttempts()).isEqualTo((short) 1);

            job.requeueAfterStaleLock();

            // The job never got a fair run - its container was killed by a
            // redeploy. Charging it an attempt means a busy deployment week
            // eventually fails perfectly good jobs for reasons that have nothing
            // to do with them.
            assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
            assertThat(job.getAttempts()).isZero();
            assertThat(job.getLockedBy()).isNull();
        }

        @Test
        @DisplayName("survives repeated restarts without exhausting attempts")
        void repeatedRestartsDoNotExhaust() {
            Job job = newJob();

            for (int i = 0; i < 10; i++) {
                job.markRunning("worker-" + i);
                job.requeueAfterStaleLock();
            }

            assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
            assertThat(job.getAttempts()).isZero();
        }
    }

    @Nested
    @DisplayName("cancellation")
    class Cancellation {

        @Test
        @DisplayName("a queued job can be cancelled")
        void queuedCanBeCancelled() {
            Job job = newJob();

            job.cancel();

            assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
            assertThat(job.getFinishedAt()).isNotNull();
        }

        @Test
        @DisplayName("a running job cannot be cancelled")
        void runningCannotBeCancelled() {
            Job job = newJob();
            job.markRunning("worker-1");

            // Cancelling mid-execution would leave the worker writing results
            // for a job the user believes is cancelled.
            assertThatThrownBy(job::cancel)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("RUNNING");
        }

        @Test
        @DisplayName("a finished job cannot be cancelled")
        void finishedCannotBeCancelled() {
            Job job = newJob();
            job.markRunning("worker-1");
            job.markSucceeded(null);

            assertThatThrownBy(job::cancel).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("terminal states")
    class Terminal {

        @Test
        @DisplayName("only SUCCEEDED, FAILED, and CANCELLED are terminal")
        void terminalStates() {
            assertThat(JobStatus.QUEUED.isTerminal()).isFalse();
            assertThat(JobStatus.RUNNING.isTerminal()).isFalse();
            assertThat(JobStatus.SUCCEEDED.isTerminal()).isTrue();
            assertThat(JobStatus.FAILED.isTerminal()).isTrue();
            assertThat(JobStatus.CANCELLED.isTerminal()).isTrue();
        }
    }
}
