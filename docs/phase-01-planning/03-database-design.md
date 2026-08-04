# Phase 1.3 — Database Design

**Engine:** PostgreSQL 16
**Migrations:** Flyway (versioned SQL, never `ddl-auto: update`)
**Status:** 📝 Awaiting approval

---

## 1. Design principles

Six rules that the 24 tables below all obey. Each exists because of a specific failure mode.

**1.1 — UUID primary keys.** Sequential integers leak business information (a resume with id 47
tells a competitor how many resumes exist) and make ID enumeration attacks trivial. UUIDs cost 16
bytes instead of 8 and index slightly worse; we pay that. Generated as UUIDv7 where the library
allows, so that values remain roughly time-ordered and B-tree inserts stay near the right edge of
the index rather than scattering.

**1.2 — Every table owning user data carries `user_id` directly.** Even where it is derivable by
joining. `ats_analyses` has `user_id` even though `resume_id → resumes.user_id` would give the same
answer. Two reasons: authorisation checks become a single-table predicate rather than a join (and a
missed join is a data-leak bug), and account deletion becomes `DELETE FROM x WHERE user_id = ?` per
table rather than a dependency-ordered graph traversal. The denormalisation is deliberate.

**1.3 — Soft delete for user-visible content, hard delete for everything else.** `resumes` has
`deleted_at`; a user who deletes a resume by accident can be helped. A background purge hard-deletes
after 30 days, satisfying NFR-PRIV-01. Tokens, jobs, and logs are hard-deleted — nobody ever wants
an accidentally-deleted refresh token back.

**1.4 — `TIMESTAMPTZ`, never `TIMESTAMP`.** Railway containers, developer laptops, and users are in
different zones. A naive timestamp is a bug waiting for the first daylight-saving transition.

**1.5 — JSONB only for genuinely open-ended structures.** Model output shape changes as prompts
evolve; forcing it into columns means a migration every prompt revision. But anything queried,
filtered, or aggregated gets a real column. The test is: *will a WHERE clause ever touch this?* If
yes, column. If it is only ever read back whole and rendered, JSONB.

**1.6 — Money and scores are integers.** Scores are `SMALLINT` 0–100. AI cost is stored in
**micro-USD** (`BIGINT`), not a float — a per-request cost of $0.0034 is 3400, exactly representable,
and summing a million of them introduces no drift.

---

## 2. Schema overview

24 tables in five clusters.

| Cluster | Tables |
|---|---|
| **Identity** | `users`, `roles`, `user_roles`, `refresh_tokens`, `password_reset_tokens`, `email_verification_tokens`, `user_profiles` |
| **Resume** | `resumes`, `resume_parses`, `parsed_contacts`, `parsed_skills`, `parsed_education`, `parsed_experience`, `parsed_projects`, `parsed_certifications` |
| **Analysis** | `ats_analyses`, `ats_findings`, `job_descriptions`, `job_matches`, `match_gaps`, `resume_rewrites`, `built_resumes` |
| **Interview** | `interview_sessions`, `interview_questions`, `interview_answers`, `interview_evaluations` |
| **Platform** | `jobs`, `notifications`, `ai_usage_logs`, `audit_logs` |

---

## 3. Identity cluster

### `users`

| Column | Type | Constraints | Notes |
|---|---|---|---|
| `id` | UUID | PK | |
| `email` | VARCHAR(255) | NOT NULL, UNIQUE | Stored lowercase; uniqueness on a `LOWER(email)` index |
| `password_hash` | VARCHAR(72) | NOT NULL | BCrypt output; 72 is BCrypt's ceiling |
| `full_name` | VARCHAR(120) | NOT NULL | |
| `status` | VARCHAR(20) | NOT NULL, DEFAULT `'PENDING'` | `PENDING` → `ACTIVE` → `SUSPENDED` / `DELETED` |
| `email_verified_at` | TIMESTAMPTZ | NULL | |
| `last_login_at` | TIMESTAMPTZ | NULL | |
| `failed_login_attempts` | SMALLINT | NOT NULL, DEFAULT 0 | Lockout after N |
| `locked_until` | TIMESTAMPTZ | NULL | |
| `ai_credits_used_month` | INTEGER | NOT NULL, DEFAULT 0 | Enforces NFR-COST-01 |
| `ai_credits_reset_at` | TIMESTAMPTZ | NOT NULL | |
| `created_at` / `updated_at` | TIMESTAMPTZ | NOT NULL | |
| `deleted_at` | TIMESTAMPTZ | NULL | |

Indexes: `ux_users_email_lower` on `LOWER(email)`, `ix_users_status`.

*Why the credit counters live on `users` rather than a ledger table:* a ledger is the right design
if you ever need to answer "what was this user's balance on March 3rd". We do not — we need "can
this request proceed", which is a single read. `ai_usage_logs` retains the per-call detail for
auditing, so nothing is actually lost. Revisit if billing arrives.

### `roles` / `user_roles`

`roles`: `id UUID PK`, `name VARCHAR(50) UNIQUE NOT NULL` (`ROLE_USER`, `ROLE_ADMIN`).
`user_roles`: `user_id`, `role_id`, composite PK, both FKs `ON DELETE CASCADE`.

A join table for two roles looks like over-engineering. It is not: a single `role` column on `users`
means the first requirement for a second role ("this user is a beta tester *and* an admin") forces a
migration on the busiest table in the system. The join table costs one extra table now and nothing
later.

### `refresh_tokens`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `user_id` | UUID FK → `users` | CASCADE |
| `token_hash` | VARCHAR(64) NOT NULL UNIQUE | **SHA-256 of the token, never the token** |
| `family_id` | UUID NOT NULL | Rotation lineage |
| `expires_at` | TIMESTAMPTZ NOT NULL | |
| `revoked_at` | TIMESTAMPTZ NULL | |
| `replaced_by_id` | UUID NULL FK → self | |
| `user_agent` / `ip_address` | VARCHAR | For "your active sessions" |
| `created_at` | TIMESTAMPTZ NOT NULL | |

Two details carry real security weight:

**Hashing the token.** A database dump of plaintext refresh tokens is a full account-takeover of
every user. Hashed, it is worthless. The token is only ever compared by hashing the presented value.

**`family_id` enables reuse detection.** On refresh we revoke the old token and issue a new one in
the same family. If a *revoked* token is ever presented again, that means someone replayed a stolen
token — so we revoke **the entire family**, logging the legitimate user out and killing the
attacker's session too. Without the family, the best you can do is reject the one token while the
attacker's freshly-rotated one keeps working.

### `password_reset_tokens` / `email_verification_tokens`

Same shape: `id`, `user_id`, `token_hash` (SHA-256), `expires_at`, `used_at`, `created_at`.
Single-use, short TTL (1 hour reset, 24 hours verification). Hashed for the same reason.

### `user_profiles`

`user_id` UUID PK **and** FK → `users` (one-to-one), plus `headline VARCHAR(160)`, `phone VARCHAR(30)`,
`location VARCHAR(120)`, `target_role VARCHAR(120)`, `experience_years SMALLINT`,
`linkedin_url` / `github_url` / `portfolio_url TEXT`, `avatar_url TEXT`,
`avatar_public_id VARCHAR(255)`, `updated_at`.

Split from `users` because it is optional, larger, and read on a different path. Auth reads `users`
on every request and should not drag profile text through the buffer cache with it.

---

## 4. Resume cluster

### `resumes`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `user_id` | UUID FK → `users` | CASCADE, indexed |
| `original_filename` | VARCHAR(255) NOT NULL | Sanitised before storage |
| `storage_public_id` | VARCHAR(255) NOT NULL | Cloudinary handle |
| `storage_url` | TEXT NOT NULL | Base URL; signed at read time |
| `mime_type` | VARCHAR(100) NOT NULL | Validated against magic bytes, not the extension |
| `size_bytes` | INTEGER NOT NULL | |
| `checksum_sha256` | CHAR(64) NOT NULL | FR-RES-05 |
| `status` | VARCHAR(20) NOT NULL | `UPLOADED` → `PARSING` → `PARSED` / `PARSE_FAILED` |
| `version` | SMALLINT NOT NULL DEFAULT 1 | Increments per user, powers score trend |
| `is_primary` | BOOLEAN NOT NULL DEFAULT false | |
| `created_at` / `deleted_at` | TIMESTAMPTZ | |

Indexes: `ix_resumes_user_created` on `(user_id, created_at DESC) WHERE deleted_at IS NULL` —
partial, because the list view never wants deleted rows and the index should not carry them;
`ux_resumes_user_checksum` on `(user_id, checksum_sha256) WHERE deleted_at IS NULL`.

*MIME validation is a security control, not a convenience.* Trusting the `Content-Type` header or
the filename extension is how a `.pdf.exe` or an HTML file with a script payload gets stored and
later served. Phase 5 must sniff the leading bytes.

### `resume_parses`

`id`, `resume_id` (FK, indexed), `user_id`, `raw_text TEXT`, `parser_name VARCHAR(50)`
(`PDFBOX` / `TIKA`), `parser_version VARCHAR(20)`, `status VARCHAR(20)`,
`page_count SMALLINT`, `word_count INTEGER`, `duration_ms INTEGER`, `error_message TEXT`,
`created_at`.

One row per parse *attempt*, not per resume — when PDFBox fails and Tika succeeds, both attempts are
recorded. That history is the raw material for improving the parser; discarding failures discards
exactly the data you need.

### Parsed-entity tables

Six tables, all with `id`, `parse_id` (FK CASCADE, indexed), `user_id`, `confidence SMALLINT`
(0–100), `source_line_start` / `source_line_end` INTEGER, `created_at`, plus:

| Table | Additional columns |
|---|---|
| `parsed_contacts` | `full_name`, `email`, `phone`, `location`, `linkedin_url`, `github_url`, `portfolio_url` |
| `parsed_skills` | `skill_name VARCHAR(100)`, `category VARCHAR(50)`, `normalized_name VARCHAR(100)` |
| `parsed_education` | `institution`, `degree`, `field_of_study`, `start_date`, `end_date`, `grade VARCHAR(20)` |
| `parsed_experience` | `company`, `job_title`, `start_date`, `end_date`, `is_current BOOLEAN`, `description TEXT` |
| `parsed_projects` | `title`, `description TEXT`, `tech_stack TEXT`, `url` |
| `parsed_certifications` | `name`, `issuer`, `issue_date`, `expiry_date`, `credential_url` |

**Why normalised tables rather than one JSONB blob.** FR-JD-03 requires "which required skills are
missing", which is a set difference against the user's skills — a query, not a render. `parsed_skills`
with `normalized_name` makes that an indexed join. In JSONB it is a full scan with array
containment operators, and skill normalisation (`ML` → `machine learning`) has nowhere to live.

**`source_line_start/end` is what makes evidence possible.** FR-ATS-03 requires quoting the resume
back to the user for every finding. Without a pointer from the extracted entity into the raw text,
the best the UI can do is assert. With it, the UI can highlight.

**`confidence` is stored per field, not per parse.** A parser can be certain about the email and
guessing about the job title. A single parse-level confidence would average those into something
that describes neither.

---

## 5. Analysis cluster

### `ats_analyses`

`id`, `resume_id` (FK), `user_id` (indexed), `job_description_id` (FK, NULL — generic analysis when
null), `overall_score SMALLINT` (0–100), `keyword_score`, `formatting_score`, `content_score`,
`grammar_score` (all SMALLINT), `summary TEXT`, `model_id VARCHAR(60)`, `prompt_version VARCHAR(20)`,
`rubric_version VARCHAR(20)`, `input_tokens` / `output_tokens INTEGER`, `duration_ms INTEGER`,
`created_at`.

`model_id`, `prompt_version`, and `rubric_version` are not bookkeeping. When a user asks "why did my
score drop when I didn't change anything", these three columns are the only way to answer. A score
without its provenance is unfalsifiable.

### `ats_findings`

`id`, `analysis_id` (FK CASCADE, indexed), `category VARCHAR(30)`
(`KEYWORD` / `FORMATTING` / `CONTENT` / `GRAMMAR` / `STRUCTURE`), `severity VARCHAR(20)`
(`CRITICAL` / `HIGH` / `MEDIUM` / `LOW`), `title VARCHAR(200)`, `detail TEXT`,
`evidence_snippet TEXT`, `suggestion TEXT`, `provenance VARCHAR(20)` (`RULE` / `MODEL`),
`display_order SMALLINT`.

**`provenance` distinguishes deterministic findings from model judgements.** "Your resume has no
Skills section" is a rule — reproducible, checkable, and correct every time. "Your project
descriptions bury the outcome" is a model judgement. Labelling them differently in the UI is honest;
merging them presents a guess with the authority of a fact.

### `job_descriptions`

`id`, `user_id` (indexed), `title VARCHAR(200)`, `company VARCHAR(200)`, `raw_text TEXT NOT NULL`,
`source_url TEXT`, `checksum_sha256 CHAR(64)`, `created_at`, `deleted_at`.

### `job_matches`

`id`, `resume_id` (FK), `job_description_id` (FK), `user_id` (indexed),
`match_percentage SMALLINT`, `skill_match_percentage`, `keyword_match_percentage`,
`experience_match_percentage`, `summary TEXT`, `model_id`, `prompt_version`, tokens, `created_at`.
Index on `(resume_id, job_description_id, created_at DESC)`.

### `match_gaps`

`id`, `match_id` (FK CASCADE, indexed), `gap_type VARCHAR(30)`
(`MISSING_SKILL` / `MISSING_KEYWORD` / `WEAK_EVIDENCE` / `EXPERIENCE_SHORTFALL`),
`term VARCHAR(150)`, `importance VARCHAR(20)` (`REQUIRED` / `PREFERRED` / `NICE_TO_HAVE`),
`found_in_resume BOOLEAN`, `suggestion TEXT`, `display_order SMALLINT`.

`importance` is what turns a keyword list into advice. Twenty missing keywords is noise; three
missing **required** skills is a decision.

### `resume_rewrites`

`id`, `user_id`, `resume_id` (FK), `job_description_id` (FK, NULL), `section VARCHAR(50)`,
`original_text TEXT NOT NULL`, `rewritten_text TEXT NOT NULL`,
`guard_status VARCHAR(20)` (`PASSED` / `FLAGGED` / `REJECTED`), `guard_detail TEXT`,
`accepted BOOLEAN NULL`, `model_id`, `prompt_version`, `created_at`.

`original_text` is stored alongside the rewrite for two reasons. It is the input to the entity-diff
guard (§7.2 of the PRD — detecting fabricated experience requires knowing what was true before), and
it is what "undo" restores.

### `built_resumes`

`id`, `user_id` (indexed), `title VARCHAR(150)`, `template_key VARCHAR(50)`,
`content JSONB NOT NULL`, `last_exported_at`, `created_at`, `updated_at`, `deleted_at`.

JSONB is correct here, unlike in the parsed-entity tables. Builder content is user-authored document
structure that is only ever loaded whole, edited, and rendered. Nothing queries inside it.

---

## 6. Interview cluster

### `interview_sessions`

`id`, `user_id` (indexed), `resume_id` (FK NULL), `job_description_id` (FK NULL),
`target_role VARCHAR(120) NOT NULL`, `seniority VARCHAR(30)`,
`interview_type VARCHAR(30)` (`TECHNICAL` / `BEHAVIORAL` / `MIXED`),
`question_count SMALLINT`, `status VARCHAR(20)` (`CREATED` / `IN_PROGRESS` / `COMPLETED` / `ABANDONED`),
`overall_score SMALLINT NULL`, `blueprint JSONB`, `started_at`, `completed_at`, `created_at`.

`blueprint` stores the competency plan the question set was generated from — "3 questions on the
candidate's weakest matched skill, 2 behavioural on ownership, 1 on the missing required skill".
Storing the plan separately from the questions means a poor session can be diagnosed as either a bad
plan or bad generation from a good plan. Without it, you can only observe that the output was bad.

### `interview_questions`

`id`, `session_id` (FK CASCADE, indexed), `position SMALLINT NOT NULL`, `question_text TEXT NOT NULL`,
`competency VARCHAR(80)`, `difficulty VARCHAR(20)`, `expected_signals JSONB`,
`linked_gap_id UUID NULL FK → match_gaps`, `created_at`.
Unique on `(session_id, position)`.

`linked_gap_id` is the loop closing (FR-INT-07): this question exists *because* the JD match found
this gap. It also lets the report say "you were asked about Kubernetes because the role requires it
and your resume doesn't mention it" — which is far more useful than a question appearing from
nowhere.

### `interview_answers`

`id`, `question_id` (FK CASCADE, UNIQUE — one answer per question), `session_id`, `user_id`,
`answer_text TEXT NOT NULL`, `word_count INTEGER`, `duration_ms INTEGER`, `submitted_at`.

### `interview_evaluations`

`id`, `answer_id` (FK CASCADE, UNIQUE), `session_id`, `score SMALLINT`,
`rubric_scores JSONB`, `strengths TEXT`, `weaknesses TEXT`, `improved_answer TEXT`,
`model_id`, `prompt_version`, `rubric_version`, tokens, `created_at`.

Separate from `interview_answers` so an evaluation can be regenerated — after a rubric revision, or
when a model call fails — without touching the user's submitted answer. The answer is the user's
data; the evaluation is ours.

---

## 7. Platform cluster

### `jobs`

The async execution backbone.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `user_id` | UUID FK, indexed | |
| `job_type` | VARCHAR(40) NOT NULL | `PARSE_RESUME`, `ANALYZE_ATS`, `MATCH_JD`, `EVALUATE_INTERVIEW` |
| `reference_id` | UUID NOT NULL | The resume / analysis / session being worked on |
| `status` | VARCHAR(20) NOT NULL | `QUEUED` → `RUNNING` → `SUCCEEDED` / `FAILED` / `CANCELLED` |
| `attempts` | SMALLINT NOT NULL DEFAULT 0 | |
| `max_attempts` | SMALLINT NOT NULL DEFAULT 3 | |
| `error_message` | TEXT NULL | |
| `result_ref` | UUID NULL | Row produced on success |
| `locked_by` | VARCHAR(80) NULL | Instance identifier |
| `locked_at` | TIMESTAMPTZ NULL | Enables stuck-job reclaim |
| `created_at` / `started_at` / `finished_at` | TIMESTAMPTZ | |

Index: `ix_jobs_status_created` on `(status, created_at) WHERE status IN ('QUEUED','RUNNING')`.

**Why a table rather than Redis or a real queue.** Parsing takes seconds and AI analysis takes tens
of seconds; neither can run inside an HTTP request without timing out. A managed queue is the
textbook answer but adds a second stateful service, a second failure mode, and a second bill — for a
system whose entire expected throughput is low. Postgres `SELECT … FOR UPDATE SKIP LOCKED` gives
correct concurrent claim semantics on the database we already run, and `locked_at` lets a
`@Scheduled` reaper reclaim jobs orphaned by a container restart. If throughput ever justifies a
broker, this table becomes its outbox rather than being thrown away.

### `notifications`

`id`, `user_id` (indexed), `type VARCHAR(40)`, `title VARCHAR(200)`, `body TEXT`,
`link_url VARCHAR(300)`, `read_at TIMESTAMPTZ NULL`, `created_at`.
Index: `(user_id, created_at DESC) WHERE read_at IS NULL`.

### `ai_usage_logs`

`id`, `user_id` (indexed), `feature VARCHAR(40)`, `model_id VARCHAR(60)`,
`input_tokens` / `output_tokens` / `cache_read_tokens INTEGER`,
`cost_micro_usd BIGINT`, `latency_ms INTEGER`, `status VARCHAR(20)`,
`error_code VARCHAR(60) NULL`, `request_id VARCHAR(80)`, `created_at`.
Index: `(user_id, created_at DESC)`, `(created_at DESC)`.

Every AI call, successful or not. This is what makes FR-ADM-03 and NFR-COST-01 possible, and it is
the difference between discovering a cost problem in a dashboard and discovering it in an invoice.
`request_id` is the provider's request identifier — the only thing that makes a support ticket
about a bad response actionable.

### `audit_logs`

`id`, `actor_user_id UUID NULL` (null for system actions), `action VARCHAR(60)`,
`entity_type VARCHAR(60)`, `entity_id UUID NULL`, `ip_address VARCHAR(45)`,
`user_agent VARCHAR(300)`, `metadata JSONB`, `created_at`.
Index: `(actor_user_id, created_at DESC)`, `(entity_type, entity_id)`.

Security-relevant events only — login, logout, password change, role change, admin actions, account
deletion, resume deletion. Not a general activity feed; an audit log that logs everything is an
audit log nobody reads.

---

## 8. Retention and deletion

| Data | Retention | Mechanism |
|---|---|---|
| Soft-deleted resumes | 30 days, then hard purge (file + row) | Nightly `@Scheduled` |
| Expired / revoked refresh tokens | 30 days | Nightly |
| Used or expired reset/verification tokens | 7 days | Nightly |
| Terminal jobs (`SUCCEEDED`/`FAILED`) | 90 days | Nightly |
| Read notifications | 90 days | Nightly |
| `ai_usage_logs` | 400 days | Cost analysis needs year-over-year |
| `audit_logs` | 400 days | Security investigation window |
| **Deleted account** | All rows purged within 30 days | Per-table `DELETE WHERE user_id` — this is what §1.2 bought |

---

## 9. Integrity rules

- Every FK declares its `ON DELETE` behaviour explicitly. `CASCADE` where the child is meaningless
  without the parent (findings without an analysis); `RESTRICT` where orphaning would hide a bug.
- Every status column is backed by a `CHECK` constraint listing valid values. An enum stored as a
  string with no constraint is a typo away from a silently invalid row.
- Every score column carries `CHECK (score BETWEEN 0 AND 100)`. Model output is not trusted to be in
  range.
- No nullable foreign key exists without a documented reason for the null (`job_description_id` on
  `ats_analyses` is null for a generic, JD-free analysis).
- All migrations are forward-only, versioned, and reviewed. `spring.jpa.hibernate.ddl-auto` is
  `validate` in every environment including local — so that a drift between entity and schema fails
  at startup rather than at 3 a.m. in production.
