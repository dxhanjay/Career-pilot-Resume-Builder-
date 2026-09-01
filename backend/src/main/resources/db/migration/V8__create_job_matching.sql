-- ---------------------------------------------------------------------------
-- V8 — Job descriptions and matching (Phase 9)
--
-- A job description is user-owned content, not shared reference data. Two
-- students pasting the same posting get two rows: the text they pasted is the
-- text their match was computed against, and deduplicating it would mean one
-- user's edit silently changing another user's result.
-- ---------------------------------------------------------------------------

CREATE TABLE job_descriptions (
    id           UUID          PRIMARY KEY,
    user_id      UUID          NOT NULL,

    title        VARCHAR(200)  NOT NULL,
    company      VARCHAR(200),
    location     VARCHAR(150),
    source_url   VARCHAR(1000),
    raw_text     TEXT          NOT NULL,

    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    deleted_at   TIMESTAMPTZ,

    CONSTRAINT fk_job_descriptions_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_job_descriptions_text_length
        CHECK (length(raw_text) BETWEEN 40 AND 40000)
);

CREATE INDEX ix_job_descriptions_user_created
    ON job_descriptions (user_id, created_at DESC)
    WHERE deleted_at IS NULL;

COMMENT ON COLUMN job_descriptions.raw_text IS
    'The posting as pasted. Never rewritten — it is the evidence a match is explained against.';

-- ---------------------------------------------------------------------------
-- Match runs.
--
-- Like ats_analyses, append-only. Re-matching after a resume edit is how a user
-- sees whether the edit worked, and that requires the previous number to survive.
-- ---------------------------------------------------------------------------

CREATE TABLE jd_matches (
    id                   UUID         PRIMARY KEY,
    job_description_id   UUID         NOT NULL,
    resume_id            UUID         NOT NULL,
    parse_id             UUID         NOT NULL,
    user_id              UUID         NOT NULL,

    overall_score        SMALLINT     NOT NULL,
    band                 VARCHAR(20)  NOT NULL,

    required_skill_score SMALLINT     NOT NULL,
    optional_skill_score SMALLINT     NOT NULL,
    title_score          SMALLINT     NOT NULL,
    experience_score     SMALLINT     NOT NULL,

    matched_count        SMALLINT     NOT NULL DEFAULT 0,
    missing_count        SMALLINT     NOT NULL DEFAULT 0,

    rubric_version       VARCHAR(20)  NOT NULL,
    duration_ms          INTEGER,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_jd_matches_jd
        FOREIGN KEY (job_description_id) REFERENCES job_descriptions (id) ON DELETE CASCADE,
    CONSTRAINT fk_jd_matches_resume
        FOREIGN KEY (resume_id) REFERENCES resumes (id) ON DELETE CASCADE,
    CONSTRAINT fk_jd_matches_parse
        FOREIGN KEY (parse_id) REFERENCES resume_parses (id) ON DELETE CASCADE,
    CONSTRAINT fk_jd_matches_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    CONSTRAINT ck_jd_matches_overall CHECK (overall_score BETWEEN 0 AND 100),
    CONSTRAINT ck_jd_matches_band
        CHECK (band IN ('WEAK', 'PARTIAL', 'PROMISING', 'STRONG')),
    CONSTRAINT ck_jd_matches_subscores CHECK (
        required_skill_score BETWEEN 0 AND 100
    AND optional_skill_score BETWEEN 0 AND 100
    AND title_score          BETWEEN 0 AND 100
    AND experience_score     BETWEEN 0 AND 100)
);

CREATE INDEX ix_jd_matches_jd_created   ON jd_matches (job_description_id, created_at DESC);
CREATE INDEX ix_jd_matches_user_created ON jd_matches (user_id, created_at DESC);
CREATE INDEX ix_jd_matches_resume       ON jd_matches (resume_id);

-- ---------------------------------------------------------------------------
-- Per-skill verdicts.
--
-- Both sides of the comparison are quoted. A gap that says only "missing:
-- Kubernetes" is unactionable; one that also shows the line of the posting that
-- asked for it tells the candidate how central it actually is.
-- ---------------------------------------------------------------------------

CREATE TABLE jd_match_skills (
    id                 UUID         PRIMARY KEY,
    match_id           UUID         NOT NULL,
    user_id            UUID         NOT NULL,

    normalized_name    VARCHAR(100) NOT NULL,
    display_name       VARCHAR(100) NOT NULL,
    category           VARCHAR(30)  NOT NULL,
    status             VARCHAR(20)  NOT NULL,
    required           BOOLEAN      NOT NULL DEFAULT FALSE,
    priority           SMALLINT     NOT NULL DEFAULT 0,

    resume_evidence    TEXT,
    resume_line        INTEGER,
    jd_evidence        TEXT,
    jd_line            INTEGER,

    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_jd_match_skills_match
        FOREIGN KEY (match_id) REFERENCES jd_matches (id) ON DELETE CASCADE,
    CONSTRAINT fk_jd_match_skills_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_jd_match_skills_status
        CHECK (status IN ('MATCHED', 'MISSING', 'EXTRA')),
    CONSTRAINT uq_jd_match_skills UNIQUE (match_id, normalized_name)
);

CREATE INDEX ix_jd_match_skills_match ON jd_match_skills (match_id, priority DESC);
CREATE INDEX ix_jd_match_skills_user  ON jd_match_skills (user_id);

COMMENT ON COLUMN jd_match_skills.status IS
    'MATCHED: on both sides. MISSING: asked for, not found. EXTRA: on the resume, not asked for.';
COMMENT ON COLUMN jd_match_skills.priority IS
    'Ranking weight for the gap list. Required-and-repeated beats optional-and-mentioned-once.';
