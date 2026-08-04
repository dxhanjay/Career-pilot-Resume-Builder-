package com.careerpilot.jobs.domain;

/**
 * Lifecycle of an async job.
 *
 * <pre>
 *   QUEUED ──claim──▶ RUNNING ──▶ SUCCEEDED
 *      ▲                 │
 *      │                 ├──▶ FAILED        (attempts exhausted, or permanent)
 *      └──── retry ──────┤
 *      └──── reaper ─────┘                  (worker died; lock went stale)
 *
 *   QUEUED ──▶ CANCELLED
 * </pre>
 *
 * <p>The {@code RUNNING → QUEUED} reaper edge is the one that matters on
 * Railway. Containers restart on every deploy and at the platform's discretion;
 * without reclaim, a job claimed by a container that then died would sit in
 * {@code RUNNING} forever while the client polled a status that never changed.
 *
 * @author CareerPilot AI
 * @since 0.1.0
 */
public enum JobStatus {

    /** Waiting to be claimed. */
    QUEUED,

    /** Claimed by a worker and executing. */
    RUNNING,

    /** Completed; {@code resultRef} names what it produced. */
    SUCCEEDED,

    /** Gave up — either a permanent failure or all attempts exhausted. */
    FAILED,

    /** Cancelled before it started. */
    CANCELLED;

    /**
     * @return whether this job has finished and will not change again
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
