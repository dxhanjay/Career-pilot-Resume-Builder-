# Phase 1.6 — User Flows

**Status:** 📝 Awaiting approval

---

## Why failure paths are drawn here

Every flow below includes what happens when it goes wrong. That is the point of the document.

Happy paths get built because they are what the ticket describes. Failure paths get discovered in
production, at which point the fix is retrofitted into a design that did not anticipate it. A parse
that fails, an AI call that times out, a user who exhausts their quota mid-session — these are
ordinary operating conditions for this product, not exceptions. Designing them now costs a paragraph;
designing them later costs a refactor.

---

## 1. Registration and verification

```mermaid
sequenceDiagram
    actor U as User
    participant FE as React SPA
    participant API as Spring Boot
    participant DB as PostgreSQL
    participant M as Mail

    U->>FE: Fill registration form
    FE->>FE: Client-side validation (React Hook Form)
    FE->>API: POST /auth/register
    API->>API: Validate DTO (@Valid)
    API->>DB: SELECT by LOWER(email)

    alt Email already registered
        API-->>FE: 409 EMAIL_ALREADY_EXISTS
        FE-->>U: "An account with this email exists. Log in?"
    else Available
        API->>API: BCrypt hash (strength 12)
        API->>DB: INSERT user (status=PENDING) + ROLE_USER
        API->>DB: INSERT verification token (SHA-256 hashed)
        API->>M: Send verification email
        Note over API,M: Email send is async — a mail<br/>outage must not fail registration
        API-->>FE: 201 Created
        FE-->>U: "Check your inbox"
    end

    U->>FE: Click link in email
    FE->>API: POST /auth/verify-email {token}
    API->>DB: Look up by token hash

    alt Token expired (>24h) or already used
        API-->>FE: 410 Gone
        FE-->>U: "Link expired" + resend button
    else Valid
        API->>DB: UPDATE user SET status=ACTIVE, email_verified_at=now()
        API->>DB: UPDATE token SET used_at=now()
        API-->>FE: 200 OK
        FE-->>U: Redirect to login
    end
```

**Why email sending is async.** If the mail provider is down and registration blocks on it, nobody
can sign up. Decoupling means the account exists and the user can request a resend, which is a far
better failure than a 500 on the first interaction anyone has with the product.

**Why the token is hashed in the database.** A verification token is a credential — presenting it
activates an account. Storing it in plaintext means a database read is an account takeover. Same
reasoning as refresh tokens.

---

## 2. Login and token refresh

```mermaid
sequenceDiagram
    actor U as User
    participant FE as React SPA
    participant API as Spring Boot
    participant DB as PostgreSQL

    U->>FE: Submit credentials
    FE->>API: POST /auth/login
    API->>DB: Load user + roles

    alt Not found or wrong password
        API->>DB: increment failed_login_attempts
        API-->>FE: 401 (generic message)
        Note over API: Same message either way —<br/>"user not found" is an enumeration oracle
    else Account locked
        API-->>FE: 423 Locked + retry-after
    else Email not verified
        API-->>FE: 403 EMAIL_NOT_VERIFIED + resend option
    else Success
        API->>DB: reset attempt counter, set last_login_at
        API->>API: Sign access JWT (15 min)
        API->>DB: INSERT refresh token (hash, new family_id, 7d)
        API-->>FE: 200 {accessToken, refreshToken, user}
        FE->>FE: Store tokens, set auth context
        FE-->>U: Redirect to dashboard
    end
```

### Transparent refresh

```mermaid
sequenceDiagram
    participant FE as Axios interceptor
    participant API as Spring Boot
    participant DB as PostgreSQL

    FE->>API: GET /resumes (expired access token)
    API-->>FE: 401 TOKEN_EXPIRED

    FE->>FE: Queue in-flight requests
    FE->>API: POST /auth/refresh {refreshToken}
    API->>DB: Look up by token hash

    alt Not found / expired
        API-->>FE: 401
        FE->>FE: Clear auth, redirect to login
    else Already revoked  ⚠ replay
        API->>DB: Revoke ENTIRE family_id
        API-->>FE: 401 TOKEN_REUSE_DETECTED
        FE->>FE: Clear auth, force re-login
        Note over API,DB: A revoked token being presented means<br/>it was stolen. Killing the family logs out<br/>the attacker too — at the cost of logging<br/>out the legitimate user. Correct trade.
    else Valid
        API->>DB: Revoke old, INSERT new (same family, replaced_by_id set)
        API-->>FE: 200 {new pair}
        FE->>FE: Replay queued requests
    end
```

**Queuing in-flight requests during refresh is not optional.** Without it, a dashboard that fires
five parallel requests on load will fire five parallel refreshes when the token expires. Four of them
race, four rotate the token, and three end up presenting a token that was just revoked — which the
reuse detector correctly treats as theft and logs the user out. The interceptor must serialise:
first 401 triggers the refresh, the rest wait on that promise.

---

## 3. Resume upload → parse → review ⭐

The most important flow in the product.

```mermaid
sequenceDiagram
    actor U as User
    participant FE as React SPA
    participant API as Spring Boot
    participant CL as Cloudinary
    participant DB as PostgreSQL
    participant W as Job worker

    U->>FE: Select PDF
    FE->>FE: Check type + size client-side (fast feedback only)
    FE->>API: POST /resumes (multipart)

    API->>API: Read magic bytes
    alt Not a real PDF/DOCX
        API-->>FE: 415 Unsupported
        Note over API: Extension and Content-Type are<br/>attacker-controlled. Bytes are not.
    else > 5 MB
        API-->>FE: 413 Too Large
    else Valid
        API->>API: SHA-256 checksum
        API->>DB: Check (user_id, checksum)
        alt Duplicate
            API-->>FE: 409 + link to existing resume
        else New
            API->>CL: Upload (private, authenticated delivery)
            CL-->>API: public_id + URL
            API->>DB: INSERT resume (status=UPLOADED)
            API->>DB: INSERT job (PARSE_RESUME, QUEUED)
            API-->>FE: 201 {resumeId, jobId}
        end
    end

    FE->>API: GET /jobs/{jobId}  (poll: 2s ×15, then 5s)

    W->>DB: SELECT ... FOR UPDATE SKIP LOCKED
    W->>CL: Download file
    W->>W: PDFBox text extraction

    alt PDFBox yields no usable text
        W->>W: Retry with Apache Tika
        alt Tika also fails
            W->>DB: parse status=FAILED, resume status=PARSE_FAILED
            W->>DB: INSERT notification
            FE-->>U: "We couldn't read this file. It may be a scanned image — try a text-based PDF."
            Note over W,U: An honest failure beats<br/>a confident empty result
        end
    end

    W->>W: Section detection, entity extraction, per-field confidence
    W->>DB: INSERT parse + parsed_* rows
    W->>DB: resume status=PARSED, job SUCCEEDED
    W->>DB: INSERT notification

    FE->>API: GET /jobs/{jobId} → SUCCEEDED
    FE->>API: GET /resumes/{id}/parse
    FE-->>U: ⭐ "Here's what the machine saw"

    U->>FE: Review — low-confidence fields highlighted
    alt Something is wrong
        FE-->>U: "Your two-column layout scrambled the reading order.<br/>Here's how to fix it." → link to builder
    else Looks right
        FE-->>U: "Analyse this resume" → flow 4
    end
```

**This screen is why the product is credible.** Every competitor hands out a score. Almost none show
the parse. A student looking at their own work history rendered as interleaved column fragments
understands the problem instantly and in a way no score can convey — and understands that the tool is
telling them something true rather than generating advice.

**The `PARSE_FAILED` path must be a first-class state, not an error toast.** A scanned-image resume
is a common real-world input, and the correct response is a clear explanation plus a route forward
(use the builder), not a red banner.

---

## 4. ATS analysis

```mermaid
sequenceDiagram
    actor U as User
    participant FE as React SPA
    participant API as Spring Boot
    participant DB as PostgreSQL
    participant W as Job worker
    participant AI as Claude API

    U->>FE: "Analyse my resume"
    FE->>API: POST /analyses {resumeId}

    API->>DB: Check ai_credits_used_month vs cap
    alt Cap exhausted
        API-->>FE: 402 Payment Required
        FE-->>U: "Monthly analysis limit reached. Resets on <date>."
    else Resume not parsed
        API-->>FE: 409 RESUME_NOT_PARSED
    else OK
        API->>DB: INSERT job (ANALYZE_ATS)
        API-->>FE: 202 {jobId}
    end

    W->>DB: Claim job
    W->>W: Deterministic rule pass
    Note over W: Section presence, contact completeness,<br/>layout warnings, length, date gaps.<br/>Reproducible. provenance = RULE.

    W->>AI: Judgement pass (parsed entities + rubric)
    Note over W,AI: The model judges what rules cannot —<br/>bullet quality, outcome vs activity,<br/>clarity. provenance = MODEL.

    alt AI timeout / 5xx
        W->>W: Retry with backoff (max 3)
        alt Still failing
            W->>DB: job FAILED, error recorded
            FE-->>U: "Analysis failed. You have not been charged a credit."
            Note over W,DB: The credit is deducted on success,<br/>not on attempt. A failed call the user<br/>didn't cause must not cost them.
        end
    else Malformed model output
        W->>W: Schema validation fails → retry once
        W->>DB: On second failure, FAILED
    else Valid
        W->>W: Combine rule + model findings, compute sub-scores
        W->>DB: INSERT analysis + findings
        W->>DB: INSERT ai_usage_log (tokens, cost_micro_usd, request_id)
        W->>DB: increment ai_credits_used_month
        W->>DB: job SUCCEEDED
    end

    FE->>API: GET /analyses/{id}
    FE-->>U: Score, sub-scores, findings with evidence quotes
    U->>FE: "Match against a job" → flow 5
```

**Two passes, not one.** The rule pass exists because a model asked "does this resume have a skills
section" will sometimes say no when it does. That question has a deterministic answer, and spending a
model call on it buys inconsistency at a price. The model is reserved for judgements that genuinely
require judgement — which is also what makes the `provenance` label meaningful in the UI.

**Schema validation of model output is mandatory.** The response is parsed into a typed structure and
rejected if it does not conform. Trusting an LLM to return valid JSON is how you get a 500 in
production on a Tuesday.

---

## 5. Job description matching and rewriting

```mermaid
sequenceDiagram
    actor U as User
    participant FE as React SPA
    participant API as Spring Boot
    participant W as Job worker
    participant AI as Claude API
    participant G as Entity guard

    U->>FE: Paste job description
    FE->>API: POST /job-descriptions
    API-->>FE: 201 {jdId}

    FE->>API: POST /matches {resumeId, jdId}
    API-->>FE: 202 {jobId}

    W->>W: Extract JD requirements, tag importance
    Note over W: REQUIRED / PREFERRED / NICE_TO_HAVE
    W->>W: Set-difference against parsed_skills.normalized_name
    W->>AI: Semantic pass for non-literal matches
    Note over W,AI: "Built REST services in Spring" satisfies<br/>"experience with backend frameworks" —<br/>string matching cannot see that
    W->>W: Compute match %
    W->>DB: INSERT match + gaps

    FE-->>U: Match %, gaps grouped by importance
    U->>FE: "Rewrite this bullet"
    FE->>API: POST /rewrites {originalText, section, jdId}

    API->>AI: Rewrite grounded in original + JD
    AI-->>API: Rewritten text
    API->>G: Diff entities: rewritten vs original

    alt New entity introduced
        G-->>API: FLAGGED / REJECTED
        Note over G: A metric, employer, technology, or date<br/>in the output but not the input is a<br/>fabrication. PRD §7.2 is enforced in code,<br/>not in the prompt.
        alt REJECTED
            API->>AI: Retry once with stricter constraint
        else FLAGGED
            API-->>FE: 200 with guardStatus + what was added
            FE-->>U: "⚠ This adds '18%' which isn't in your original.<br/>Only accept if it's true."
        end
    else Clean
        API-->>FE: 200 guardStatus=PASSED
    end

    U->>FE: Accept / reject
    FE->>API: POST /rewrites/{id}/accept
    U->>FE: "Practise the interview" → flow 6
```

**The guard is the difference between a tool and a liability.** A prompt that says "do not invent
experience" reduces fabrication; it does not eliminate it. A programmatic diff between input and
output entities catches what the prompt missed, and — critically — a flagged rewrite is shown to the
user *with the invention highlighted*, so they can accept it if the claim happens to be true and
reject it if it is not. The user stays the author.

---

## 6. Mock interview

```mermaid
sequenceDiagram
    actor U as User
    participant FE as React SPA
    participant API as Spring Boot
    participant DB as PostgreSQL
    participant W as Job worker
    participant AI as Claude API

    U->>FE: Configure: role, seniority, type, count
    FE->>API: POST /interviews {..., resumeId?, jdId?}
    API-->>FE: 202 {jobId}

    W->>DB: Load resume gaps + match gaps if a JD was linked
    W->>AI: Generate blueprint (competency plan)
    W->>DB: Save blueprint on session
    W->>AI: Generate questions from blueprint
    W->>DB: INSERT questions (linked_gap_id where applicable)
    W->>DB: session status=IN_PROGRESS

    loop Each question
        FE->>API: GET /interviews/{id}/questions
        U->>FE: Type answer
        FE->>API: POST /interviews/{id}/questions/{qid}/answer
        API->>DB: INSERT answer
        API->>DB: INSERT job (EVALUATE_INTERVIEW)
        API-->>FE: 202
        Note over FE,U: Evaluation runs in the background —<br/>the user moves to the next question<br/>rather than waiting
        W->>AI: Evaluate against rubric
        W->>DB: INSERT evaluation
    end

    U->>FE: Finish
    FE->>API: POST /interviews/{id}/complete

    alt Some evaluations still pending
        API-->>FE: 202 {jobId}
        FE-->>U: "Scoring your answers…"
    end

    W->>W: Aggregate scores, synthesise strengths/weaknesses
    W->>DB: session COMPLETED, overall_score set

    FE->>API: GET /interviews/{id}/report
    FE-->>U: Score, per-answer feedback, model answers,<br/>and which JD gap each question probed

    alt User abandons mid-session
        Note over DB: Session stays IN_PROGRESS.<br/>Nightly job marks ABANDONED after 7 days.<br/>Answers already given are preserved.
    end
```

**Blueprint before questions.** Generating six questions in one call produces six questions that
sound plausible and cover four competencies with three overlaps. Generating a plan first — "two on
the weakest matched skill, two behavioural on ownership, one on the missing required skill, one
open" — and then filling each slot produces coverage. It also makes a bad session diagnosable: was
the plan wrong, or was the generation from a good plan wrong? Without a stored blueprint you can only
observe that the output was poor.

**Evaluation is async and per-answer.** Blocking the user for 15 seconds between questions destroys
the flow. Evaluating as they go means the report is nearly ready by the time they finish.

---

## 7. Account deletion

```mermaid
sequenceDiagram
    actor U as User
    participant FE as React SPA
    participant API as Spring Boot
    participant DB as PostgreSQL
    participant CL as Cloudinary
    participant P as Nightly purge

    U->>FE: Settings → Delete account
    FE-->>U: Explain consequences; require password + typed confirmation
    U->>FE: Confirm
    FE->>API: DELETE /profile/account {password, confirmation}

    alt Wrong password
        API-->>FE: 401
    else Confirmed
        API->>DB: user status=DELETED, deleted_at=now()
        API->>DB: Revoke all refresh tokens
        API->>DB: INSERT audit_log (ACCOUNT_DELETION_REQUESTED)
        API-->>FE: 204
        FE->>FE: Clear auth
        FE-->>U: "Your account is scheduled for deletion. Data removed within 30 days."
    end

    Note over P: ≤ 30 days later
    P->>CL: Delete every stored file for this user
    P->>DB: DELETE per table WHERE user_id = ?
    Note over P,DB: Flat fan-out, no dependency ordering —<br/>this is what the denormalised user_id<br/>on every table bought (DB design §1.2)
    P->>DB: DELETE user row
    P->>DB: Retain audit_log entry with actor_user_id anonymised
```

**The audit entry survives the user.** Recording that an account was deleted, and when, is required
to demonstrate compliance with the deletion request itself. The actor reference is anonymised so the
record proves the action without retaining the person.

---

## 8. Cross-cutting: the async job pattern

Every long-running operation follows one shape. Building four bespoke variants would mean four
polling implementations, four retry policies, and four different behaviours when a container
restarts.

```mermaid
stateDiagram-v2
    [*] --> QUEUED: API accepts, returns 202
    QUEUED --> RUNNING: worker claims (FOR UPDATE SKIP LOCKED)
    RUNNING --> SUCCEEDED: result written, resultRef set
    RUNNING --> QUEUED: transient failure, attempts < max
    RUNNING --> FAILED: attempts exhausted
    QUEUED --> CANCELLED: user cancels
    RUNNING --> QUEUED: container died — reaper reclaims via stale locked_at
    SUCCEEDED --> [*]
    FAILED --> [*]
    CANCELLED --> [*]
```

The `RUNNING → QUEUED` reclaim edge is the one that matters on Railway. Containers restart on deploy,
on scale, and on the platform's own schedule. Without a reaper watching `locked_at`, every restart
would strand every in-flight job in `RUNNING` forever, and the user would poll a status that never
changes. With it, an interrupted job is picked up by the next worker within a minute.
