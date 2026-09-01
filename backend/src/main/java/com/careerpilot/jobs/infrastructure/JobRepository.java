package com.careerpilot.jobs.infrastructure;

import com.careerpilot.jobs.domain.Job;
import com.careerpilot.jobs.domain.JobStatus;
import com.careerpilot.jobs.domain.JobType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence access for {@link Job}.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public interface JobRepository extends JpaRepository<Job, UUID> {

    /**
     * Atomically claims up to {@code limit} queued jobs.
     *
     * <p><strong>{@code FOR UPDATE SKIP LOCKED} is what makes this correct with
     * more than one worker.</strong> {@code FOR UPDATE} alone would make the
     * second worker <em>wait</em> for the first's transaction, serialising the
     * whole engine. {@code SKIP LOCKED} makes it step over already-claimed rows
     * and take the next free ones instead, so workers never collide and never
     * block each other.
     *
     * <p>Without it, the obvious implementation — select, then update — has a
     * race in which two workers read the same row before either writes, and the
     * job runs twice. For an AI job that means paying for the same analysis
     * twice; for an email job it would mean sending two.
     *
     * <p>Native SQL because JPQL has no way to express row locking hints. The
     * caller must hold a transaction, or the lock is released before the rows
     * are updated and the guarantee evaporates.
     *
     * @param limit how many to claim at once
     * @return claimed jobs, already locked for this transaction
     */
    @Query(value = """
            SELECT * FROM jobs
             WHERE status = 'QUEUED'
             ORDER BY created_at
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Job> claimQueued(@Param("limit") int limit);

    /**
     * Returns jobs whose worker appears to have died.
     *
     * <p>A {@code RUNNING} job whose {@code locked_at} is older than any
     * plausible execution time means the container holding it is gone — a
     * redeploy, a crash, or the platform reclaiming it. These are requeued.
     *
     * @param staleBefore lock timestamps older than this are considered dead
     * @return orphaned jobs
     */
    @Query("SELECT j FROM Job j WHERE j.status = com.careerpilot.jobs.domain.JobStatus.RUNNING "
            + "AND j.lockedAt < :staleBefore")
    List<Job> findStale(@Param("staleBefore") Instant staleBefore);

    /**
     * Finds a job owned by a specific user.
     *
     * <p>Scoped by owner for the same reason every resume query is: a job id is
     * a UUID, and returning another user's job would disclose what they are
     * doing and when.
     *
     * @param id     job identifier
     * @param userId the requesting user
     * @return the job, if it is theirs
     */
    @Query("SELECT j FROM Job j WHERE j.id = :id AND j.userId = :userId")
    Optional<Job> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * A user's jobs, newest first.
     *
     * @param userId   the owner
     * @param pageable page request
     * @return one page of jobs
     */
    @Query("SELECT j FROM Job j WHERE j.userId = :userId ORDER BY j.createdAt DESC")
    Page<Job> findAllByUserId(@Param("userId") UUID userId, Pageable pageable);

    /**
     * The most recent job of a given type for a target.
     *
     * <p>Lets a client ask "is this resume being parsed?" knowing only the
     * resume id, which is what the upload response gives it.
     *
     * @param referenceId the target
     * @param jobType     the kind of work
     * @return the newest matching job
     */
    @Query("SELECT j FROM Job j WHERE j.referenceId = :referenceId AND j.jobType = :jobType "
            + "ORDER BY j.createdAt DESC LIMIT 1")
    Optional<Job> findLatestByReference(@Param("referenceId") UUID referenceId,
                                        @Param("jobType") JobType jobType);

    /**
     * Whether a target already has work queued or running.
     *
     * <p>Prevents a user double-clicking "Analyse" from enqueuing the same
     * expensive AI job twice.
     *
     * @param referenceId the target
     * @param jobType     the kind of work
     * @return {@code true} if such a job is already pending
     */
    @Query("""
            SELECT COUNT(j) > 0 FROM Job j
             WHERE j.referenceId = :referenceId
               AND j.jobType = :jobType
               AND j.status IN (com.careerpilot.jobs.domain.JobStatus.QUEUED,
                                com.careerpilot.jobs.domain.JobStatus.RUNNING)
            """)
    boolean existsActiveForReference(@Param("referenceId") UUID referenceId,
                                     @Param("jobType") JobType jobType);

    /**
     * Deletes terminal jobs older than a cut-off.
     *
     * <p>An unbounded jobs table slows the claim query, which runs every few
     * seconds forever.
     *
     * @param cutoff finished before this instant
     * @return rows removed
     */
    @Modifying
    @Query("DELETE FROM Job j WHERE j.finishedAt IS NOT NULL AND j.finishedAt < :cutoff")
    int deleteTerminalBefore(@Param("cutoff") Instant cutoff);

    /**
     * How many jobs are in one state, across every user.
     *
     * <p>Operational, not per-user: a queue depth that only an administrator can
     * see is the point of the metric.
     */
    long countByStatus(JobStatus status);
}
