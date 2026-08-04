-- ===========================================================================
-- V4 — Async job engine
--
-- Resume parsing takes seconds and AI analysis takes tens of seconds. Neither
-- fits inside an HTTP request, so both become jobs: the API enqueues, returns
-- 202 with an id, and the client polls.
--
-- Why a table rather than Redis or a message broker: `SELECT ... FOR UPDATE
-- SKIP LOCKED` gives correct concurrent-claim semantics (two workers cannot
-- take the same row) on infrastructure that already exists. A broker would add
-- a second stateful service, a second failure mode, and a second bill for a
-- system whose expected throughput is a few jobs per minute. If throughput ever
-- justifies one, this table becomes its outbox rather than being discarded.
-- ===========================================================================

CREATE TABLE jobs (
    id             UUID         PRIMARY KEY,
    user_id        UUID         NOT NULL,

    job_type       VARCHAR(40)  NOT NULL,

    -- Polymorphic: a resume for PARSE_RESUME, an analysis for ANALYZE_ATS.
    -- Deliberately NOT a foreign key. The alternative is four nullable FK
    -- columns of which exactly one is ever populated, which trades a real
    -- constraint for a real mess. The application sets this from the job type
    -- and never from user input.
    reference_id   UUID         NOT NULL,

    status         VARCHAR(20)  NOT NULL DEFAULT 'QUEUED',

    attempts       SMALLINT     NOT NULL DEFAULT 0,
    max_attempts   SMALLINT     NOT NULL DEFAULT 3,

    error_message  TEXT,

    -- Id of the row produced on success, so the client can fetch the result
    -- without knowing how to construct a URL per job type.
    result_ref     UUID,

    -- Which instance holds the claim, and since when. locked_at is what lets a
    -- reaper reclaim work orphaned by a container restart - without it, every
    -- Railway redeploy would strand in-flight jobs in RUNNING forever and the
    -- client would poll a status that never changes.
    locked_by      VARCHAR(80),
    locked_at      TIMESTAMPTZ,

    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at     TIMESTAMPTZ,
    finished_at    TIMESTAMPTZ,

    CONSTRAINT fk_jobs_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    CONSTRAINT ck_jobs_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),

    CONSTRAINT ck_jobs_type
        CHECK (job_type IN ('PARSE_RESUME', 'ANALYZE_ATS', 'MATCH_JD', 'EVALUATE_INTERVIEW')),

    CONSTRAINT ck_jobs_attempts
        CHECK (attempts >= 0 AND attempts <= max_attempts)
);

-- The claim query. Partial on the two live statuses because terminal jobs are
-- the overwhelming majority after a few days and have no business being scanned
-- on every poll.
CREATE INDEX ix_jobs_claimable
    ON jobs (status, created_at)
    WHERE status IN ('QUEUED', 'RUNNING');

-- "What is the status of my upload?" - the polling path.
CREATE INDEX ix_jobs_user_created ON jobs (user_id, created_at DESC);

-- Lets a caller find the job for a given resume without knowing its id.
CREATE INDEX ix_jobs_reference ON jobs (reference_id, job_type);

COMMENT ON COLUMN jobs.reference_id IS
    'Polymorphic target, interpreted per job_type. Intentionally not a foreign key.';
COMMENT ON COLUMN jobs.locked_at IS
    'Claim timestamp. A stale value means the worker died; the reaper requeues it.';
