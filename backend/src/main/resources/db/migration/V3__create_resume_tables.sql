-- ===========================================================================
-- V3 — Resume storage
--
-- One table. Parsing output (V4) hangs off resume_parses rather than off this
-- table, so that re-parsing a file does not destroy the previous extraction.
-- ===========================================================================

CREATE TABLE resumes (
    id                 UUID         PRIMARY KEY,
    user_id            UUID         NOT NULL,

    original_filename  VARCHAR(255) NOT NULL,
    storage_provider   VARCHAR(20)  NOT NULL,
    storage_public_id  VARCHAR(255) NOT NULL,
    storage_url        TEXT         NOT NULL,

    mime_type          VARCHAR(100) NOT NULL,
    size_bytes         INTEGER      NOT NULL,

    -- FR-RES-05. Lets a re-upload of the identical file be recognised instead
    -- of silently costing another parse and another AI analysis.
    checksum_sha256    CHAR(64)     NOT NULL,

    status             VARCHAR(20)  NOT NULL DEFAULT 'UPLOADED',

    -- Increments per user. Powers the score-trend view (FR-ATS-05) that shows
    -- a student their resume improving, which is the retention moment.
    version            SMALLINT     NOT NULL DEFAULT 1,
    is_primary         BOOLEAN      NOT NULL DEFAULT false,

    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    deleted_at         TIMESTAMPTZ,

    CONSTRAINT fk_resumes_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    CONSTRAINT ck_resumes_status
        CHECK (status IN ('UPLOADED', 'PARSING', 'PARSED', 'PARSE_FAILED')),

    CONSTRAINT ck_resumes_provider
        CHECK (storage_provider IN ('CLOUDINARY', 'LOCAL')),

    -- Enforced here as well as in the application. The application limit can be
    -- reconfigured or bypassed by a future code path; the column cannot.
    CONSTRAINT ck_resumes_size_positive
        CHECK (size_bytes > 0 AND size_bytes <= 10485760),

    CONSTRAINT ck_resumes_version_positive
        CHECK (version >= 1)
);

-- The list view. Partial on deleted_at because that view never wants deleted
-- rows, and an index carrying them would be larger for no benefit.
CREATE INDEX ix_resumes_user_created
    ON resumes (user_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- Duplicate detection, scoped per user. Two different users uploading the same
-- public CV template is not a conflict; the same user uploading the same file
-- twice is.
CREATE UNIQUE INDEX ux_resumes_user_checksum
    ON resumes (user_id, checksum_sha256)
    WHERE deleted_at IS NULL;

-- At most one primary resume per user. Expressed as a partial unique index
-- rather than application logic, because "set this one primary" is a two-step
-- update and a crash between the steps would otherwise leave two primaries.
CREATE UNIQUE INDEX ux_resumes_user_primary
    ON resumes (user_id)
    WHERE is_primary = true AND deleted_at IS NULL;

-- Supports the nightly purge of rows soft-deleted more than 30 days ago.
CREATE INDEX ix_resumes_deleted_at
    ON resumes (deleted_at)
    WHERE deleted_at IS NOT NULL;

COMMENT ON COLUMN resumes.storage_public_id IS
    'Provider handle used to fetch or delete the object. Not a URL.';
COMMENT ON COLUMN resumes.checksum_sha256 IS
    'SHA-256 of the file bytes. Duplicate detection, FR-RES-05.';
COMMENT ON COLUMN resumes.deleted_at IS
    'Soft delete. A scheduled job purges the file and the row after 30 days.';
