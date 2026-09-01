-- ---------------------------------------------------------------------------
-- V7 — ATS analysis (Phase 7)
--
-- One row per analysis RUN, never updated in place. The product's closing
-- promise is "fix it and watch the score move", and a table that overwrites the
-- previous score cannot show movement. Retaining runs also makes a rubric change
-- auditable: rubric_version records which set of rules produced a number, so an
-- old score is never silently reinterpreted under new rules.
-- ---------------------------------------------------------------------------

CREATE TABLE ats_analyses (
    id                   UUID         PRIMARY KEY,
    resume_id            UUID         NOT NULL,
    parse_id             UUID         NOT NULL,
    user_id              UUID         NOT NULL,

    overall_score        SMALLINT     NOT NULL,
    band                 VARCHAR(20)  NOT NULL,

    -- Category subscores, each already normalised to 0-100. Stored rather than
    -- derived so a rubric reweighting cannot retroactively change what a user
    -- was shown last month.
    parseability_score   SMALLINT     NOT NULL,
    structure_score      SMALLINT     NOT NULL,
    content_score        SMALLINT     NOT NULL,
    skills_score         SMALLINT     NOT NULL,
    contact_score        SMALLINT     NOT NULL,

    rubric_version       VARCHAR(20)  NOT NULL,
    duration_ms          INTEGER,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_ats_analyses_resume
        FOREIGN KEY (resume_id) REFERENCES resumes (id) ON DELETE CASCADE,
    CONSTRAINT fk_ats_analyses_parse
        FOREIGN KEY (parse_id) REFERENCES resume_parses (id) ON DELETE CASCADE,
    CONSTRAINT fk_ats_analyses_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    CONSTRAINT ck_ats_analyses_overall  CHECK (overall_score BETWEEN 0 AND 100),
    CONSTRAINT ck_ats_analyses_band
        CHECK (band IN ('NEEDS_WORK', 'FAIR', 'GOOD', 'STRONG')),
    CONSTRAINT ck_ats_analyses_subscores CHECK (
        parseability_score BETWEEN 0 AND 100
    AND structure_score    BETWEEN 0 AND 100
    AND content_score      BETWEEN 0 AND 100
    AND skills_score       BETWEEN 0 AND 100
    AND contact_score      BETWEEN 0 AND 100)
);

CREATE INDEX ix_ats_analyses_resume_created
    ON ats_analyses (resume_id, created_at DESC);
CREATE INDEX ix_ats_analyses_user_created
    ON ats_analyses (user_id, created_at DESC);

COMMENT ON TABLE ats_analyses IS
    'One row per analysis run. Never updated — the history IS the improvement proof.';
COMMENT ON COLUMN ats_analyses.rubric_version IS
    'Which rule set produced this score. An old score must never be reread under new rules.';

-- ---------------------------------------------------------------------------
-- Findings.
--
-- FR-ATS-03: a score without a quote from the resume it came from is an
-- assertion, not analysis. evidence/evidence_line_* are therefore first-class
-- columns rather than an optional extra, and the rule engine is expected to
-- populate them for every finding that refers to specific text.
-- ---------------------------------------------------------------------------

CREATE TABLE ats_findings (
    id                   UUID         PRIMARY KEY,
    analysis_id          UUID         NOT NULL,
    user_id              UUID         NOT NULL,

    code                 VARCHAR(60)  NOT NULL,
    category             VARCHAR(30)  NOT NULL,
    severity             VARCHAR(20)  NOT NULL,

    title                VARCHAR(200) NOT NULL,
    detail               TEXT         NOT NULL,
    recommendation       TEXT,

    evidence             TEXT,
    evidence_line_start  INTEGER,
    evidence_line_end    INTEGER,

    points_lost          SMALLINT     NOT NULL DEFAULT 0,
    display_order        SMALLINT     NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_ats_findings_analysis
        FOREIGN KEY (analysis_id) REFERENCES ats_analyses (id) ON DELETE CASCADE,
    CONSTRAINT fk_ats_findings_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    CONSTRAINT ck_ats_findings_severity
        CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'PASS')),
    CONSTRAINT ck_ats_findings_category
        CHECK (category IN ('PARSEABILITY', 'STRUCTURE', 'CONTENT', 'SKILLS', 'CONTACT')),
    CONSTRAINT ck_ats_findings_points
        CHECK (points_lost >= 0)
);

CREATE INDEX ix_ats_findings_analysis ON ats_findings (analysis_id, display_order);
CREATE INDEX ix_ats_findings_user     ON ats_findings (user_id);

COMMENT ON COLUMN ats_findings.evidence IS
    'The text this finding is about, quoted verbatim from the parse. FR-ATS-03.';
COMMENT ON COLUMN ats_findings.severity IS
    'PASS is a finding too — showing what already works is what stops the report reading as a list of failures.';
