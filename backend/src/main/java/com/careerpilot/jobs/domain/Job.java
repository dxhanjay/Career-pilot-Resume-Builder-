package com.careerpilot.jobs.domain;

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
 * A unit of asynchronous work.
 *
 * <p>State transitions live here rather than in the service, so that "can this
 * job be retried?" has one answer rather than one per caller.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 40, updatable = false)
    private JobType jobType;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status = JobStatus.QUEUED;

    @Column(name = "attempts", nullable = false)
    private short attempts;

    @Column(name = "max_attempts", nullable = false)
    private short maxAttempts;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "result_ref")
    private UUID resultRef;

    @Column(name = "locked_by", length = 80)
    private String lockedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    /** Required by JPA. */
    protected Job() {
    }

    /**
     * Enqueues a new job.
     *
     * @param userId      owner, so status can be scoped to the caller
     * @param jobType     what to run
     * @param referenceId the target, interpreted per {@code jobType}
     * @param maxAttempts retries before giving up
     */
    public Job(UUID userId, JobType jobType, UUID referenceId, short maxAttempts) {
        this.userId = userId;
        this.jobType = jobType;
        this.referenceId = referenceId;
        this.maxAttempts = maxAttempts;
        this.status = JobStatus.QUEUED;
        this.attempts = 0;
    }

    /**
     * Marks the job claimed by a worker.
     *
     * @param workerId identifier of the claiming instance
     */
    public void markRunning(String workerId) {
        this.status = JobStatus.RUNNING;
        this.lockedBy = workerId;
        this.lockedAt = Instant.now();
        this.attempts++;
        if (this.startedAt == null) {
            this.startedAt = Instant.now();
        }
    }

    /**
     * Records success.
     *
     * @param resultRef id of the row produced, or {@code null}
     */
    public void markSucceeded(UUID resultRef) {
        this.status = JobStatus.SUCCEEDED;
        this.resultRef = resultRef;
        this.finishedAt = Instant.now();
        releaseLock();
    }

    /**
     * Records a failure, retrying if attempts remain and the cause is transient.
     *
     * <p>The distinction matters for cost and for latency. A 429 from an AI
     * provider is worth retrying; a corrupt PDF is not, and retrying it three
     * times is three times the work for the same outcome.
     *
     * @param message   client-safe failure description
     * @param transient whether a retry could plausibly succeed
     */
    public void markFailed(String message, boolean isTransient) {
        this.errorMessage = message;
        releaseLock();

        if (isTransient && attempts < maxAttempts) {
            this.status = JobStatus.QUEUED;
        } else {
            this.status = JobStatus.FAILED;
            this.finishedAt = Instant.now();
        }
    }

    /**
     * Returns an orphaned job to the queue.
     *
     * <p>Called by the reaper when {@code lockedAt} is stale, which means the
     * worker holding it died. The attempt counter is deliberately <em>not</em>
     * incremented — the job never got a fair run, and charging it an attempt
     * would eventually fail a perfectly good job because containers restarted.
     */
    public void requeueAfterStaleLock() {
        this.status = JobStatus.QUEUED;
        if (this.attempts > 0) {
            this.attempts--;
        }
        releaseLock();
    }

    /**
     * Cancels a job that has not started.
     *
     * @throws IllegalStateException if it is already running or terminal
     */
    public void cancel() {
        if (status != JobStatus.QUEUED) {
            throw new IllegalStateException("Only a queued job can be cancelled; this one is " + status);
        }
        this.status = JobStatus.CANCELLED;
        this.finishedAt = Instant.now();
    }

    private void releaseLock() {
        this.lockedBy = null;
        this.lockedAt = null;
    }

    // --- accessors ---------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public JobType getJobType() {
        return jobType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public JobStatus getStatus() {
        return status;
    }

    public short getAttempts() {
        return attempts;
    }

    public short getMaxAttempts() {
        return maxAttempts;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public UUID getResultRef() {
        return resultRef;
    }

    public String getLockedBy() {
        return lockedBy;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Job job)) {
            return false;
        }
        return id != null && id.equals(job.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Job.class);
    }

    @Override
    public String toString() {
        return "Job{id=" + id + ", type=" + jobType + ", status=" + status + ", attempts=" + attempts + "}";
    }
}
