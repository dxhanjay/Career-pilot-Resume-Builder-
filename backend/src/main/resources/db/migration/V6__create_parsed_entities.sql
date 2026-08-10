-- ===========================================================================
-- V6 — Parsed resume entities
--
-- Six tables hanging off resume_parses, one per entity kind. A re-parse writes
-- a new parse row and a new entity set, so the previous structured view of a
-- resume survives intact — which is what makes "your score moved because we
-- now find three more skills" answerable.
--
-- Normalised tables rather than one JSONB blob, because FR-JD-03 ("which
-- required skills are missing") is a set difference against parsed_skills, not
-- a render. In JSONB that is a full scan with containment operators, and skill
-- normalisation (ML -> machine learning) has nowhere to live.
--
-- Every table carries user_id even though it is reachable through parse_id.
-- These rows are personal data, and deleting a user's data must not depend on
-- getting a three-table join right.
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Line pointers are only meaningful against the normalisation that produced
-- them. Storing the version makes a later reader able to tell whether the
-- pointers it holds were written by today's rules, instead of silently
-- highlighting the wrong lines after a normalisation change.
-- ---------------------------------------------------------------------------
ALTER TABLE resume_parses
    ADD COLUMN normalisation_version SMALLINT;

COMMENT ON COLUMN resume_parses.normalisation_version IS
    'LineModel.NORMALISATION_VERSION in force when this parse was written. '
    'Line pointers on parsed_* rows are only valid against this version.';


-- ---------------------------------------------------------------------------
-- parsed_contacts — one row per parse
-- ---------------------------------------------------------------------------
CREATE TABLE parsed_contacts (
    id                 UUID         PRIMARY KEY,
    parse_id           UUID         NOT NULL,
    user_id            UUID         NOT NULL,

    full_name          VARCHAR(150),
    email              VARCHAR(320),
    phone              VARCHAR(40),
    location           VARCHAR(150),
    linkedin_url       VARCHAR(500),
    github_url         VARCHAR(500),
    portfolio_url      VARCHAR(500),

    -- Confidence that this block IS the contact block.
    confidence         SMALLINT     NOT NULL,

    -- Confidence in full_name specifically. Separate because the two differ
    -- sharply: an email is found by an unambiguous pattern, whereas a name is
    -- inferred from position and capitalisation and is the one field here that
    -- is genuinely a guess. A single column would average a certainty and a
    -- guess into a number describing neither.
    name_confidence    SMALLINT,

    source_line_start  INTEGER,
    source_line_end    INTEGER,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_parsed_contacts_parse
        FOREIGN KEY (parse_id) REFERENCES resume_parses (id) ON DELETE CASCADE,
    CONSTRAINT fk_parsed_contacts_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_parsed_contacts_confidence
        CHECK (confidence BETWEEN 0 AND 100),
    CONSTRAINT ck_parsed_contacts_name_confidence
        CHECK (name_confidence IS NULL OR name_confidence BETWEEN 0 AND 100),

    -- One contact block per parse.
    CONSTRAINT uq_parsed_contacts_parse UNIQUE (parse_id)
);

CREATE INDEX ix_parsed_contacts_user ON parsed_contacts (user_id);


-- ---------------------------------------------------------------------------
-- parsed_skills
-- ---------------------------------------------------------------------------
CREATE TABLE parsed_skills (
    id                 UUID         PRIMARY KEY,
    parse_id           UUID         NOT NULL,
    user_id            UUID         NOT NULL,

    -- As written by the candidate: "ReactJS". Shown back to them verbatim,
    -- because correcting someone's own resume in the UI reads as a bug.
    skill_name         VARCHAR(100) NOT NULL,

    -- Canonical form: "react". What matching actually joins on.
    normalized_name    VARCHAR(100) NOT NULL,

    category           VARCHAR(30)  NOT NULL,
    confidence         SMALLINT     NOT NULL,

    source_line_start  INTEGER,
    source_line_end    INTEGER,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_parsed_skills_parse
        FOREIGN KEY (parse_id) REFERENCES resume_parses (id) ON DELETE CASCADE,
    CONSTRAINT fk_parsed_skills_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_parsed_skills_confidence
        CHECK (confidence BETWEEN 0 AND 100),

    -- A skill appears once per parse however many times it is written.
    CONSTRAINT uq_parsed_skills_parse_name UNIQUE (parse_id, normalized_name)
);

-- The JD set-difference query: "which of these skills does this user lack?"
CREATE INDEX ix_parsed_skills_normalized ON parsed_skills (normalized_name);
CREATE INDEX ix_parsed_skills_parse ON parsed_skills (parse_id);
CREATE INDEX ix_parsed_skills_user ON parsed_skills (user_id);


-- ---------------------------------------------------------------------------
-- parsed_education
-- ---------------------------------------------------------------------------
CREATE TABLE parsed_education (
    id                 UUID         PRIMARY KEY,
    parse_id           UUID         NOT NULL,
    user_id            UUID         NOT NULL,

    institution        VARCHAR(200),
    degree             VARCHAR(150),
    field_of_study     VARCHAR(150),
    start_date         DATE,
    end_date           DATE,
    grade              VARCHAR(20),

    confidence         SMALLINT     NOT NULL,
    source_line_start  INTEGER,
    source_line_end    INTEGER,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_parsed_education_parse
        FOREIGN KEY (parse_id) REFERENCES resume_parses (id) ON DELETE CASCADE,
    CONSTRAINT fk_parsed_education_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_parsed_education_confidence
        CHECK (confidence BETWEEN 0 AND 100)
);

CREATE INDEX ix_parsed_education_parse ON parsed_education (parse_id);
CREATE INDEX ix_parsed_education_user ON parsed_education (user_id);


-- ---------------------------------------------------------------------------
-- parsed_experience
-- ---------------------------------------------------------------------------
CREATE TABLE parsed_experience (
    id                 UUID         PRIMARY KEY,
    parse_id           UUID         NOT NULL,
    user_id            UUID         NOT NULL,

    company            VARCHAR(200),
    job_title          VARCHAR(200),
    start_date         DATE,
    end_date           DATE,
    is_current         BOOLEAN      NOT NULL DEFAULT FALSE,
    description        TEXT,

    confidence         SMALLINT     NOT NULL,
    source_line_start  INTEGER,
    source_line_end    INTEGER,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_parsed_experience_parse
        FOREIGN KEY (parse_id) REFERENCES resume_parses (id) ON DELETE CASCADE,
    CONSTRAINT fk_parsed_experience_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_parsed_experience_confidence
        CHECK (confidence BETWEEN 0 AND 100)
);

CREATE INDEX ix_parsed_experience_parse ON parsed_experience (parse_id);
CREATE INDEX ix_parsed_experience_user ON parsed_experience (user_id);


-- ---------------------------------------------------------------------------
-- parsed_projects
-- ---------------------------------------------------------------------------
CREATE TABLE parsed_projects (
    id                 UUID         PRIMARY KEY,
    parse_id           UUID         NOT NULL,
    user_id            UUID         NOT NULL,

    title              VARCHAR(200),
    description        TEXT,
    tech_stack         TEXT,
    url                VARCHAR(500),

    confidence         SMALLINT     NOT NULL,
    source_line_start  INTEGER,
    source_line_end    INTEGER,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_parsed_projects_parse
        FOREIGN KEY (parse_id) REFERENCES resume_parses (id) ON DELETE CASCADE,
    CONSTRAINT fk_parsed_projects_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_parsed_projects_confidence
        CHECK (confidence BETWEEN 0 AND 100)
);

CREATE INDEX ix_parsed_projects_parse ON parsed_projects (parse_id);
CREATE INDEX ix_parsed_projects_user ON parsed_projects (user_id);


-- ---------------------------------------------------------------------------
-- parsed_certifications
-- ---------------------------------------------------------------------------
CREATE TABLE parsed_certifications (
    id                 UUID         PRIMARY KEY,
    parse_id           UUID         NOT NULL,
    user_id            UUID         NOT NULL,

    name               VARCHAR(200),
    issuer             VARCHAR(200),
    issue_date         DATE,
    expiry_date        DATE,
    credential_url     VARCHAR(500),

    confidence         SMALLINT     NOT NULL,
    source_line_start  INTEGER,
    source_line_end    INTEGER,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_parsed_certifications_parse
        FOREIGN KEY (parse_id) REFERENCES resume_parses (id) ON DELETE CASCADE,
    CONSTRAINT fk_parsed_certifications_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_parsed_certifications_confidence
        CHECK (confidence BETWEEN 0 AND 100)
);

CREATE INDEX ix_parsed_certifications_parse ON parsed_certifications (parse_id);
CREATE INDEX ix_parsed_certifications_user ON parsed_certifications (user_id);


COMMENT ON TABLE parsed_contacts IS
    'Contact block extracted from a parse. One row per parse.';
COMMENT ON TABLE parsed_skills IS
    'Skills found in a parse. normalized_name is what job matching joins on.';
COMMENT ON COLUMN parsed_skills.skill_name IS
    'Verbatim as written by the candidate; shown back to them unaltered.';
COMMENT ON COLUMN parsed_skills.normalized_name IS
    'Canonical lexicon form used for matching, e.g. "react" for "ReactJS".';
