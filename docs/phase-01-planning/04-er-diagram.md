# Phase 1.4 — Entity Relationship Diagrams

**Status:** 📝 Awaiting approval
**Companion to:** [03-database-design.md](03-database-design.md) — that document is the authoritative
column-level specification; this one shows the shape.

---

## Reading these diagrams

Twenty-four tables in one diagram is a picture nobody looks at twice. They are split into the five
clusters from the database design, with a sixth diagram showing only the edges *between* clusters.

Only columns that carry structural meaning — keys, discriminators, status — appear here. Full column
lists are in the companion document.

Cardinality notation: `||--o{` is one-to-many (optional on the many side), `||--||` is one-to-one,
`}o--||` is many-to-one-optional.

---

## 1. Identity

```mermaid
erDiagram
    USERS ||--o{ USER_ROLES : "assigned"
    ROLES ||--o{ USER_ROLES : "grants"
    USERS ||--|| USER_PROFILES : "has"
    USERS ||--o{ REFRESH_TOKENS : "owns"
    USERS ||--o{ PASSWORD_RESET_TOKENS : "requests"
    USERS ||--o{ EMAIL_VERIFICATION_TOKENS : "requests"
    REFRESH_TOKENS ||--o| REFRESH_TOKENS : "replaced_by"

    USERS {
        uuid id PK
        varchar email UK "lowercased, unique"
        varchar password_hash "BCrypt"
        varchar status "PENDING|ACTIVE|SUSPENDED|DELETED"
        timestamptz email_verified_at
        smallint failed_login_attempts
        integer ai_credits_used_month
        timestamptz deleted_at
    }

    ROLES {
        uuid id PK
        varchar name UK "ROLE_USER|ROLE_ADMIN"
    }

    USER_ROLES {
        uuid user_id PK,FK
        uuid role_id PK,FK
    }

    USER_PROFILES {
        uuid user_id PK,FK "one-to-one"
        varchar headline
        varchar target_role
        smallint experience_years
        text avatar_url
    }

    REFRESH_TOKENS {
        uuid id PK
        uuid user_id FK
        varchar token_hash UK "SHA-256, never plaintext"
        uuid family_id "rotation lineage"
        timestamptz expires_at
        timestamptz revoked_at
        uuid replaced_by_id FK
    }

    PASSWORD_RESET_TOKENS {
        uuid id PK
        uuid user_id FK
        varchar token_hash UK
        timestamptz expires_at
        timestamptz used_at
    }

    EMAIL_VERIFICATION_TOKENS {
        uuid id PK
        uuid user_id FK
        varchar token_hash UK
        timestamptz expires_at
        timestamptz used_at
    }
```

The self-referencing edge on `REFRESH_TOKENS` (`replaced_by_id`) plus `family_id` is the rotation
chain. `family_id` is what makes revocation collective rather than per-token — see §3 of the
database design for why that matters.

---

## 2. Resume and parsing

```mermaid
erDiagram
    USERS ||--o{ RESUMES : "uploads"
    RESUMES ||--o{ RESUME_PARSES : "attempted"
    RESUME_PARSES ||--o| PARSED_CONTACTS : "extracts"
    RESUME_PARSES ||--o{ PARSED_SKILLS : "extracts"
    RESUME_PARSES ||--o{ PARSED_EDUCATION : "extracts"
    RESUME_PARSES ||--o{ PARSED_EXPERIENCE : "extracts"
    RESUME_PARSES ||--o{ PARSED_PROJECTS : "extracts"
    RESUME_PARSES ||--o{ PARSED_CERTIFICATIONS : "extracts"

    RESUMES {
        uuid id PK
        uuid user_id FK
        varchar original_filename
        varchar storage_public_id "Cloudinary"
        char checksum_sha256 "dedup"
        varchar status "UPLOADED|PARSING|PARSED|PARSE_FAILED"
        smallint version "per-user, powers trend"
        boolean is_primary
        timestamptz deleted_at
    }

    RESUME_PARSES {
        uuid id PK
        uuid resume_id FK
        varchar parser_name "PDFBOX|TIKA"
        varchar parser_version
        varchar status
        text raw_text
        integer duration_ms
        text error_message
    }

    PARSED_CONTACTS {
        uuid id PK
        uuid parse_id FK
        varchar full_name
        varchar email
        smallint confidence "0-100"
    }

    PARSED_SKILLS {
        uuid id PK
        uuid parse_id FK
        varchar skill_name
        varchar normalized_name "ML -> machine learning"
        smallint confidence
        integer source_line_start
    }

    PARSED_EDUCATION {
        uuid id PK
        uuid parse_id FK
        varchar institution
        varchar degree
        smallint confidence
    }

    PARSED_EXPERIENCE {
        uuid id PK
        uuid parse_id FK
        varchar company
        varchar job_title
        boolean is_current
        smallint confidence
    }

    PARSED_PROJECTS {
        uuid id PK
        uuid parse_id FK
        varchar title
        text tech_stack
        smallint confidence
    }

    PARSED_CERTIFICATIONS {
        uuid id PK
        uuid parse_id FK
        varchar name
        varchar issuer
        smallint confidence
    }
```

Note the shape: parsed entities hang off **`RESUME_PARSES`**, not off `RESUMES`. A resume may be
parsed more than once — PDFBox fails, Tika succeeds; or a parser upgrade re-runs an old file — and
each attempt produces its own entity set. Hanging entities off the resume would force each re-parse
to destroy the previous extraction, losing exactly the before/after comparison that tells you
whether the parser improved.

`PARSED_CONTACTS` is `||--o|` (zero-or-one) rather than one-to-many: a resume has one contact block,
and a failed extraction produces no row rather than a row full of nulls.

---

## 3. Analysis, matching, and building

```mermaid
erDiagram
    RESUMES ||--o{ ATS_ANALYSES : "scored by"
    JOB_DESCRIPTIONS |o--o{ ATS_ANALYSES : "targeted at"
    ATS_ANALYSES ||--o{ ATS_FINDINGS : "produces"

    USERS ||--o{ JOB_DESCRIPTIONS : "saves"
    RESUMES ||--o{ JOB_MATCHES : "compared in"
    JOB_DESCRIPTIONS ||--o{ JOB_MATCHES : "compared in"
    JOB_MATCHES ||--o{ MATCH_GAPS : "identifies"

    RESUMES ||--o{ RESUME_REWRITES : "rewritten in"
    JOB_DESCRIPTIONS |o--o{ RESUME_REWRITES : "targeted at"
    USERS ||--o{ BUILT_RESUMES : "authors"

    ATS_ANALYSES {
        uuid id PK
        uuid resume_id FK
        uuid job_description_id FK "NULL = generic"
        smallint overall_score "0-100"
        smallint keyword_score
        smallint formatting_score
        smallint content_score
        smallint grammar_score
        varchar model_id "provenance"
        varchar rubric_version "provenance"
    }

    ATS_FINDINGS {
        uuid id PK
        uuid analysis_id FK
        varchar category "KEYWORD|FORMATTING|CONTENT|GRAMMAR|STRUCTURE"
        varchar severity "CRITICAL|HIGH|MEDIUM|LOW"
        text evidence_snippet "quoted from resume"
        varchar provenance "RULE|MODEL"
        smallint display_order
    }

    JOB_DESCRIPTIONS {
        uuid id PK
        uuid user_id FK
        varchar title
        varchar company
        text raw_text
        char checksum_sha256
    }

    JOB_MATCHES {
        uuid id PK
        uuid resume_id FK
        uuid job_description_id FK
        smallint match_percentage
        smallint skill_match_percentage
        smallint experience_match_percentage
        varchar model_id
    }

    MATCH_GAPS {
        uuid id PK
        uuid match_id FK
        varchar gap_type "MISSING_SKILL|MISSING_KEYWORD|WEAK_EVIDENCE|EXPERIENCE_SHORTFALL"
        varchar term
        varchar importance "REQUIRED|PREFERRED|NICE_TO_HAVE"
        boolean found_in_resume
    }

    RESUME_REWRITES {
        uuid id PK
        uuid resume_id FK
        uuid job_description_id FK
        varchar section
        text original_text "guard input"
        text rewritten_text
        varchar guard_status "PASSED|FLAGGED|REJECTED"
        boolean accepted
    }

    BUILT_RESUMES {
        uuid id PK
        uuid user_id FK
        varchar template_key
        jsonb content "document structure"
        timestamptz deleted_at
    }
```

`JOB_DESCRIPTIONS` appears as an optional participant (`|o--o{`) in both `ATS_ANALYSES` and
`RESUME_REWRITES`: a user can run a generic analysis with no target job, then re-run it against a
specific posting. The same analysis pipeline serves both, which is why the FK is nullable rather
than there being two separate tables.

---

## 4. Interview

```mermaid
erDiagram
    USERS ||--o{ INTERVIEW_SESSIONS : "conducts"
    RESUMES |o--o{ INTERVIEW_SESSIONS : "informs"
    JOB_DESCRIPTIONS |o--o{ INTERVIEW_SESSIONS : "targets"
    INTERVIEW_SESSIONS ||--o{ INTERVIEW_QUESTIONS : "contains"
    INTERVIEW_QUESTIONS ||--o| INTERVIEW_ANSWERS : "answered by"
    INTERVIEW_ANSWERS ||--o| INTERVIEW_EVALUATIONS : "evaluated by"
    MATCH_GAPS |o--o{ INTERVIEW_QUESTIONS : "targeted by"

    INTERVIEW_SESSIONS {
        uuid id PK
        uuid user_id FK
        uuid resume_id FK
        uuid job_description_id FK
        varchar target_role
        varchar seniority
        varchar interview_type "TECHNICAL|BEHAVIORAL|MIXED"
        varchar status "CREATED|IN_PROGRESS|COMPLETED|ABANDONED"
        smallint overall_score
        jsonb blueprint "the competency plan"
    }

    INTERVIEW_QUESTIONS {
        uuid id PK
        uuid session_id FK
        smallint position UK "unique per session"
        text question_text
        varchar competency
        varchar difficulty
        uuid linked_gap_id FK "closes the loop"
    }

    INTERVIEW_ANSWERS {
        uuid id PK
        uuid question_id FK,UK "one answer per question"
        text answer_text
        integer word_count
        integer duration_ms
    }

    INTERVIEW_EVALUATIONS {
        uuid id PK
        uuid answer_id FK,UK
        smallint score
        jsonb rubric_scores
        text strengths
        text weaknesses
        text improved_answer
        varchar rubric_version
    }
```

The `MATCH_GAPS → INTERVIEW_QUESTIONS` edge is the product thesis expressed as a foreign key. It is
what lets the interview report say *"this question was asked because the job requires Kubernetes and
your resume never mentions it"* — the gap analysis and the rehearsal are one system, not two
features that happen to share a login.

The chain `QUESTIONS ||--o| ANSWERS ||--o| EVALUATIONS` uses zero-or-one at each step so that an
abandoned session is representable: questions with no answers, or answers awaiting evaluation, are
valid states rather than integrity violations.

---

## 5. Platform

```mermaid
erDiagram
    USERS ||--o{ JOBS : "enqueues"
    USERS ||--o{ NOTIFICATIONS : "receives"
    USERS ||--o{ AI_USAGE_LOGS : "incurs"
    USERS |o--o{ AUDIT_LOGS : "acts in"

    JOBS {
        uuid id PK
        uuid user_id FK
        varchar job_type "PARSE_RESUME|ANALYZE_ATS|MATCH_JD|EVALUATE_INTERVIEW"
        uuid reference_id "polymorphic target"
        varchar status "QUEUED|RUNNING|SUCCEEDED|FAILED|CANCELLED"
        smallint attempts
        varchar locked_by "instance id"
        timestamptz locked_at "stuck-job reclaim"
        uuid result_ref
    }

    NOTIFICATIONS {
        uuid id PK
        uuid user_id FK
        varchar type
        varchar title
        varchar link_url
        timestamptz read_at
    }

    AI_USAGE_LOGS {
        uuid id PK
        uuid user_id FK
        varchar feature
        varchar model_id
        integer input_tokens
        integer output_tokens
        integer cache_read_tokens
        bigint cost_micro_usd "integer, not float"
        varchar request_id "provider id"
        varchar status
    }

    AUDIT_LOGS {
        uuid id PK
        uuid actor_user_id FK "NULL = system"
        varchar action
        varchar entity_type
        uuid entity_id
        varchar ip_address
        jsonb metadata
    }
```

`JOBS.reference_id` is intentionally **not** a foreign key. It points at whichever table the job type
implies — a resume for `PARSE_RESUME`, an analysis for `ANALYZE_ATS`. A polymorphic reference cannot
be constrained by the database; the alternative is four nullable FK columns, of which exactly one is
ever populated. That trades a real constraint for a real mess. The chosen design accepts an
unconstrained UUID and relies on the job-type discriminator, which the application sets and never
derives from user input.

`AUDIT_LOGS.actor_user_id` is nullable because the actor may be the system — a scheduled purge, a
job reaper. Attributing a system action to a user would be a lie in the one table that must not
contain lies.

---

## 6. Cross-cluster edges

Everything above, with intra-cluster detail removed — the load-bearing relationships only.

```mermaid
erDiagram
    USERS ||--o{ RESUMES : ""
    USERS ||--o{ JOB_DESCRIPTIONS : ""
    USERS ||--o{ INTERVIEW_SESSIONS : ""
    USERS ||--o{ JOBS : ""
    USERS ||--o{ AI_USAGE_LOGS : ""

    RESUMES ||--o{ RESUME_PARSES : ""
    RESUMES ||--o{ ATS_ANALYSES : ""
    RESUMES ||--o{ JOB_MATCHES : ""
    RESUMES |o--o{ INTERVIEW_SESSIONS : ""

    JOB_DESCRIPTIONS ||--o{ JOB_MATCHES : ""
    JOB_DESCRIPTIONS |o--o{ ATS_ANALYSES : ""
    JOB_DESCRIPTIONS |o--o{ INTERVIEW_SESSIONS : ""

    JOB_MATCHES ||--o{ MATCH_GAPS : ""
    MATCH_GAPS |o--o{ INTERVIEW_QUESTIONS : ""
    INTERVIEW_SESSIONS ||--o{ INTERVIEW_QUESTIONS : ""
```

Two observations from this view:

**`USERS` is the hub of every cluster.** That is the §1.2 rule made visible — the denormalised
`user_id` on every table means account deletion is a fan-out of independent deletes rather than an
ordered traversal, and every authorisation check is a local predicate.

**`RESUMES` and `JOB_DESCRIPTIONS` are the two axes the product turns on.** Every analytical artefact
— parses, scores, matches, interviews — sits at some intersection of those two. That is not an
accident of the schema; it is the product thesis showing through the data model.
