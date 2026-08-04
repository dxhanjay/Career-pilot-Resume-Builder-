-- ===========================================================================
-- V1 — Baseline: PostgreSQL extensions
--
-- Phase 2 defines no tables. This migration exists anyway, for two reasons:
--
--   1. It proves the Flyway mechanism end to end — configuration, classpath
--      location, naming convention, database permissions — during the phase
--      that sets it up, rather than discovering a misconfiguration in Phase 3
--      while also debugging authentication.
--
--   2. Extensions must exist before any object that depends on them. Adding an
--      extension in the same migration as the index that needs it works, but
--      separating them keeps a schema migration from failing on a permissions
--      problem that has nothing to do with the schema.
--
-- Migrations are FORWARD-ONLY. There is no V1 down-script and there never will
-- be. A bad migration is corrected by a new migration that fixes it — which is
-- reviewable, testable, and does not run destructive DDL during an incident.
-- ===========================================================================

-- Trigram matching, for the admin user search (`GET /api/v1/admin/users?search=`).
--
-- A LIKE '%term%' query cannot use a normal B-tree index and degrades to a
-- sequential scan over every user. pg_trgm provides a GIN index that makes
-- substring search on email and name viable. Created now so that the Phase 13
-- migration adding those indexes has nothing left to arrange.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Note: gen_random_uuid() is built into PostgreSQL 13+, so no extension is
-- needed for the UUID primary keys described in the database design. pgcrypto
-- is deliberately NOT installed — an unused extension is unnecessary surface.

COMMENT ON EXTENSION pg_trgm IS
    'Trigram index support for admin user search (FR-ADM-01).';
