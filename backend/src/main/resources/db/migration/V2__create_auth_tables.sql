-- ===========================================================================
-- V2 — Authentication schema
--
-- Six tables: users, roles, user_roles, refresh_tokens, password_reset_tokens,
-- email_verification_tokens.
--
-- Conventions applied throughout, per docs/phase-01-planning/03-database-design.md:
--   * UUID primary keys (no sequential IDs to enumerate)
--   * TIMESTAMPTZ everywhere (never naive TIMESTAMP)
--   * Every status column backed by a CHECK constraint
--   * Every foreign key declares ON DELETE explicitly
--   * Token columns store a SHA-256 hash, never the token itself
-- ===========================================================================


-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id                      UUID         PRIMARY KEY,
    email                   VARCHAR(255) NOT NULL,
    password_hash           VARCHAR(72)  NOT NULL,
    full_name               VARCHAR(120) NOT NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'PENDING',

    email_verified_at       TIMESTAMPTZ,
    last_login_at           TIMESTAMPTZ,

    failed_login_attempts   SMALLINT     NOT NULL DEFAULT 0,
    locked_until            TIMESTAMPTZ,

    -- NFR-COST-01. Counters live here rather than in a ledger table because the
    -- question asked at request time is "may this proceed", which is one read.
    -- Per-call detail is retained in ai_usage_logs (Phase 7) for auditing, so
    -- nothing is lost by not keeping a running ledger.
    ai_credits_used_month   INTEGER      NOT NULL DEFAULT 0,
    ai_credits_reset_at     TIMESTAMPTZ  NOT NULL DEFAULT (date_trunc('month', now()) + INTERVAL '1 month'),

    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    deleted_at              TIMESTAMPTZ,

    CONSTRAINT ck_users_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'DELETED')),

    -- BCrypt output is always 60 characters. The column is 72 for headroom, but
    -- a value shorter than 59 means something other than a BCrypt hash was
    -- written — most plausibly a plaintext password from a bug in a future
    -- refactor. This constraint makes that fail at the INSERT rather than
    -- silently persisting a credential in the clear.
    CONSTRAINT ck_users_password_hash_is_bcrypt
        CHECK (length(password_hash) >= 59),

    CONSTRAINT ck_users_failed_attempts_non_negative
        CHECK (failed_login_attempts >= 0)
);

-- Case-insensitive uniqueness.
--
-- A plain UNIQUE(email) would let "Aditi@example.com" and "aditi@example.com"
-- both register, and the second user could then never log in reliably. The
-- application also lowercases on write; this index is what makes the guarantee
-- true regardless of which code path inserts.
CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email));

CREATE INDEX ix_users_status     ON users (status) WHERE deleted_at IS NULL;
CREATE INDEX ix_users_created_at ON users (created_at DESC);

COMMENT ON COLUMN users.password_hash IS 'BCrypt hash. Never a plaintext password.';
COMMENT ON COLUMN users.status        IS 'PENDING until email verified; DELETED pending 30-day purge.';


-- ---------------------------------------------------------------------------
-- roles / user_roles
--
-- A join table for what is currently two roles looks like over-engineering. It
-- is not: a single `role` column on users means the first requirement for a
-- second role ("this user is a beta tester AND an admin") forces a migration on
-- the busiest table in the system. The join table costs one table now and
-- nothing later.
-- ---------------------------------------------------------------------------
CREATE TABLE roles (
    id          UUID        PRIMARY KEY,
    name        VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(200),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_roles_name CHECK (name IN ('ROLE_USER', 'ROLE_ADMIN'))
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,

    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),

    -- CASCADE: a role assignment is meaningless without its user.
    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    -- RESTRICT: deleting a role that users still hold would silently strip
    -- their permissions. Force the assignments to be dealt with first.
    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT
);

CREATE INDEX ix_user_roles_role ON user_roles (role_id);

-- Seeded here rather than by application startup code. A CommandLineRunner that
-- inserts reference data races with itself when two instances boot during a
-- rolling deploy, and makes the schema's meaning depend on code that ran once.
INSERT INTO roles (id, name, description) VALUES
    (gen_random_uuid(), 'ROLE_USER',  'Standard authenticated user'),
    (gen_random_uuid(), 'ROLE_ADMIN', 'Platform administrator');


-- ---------------------------------------------------------------------------
-- refresh_tokens
--
-- The two columns that carry the security weight are token_hash and family_id.
--
-- token_hash: the raw token is returned to the client exactly once and never
-- stored. A database dump therefore yields nothing usable. Verification hashes
-- the presented value and looks it up.
--
-- family_id: every token descended from one login shares a family. Rotation
-- revokes the old token and issues a new one in the same family. If a REVOKED
-- token is ever presented, that means a token was stolen and replayed — so the
-- entire family is revoked, ending the attacker's session and the victim's
-- together. Without the family, the best available response is rejecting the
-- single replayed token while the attacker's freshly-rotated one keeps working.
-- ---------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id              UUID        PRIMARY KEY,
    user_id         UUID        NOT NULL,
    token_hash      CHAR(64)    NOT NULL,
    family_id       UUID        NOT NULL,

    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    replaced_by_id  UUID,

    user_agent      VARCHAR(300),
    ip_address      VARCHAR(45),          -- 45 chars covers IPv6 with an IPv4 tail

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,

    -- Self-reference recording the rotation chain. SET NULL rather than CASCADE:
    -- purging an old token must not delete its successor.
    CONSTRAINT fk_refresh_tokens_replaced_by
        FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens (id) ON DELETE SET NULL
);

-- Unique: the lookup path, and a guarantee that a hash collision cannot produce
-- two live tokens.
CREATE UNIQUE INDEX ux_refresh_tokens_hash ON refresh_tokens (token_hash);

-- Family revocation reads every row in a family; without this it is a table scan
-- on the hot path of a security incident.
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);

CREATE INDEX ix_refresh_tokens_user_active
    ON refresh_tokens (user_id) WHERE revoked_at IS NULL;

-- Supports the nightly purge of expired rows.
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);

COMMENT ON COLUMN refresh_tokens.token_hash IS 'SHA-256 of the token. The token itself is never stored.';
COMMENT ON COLUMN refresh_tokens.family_id  IS 'Rotation lineage; revoked collectively on reuse detection.';


-- ---------------------------------------------------------------------------
-- password_reset_tokens / email_verification_tokens
--
-- Identical shape, deliberately separate tables. Merging them behind a "type"
-- column would mean one query path where a verification token could be
-- presented to the reset endpoint — a privilege confusion that the type system
-- would not catch. Two tables make that category of bug unrepresentable.
--
-- Both are single-use (used_at) and short-lived, and both store only a hash,
-- for the same reason as refresh tokens: presenting one is sufficient to act.
-- ---------------------------------------------------------------------------
CREATE TABLE password_reset_tokens (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_password_reset_tokens_hash ON password_reset_tokens (token_hash);
CREATE INDEX ix_password_reset_tokens_user        ON password_reset_tokens (user_id);
CREATE INDEX ix_password_reset_tokens_expires     ON password_reset_tokens (expires_at);


CREATE TABLE email_verification_tokens (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_email_verification_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX ux_email_verification_tokens_hash ON email_verification_tokens (token_hash);
CREATE INDEX ix_email_verification_tokens_user        ON email_verification_tokens (user_id);
CREATE INDEX ix_email_verification_tokens_expires     ON email_verification_tokens (expires_at);
