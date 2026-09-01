-- ---------------------------------------------------------------------------
-- V9 — Mock interview (Phase 10)
--
-- A session is generated once and then fixed. Questions are not re-derived on
-- each request: a candidate who reloads the page mid-interview must see the same
-- question they were part-way through answering, and a report must be about the
-- interview that actually happened.
--
-- ADR-0033: blueprint first, then slot generation. The session records which
-- blueprint produced it so a change to question generation cannot retroactively
-- alter what an old report claims was asked.
-- ---------------------------------------------------------------------------

CREATE TABLE interview_sessions (
    id                   UUID         PRIMARY KEY,
    user_id              UUID         NOT NULL,
    resume_id            UUID,
    job_description_id   UUID,

    focus                VARCHAR(30)  NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',

    question_count       SMALLINT     NOT NULL,
    answered_count       SMALLINT     NOT NULL DEFAULT 0,

    overall_score        SMALLINT,
    band                 VARCHAR(20),

    blueprint_version    VARCHAR(20)  NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at         TIMESTAMPTZ,

    CONSTRAINT fk_interview_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    -- SET NULL rather than CASCADE: deleting a resume must not delete the
    -- record of an interview the candidate already sat and learned from.
    CONSTRAINT fk_interview_sessions_resume
        FOREIGN KEY (resume_id) REFERENCES resumes (id) ON DELETE SET NULL,
    CONSTRAINT fk_interview_sessions_jd
        FOREIGN KEY (job_description_id) REFERENCES job_descriptions (id) ON DELETE SET NULL,

    CONSTRAINT ck_interview_sessions_focus
        CHECK (focus IN ('GENERAL', 'RESUME_DEEP_DIVE', 'JOB_SPECIFIC', 'BEHAVIOURAL')),
    CONSTRAINT ck_interview_sessions_status
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED')),
    CONSTRAINT ck_interview_sessions_counts
        CHECK (question_count > 0 AND answered_count >= 0 AND answered_count <= question_count),
    CONSTRAINT ck_interview_sessions_score
        CHECK (overall_score IS NULL OR overall_score BETWEEN 0 AND 100),
    CONSTRAINT ck_interview_sessions_band
        CHECK (band IS NULL OR band IN ('NEEDS_WORK', 'DEVELOPING', 'SOLID', 'STRONG'))
);

CREATE INDEX ix_interview_sessions_user_created
    ON interview_sessions (user_id, created_at DESC);
CREATE INDEX ix_interview_sessions_open
    ON interview_sessions (user_id) WHERE status = 'IN_PROGRESS';

COMMENT ON COLUMN interview_sessions.blueprint_version IS
    'Which generator produced these questions. An old report must not be reread under new rules.';

-- ---------------------------------------------------------------------------
-- Questions.
--
-- rationale is not decoration. A candidate told why they are being asked
-- something learns from the question itself, and a question generated from their
-- own resume that cannot explain itself is indistinguishable from a generic one.
-- ---------------------------------------------------------------------------

CREATE TABLE interview_questions (
    id               UUID         PRIMARY KEY,
    session_id       UUID         NOT NULL,
    user_id          UUID         NOT NULL,

    position         SMALLINT     NOT NULL,
    kind             VARCHAR(30)  NOT NULL,
    prompt           TEXT         NOT NULL,
    focus_skill      VARCHAR(100),
    rationale        TEXT,
    expected_points  TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_interview_questions_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_interview_questions_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_interview_questions_kind
        CHECK (kind IN ('TECHNICAL', 'BEHAVIOURAL', 'GAP_PROBE', 'PROJECT_DEEP_DIVE',
                        'EXPERIENCE_PROBE', 'MOTIVATION')),
    CONSTRAINT uq_interview_questions_position UNIQUE (session_id, position)
);

CREATE INDEX ix_interview_questions_session ON interview_questions (session_id, position);
CREATE INDEX ix_interview_questions_user    ON interview_questions (user_id);

COMMENT ON COLUMN interview_questions.expected_points IS
    'Newline-separated cues a good answer covers. Shown only after answering.';

-- ---------------------------------------------------------------------------
-- Answers.
--
-- One per question, enforced by a unique constraint rather than by application
-- code: re-answering is an edit of the existing row, and two rows for one
-- question would make the session score depend on which one a query happened to
-- return first.
--
-- ADR-0003: feedback comes from answer content only. There is no audio column,
-- no video column, and no affect score, and there never will be.
-- ---------------------------------------------------------------------------

CREATE TABLE interview_answers (
    id                  UUID         PRIMARY KEY,
    question_id         UUID         NOT NULL,
    session_id          UUID         NOT NULL,
    user_id             UUID         NOT NULL,

    answer_text         TEXT         NOT NULL,
    word_count          INTEGER      NOT NULL,

    score               SMALLINT     NOT NULL,
    structure_score     SMALLINT     NOT NULL,
    specificity_score   SMALLINT     NOT NULL,
    relevance_score     SMALLINT     NOT NULL,
    clarity_score       SMALLINT     NOT NULL,

    strengths           TEXT,
    improvements        TEXT,
    rubric_version      VARCHAR(20)  NOT NULL,

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_interview_answers_question
        FOREIGN KEY (question_id) REFERENCES interview_questions (id) ON DELETE CASCADE,
    CONSTRAINT fk_interview_answers_session
        FOREIGN KEY (session_id) REFERENCES interview_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_interview_answers_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    CONSTRAINT uq_interview_answers_question UNIQUE (question_id),
    CONSTRAINT ck_interview_answers_scores CHECK (
        score             BETWEEN 0 AND 100
    AND structure_score   BETWEEN 0 AND 100
    AND specificity_score BETWEEN 0 AND 100
    AND relevance_score   BETWEEN 0 AND 100
    AND clarity_score     BETWEEN 0 AND 100)
);

CREATE INDEX ix_interview_answers_session ON interview_answers (session_id);
CREATE INDEX ix_interview_answers_user    ON interview_answers (user_id);

COMMENT ON TABLE interview_answers IS
    'Answer content only. No audio, no video, no affect scoring — ADR-0003.';
