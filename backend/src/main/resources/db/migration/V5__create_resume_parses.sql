-- ===========================================================================
-- V5 — Resume text extraction
--
-- One row per parse ATTEMPT, not per resume. When PDFBox fails and Tika
-- succeeds, both attempts are recorded.
--
-- That is deliberate. Failed extractions are the raw material for improving the
-- parser, and they are the only evidence of which real-world layouts defeat it.
-- Keeping only the successful attempt discards exactly the data needed to make
-- the next version better.
--
-- Structured entities (skills, education, experience, ...) hang off this table
-- in V6, so a re-parse produces a new entity set without destroying the old one.
-- ===========================================================================

CREATE TABLE resume_parses (
    id              UUID         PRIMARY KEY,
    resume_id       UUID         NOT NULL,
    user_id         UUID         NOT NULL,

    parser_name     VARCHAR(50)  NOT NULL,
    parser_version  VARCHAR(20)  NOT NULL,

    status          VARCHAR(20)  NOT NULL,

    raw_text        TEXT,
    page_count      SMALLINT,
    word_count      INTEGER,
    char_count      INTEGER,

    -- Structural problems that are not extraction failures but predict them:
    -- a two-column layout, an embedded image, no text layer at all. This is how
    -- risk R1 becomes visible to the user rather than silently producing
    -- garbage, and it is what feeds the "here's what the machine saw" screen.
    warnings        JSONB        NOT NULL DEFAULT '[]'::jsonb,

    duration_ms     INTEGER,
    error_message   TEXT,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_resume_parses_resume
        FOREIGN KEY (resume_id) REFERENCES resumes (id) ON DELETE CASCADE,

    CONSTRAINT fk_resume_parses_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    CONSTRAINT ck_resume_parses_status
        CHECK (status IN ('SUCCEEDED', 'FAILED')),

    CONSTRAINT ck_resume_parses_parser
        CHECK (parser_name IN ('PDFBOX', 'TIKA'))
);

-- "Show me the latest successful parse for this resume" - the read path.
CREATE INDEX ix_resume_parses_resume_created
    ON resume_parses (resume_id, created_at DESC);

CREATE INDEX ix_resume_parses_user
    ON resume_parses (user_id);

-- Supports "which layouts are defeating the parser?", the query that drives
-- parser improvement.
CREATE INDEX ix_resume_parses_failures
    ON resume_parses (created_at DESC)
    WHERE status = 'FAILED';

COMMENT ON TABLE resume_parses IS
    'One row per parse attempt. Failures are retained deliberately as parser-improvement data.';
COMMENT ON COLUMN resume_parses.warnings IS
    'Structural problems detected during extraction, as a JSON array of {code, message}.';
