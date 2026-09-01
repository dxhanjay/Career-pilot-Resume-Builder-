-- ---------------------------------------------------------------------------
-- V10 — CHAR(64) hash columns become VARCHAR(64)
--
-- These four columns were declared CHAR(64) in V2 and V3 and mapped as plain
-- Strings on their entities. PostgreSQL reports CHAR as `bpchar` with JDBC type
-- CHAR; Hibernate expects VARCHAR for a String field. With
-- spring.jpa.hibernate.ddl-auto=validate — which is set in every environment,
-- deliberately — the application refuses to start:
--
--     Schema-validation: wrong column type encountered in column [token_hash]
--     in table [email_verification_tokens]; found [bpchar (Types#CHAR)],
--     but expecting [varchar(64) (Types#VARCHAR)]
--
-- This was never caught because it only appears on a real PostgreSQL boot, and
-- the schema had not yet been applied to one.
--
-- VARCHAR rather than annotating the entities to expect CHAR, because CHAR is
-- the wrong type here on its own merits. PostgreSQL pads CHAR values with
-- trailing spaces to the declared width. A SHA-256 hex digest is always exactly
-- 64 characters so the padding never fires today — but a column whose stored
-- value differs from the value written, in a column used for equality lookup on
-- a security token, is a trap waiting for the first time something writes 63
-- characters. VARCHAR stores what it is given. There is no space or performance
-- advantage to CHAR in PostgreSQL.
--
-- The lengths are unchanged, so this rewrites no data and drops no index.
-- ---------------------------------------------------------------------------

ALTER TABLE refresh_tokens
    ALTER COLUMN token_hash TYPE VARCHAR(64);

ALTER TABLE password_reset_tokens
    ALTER COLUMN token_hash TYPE VARCHAR(64);

ALTER TABLE email_verification_tokens
    ALTER COLUMN token_hash TYPE VARCHAR(64);

ALTER TABLE resumes
    ALTER COLUMN checksum_sha256 TYPE VARCHAR(64);

COMMENT ON COLUMN refresh_tokens.token_hash IS
    'SHA-256 of the token, 64 hex characters. The token itself is never stored.';
COMMENT ON COLUMN resumes.checksum_sha256 IS
    'SHA-256 of the file bytes, 64 hex characters. Duplicate detection, FR-RES-05.';
