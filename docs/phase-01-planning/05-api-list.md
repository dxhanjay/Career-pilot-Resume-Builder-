# Phase 1.5 — API Specification

**Base path:** `/api/v1`
**Auth:** Bearer JWT in `Authorization`, except where marked Public
**Status:** 📝 Awaiting approval

---

## 1. Conventions

These apply to every endpoint below; they are not repeated per row.

**Versioning in the path.** `/api/v1` from the first commit. Adding a version prefix later means
either breaking every client or maintaining an unversioned alias forever.

**Uniform response envelope.**

```json
{
  "success": true,
  "data": { },
  "message": "Resume uploaded",
  "timestamp": "2026-08-04T10:15:30Z"
}
```

Errors use the same envelope with `success: false` and an `error` object:

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": [
      { "field": "email", "message": "must be a well-formed email address" }
    ],
    "traceId": "b7f3a1c2-…"
  },
  "timestamp": "2026-08-04T10:15:30Z"
}
```

`traceId` is the request correlation ID (NFR-OBS-01), also emitted in the response header and in
every log line for that request. A user support ticket that includes it is diagnosable; one that
says "it broke" is not.

**Pagination.** `?page=0&size=20&sort=createdAt,desc`. Responses return a `PageResponse` DTO
(`content`, `page`, `size`, `totalElements`, `totalPages`, `last`) — never Spring's `Page`, whose
JSON shape is an implementation detail that has changed between Spring versions.

**Status codes.**

| Code | Used for |
|---|---|
| 200 | Successful read or update |
| 201 | Resource created; `Location` header set |
| 202 | Async job accepted — body carries the job ID |
| 204 | Successful delete |
| 400 | Malformed request |
| 401 | Missing / invalid / expired token |
| 403 | Authenticated but not permitted |
| 404 | Not found **or** not owned by the caller — see below |
| 409 | Conflict (duplicate email, duplicate resume) |
| 413 | File exceeds size limit |
| 415 | Unsupported media type |
| 422 | Semantically invalid (validation failure) |
| 429 | Rate limit exceeded |
| 500 | Unhandled — never leaks a stack trace |

**404 rather than 403 for another user's resource.** If `GET /resumes/{id}` returned 403 for a
resume owned by someone else and 404 for one that does not exist, the API would confirm the
existence of other users' data to anyone who enumerated IDs. Both cases return 404. The check is
`findByIdAndUserId`, not `findById` followed by an ownership comparison — the ownership predicate
belongs in the query, where it cannot be forgotten.

**Async endpoints return 202.** Parsing, ATS analysis, JD matching, and interview evaluation all
exceed a reasonable HTTP timeout. Each returns `202 Accepted` with a job ID; the client polls
`GET /jobs/{id}` (§9) until terminal. This is why Railway cold starts do not break the UX (risk R5).

**Rate limits.** Auth endpoints: 5 requests/minute/IP. AI-invoking endpoints: 10/hour/user plus the
per-user credit cap (NFR-COST-01). Everything else: 100/minute/user. Exceeding returns 429 with
`Retry-After`.

---

## 2. Authentication — `/api/v1/auth`

| Method | Path | Auth | Request | Response | Codes |
|---|---|---|---|---|---|
| POST | `/register` | Public | `RegisterRequest` {email, password, fullName} | `UserResponse` | 201, 409, 422 |
| POST | `/login` | Public | `LoginRequest` {email, password} | `TokenResponse` {accessToken, refreshToken, expiresIn, user} | 200, 401, 423, 429 |
| POST | `/refresh` | Public | `RefreshRequest` {refreshToken} | `TokenResponse` | 200, 401 |
| POST | `/logout` | Bearer | `RefreshRequest` | — | 204 |
| POST | `/logout-all` | Bearer | — | — | 204 |
| POST | `/verify-email` | Public | `{token}` | — | 200, 400, 410 |
| POST | `/resend-verification` | Public | `{email}` | — | 202, 429 |
| POST | `/forgot-password` | Public | `{email}` | — | 202, 429 |
| POST | `/reset-password` | Public | `{token, newPassword}` | — | 200, 400, 410 |
| GET | `/me` | Bearer | — | `UserResponse` | 200, 401 |

**`/forgot-password` always returns 202**, whether or not the email exists. Returning 404 for an
unknown address turns the endpoint into a user-enumeration oracle. The same reasoning applies to
`/resend-verification`.

**`/login` can return 423 Locked** after repeated failures (`users.failed_login_attempts`,
`locked_until`). This is distinct from 401 so the client can show a useful message instead of "wrong
password" to someone whose password is correct.

**`/refresh` failure on a revoked token is not just a 401.** It triggers family revocation — every
token in that lineage is invalidated, because presenting a revoked token means the token was stolen
and replayed. The user is logged out everywhere; that is the correct outcome.

---

## 3. Profile — `/api/v1/profile`

| Method | Path | Auth | Request | Response | Codes |
|---|---|---|---|---|---|
| GET | `/` | Bearer | — | `ProfileResponse` | 200 |
| PUT | `/` | Bearer | `UpdateProfileRequest` | `ProfileResponse` | 200, 422 |
| POST | `/avatar` | Bearer | `multipart/form-data` | `{avatarUrl}` | 200, 413, 415 |
| DELETE | `/avatar` | Bearer | — | — | 204 |
| POST | `/change-password` | Bearer | `{currentPassword, newPassword}` | — | 200, 401, 422 |
| DELETE | `/account` | Bearer | `{password, confirmation}` | — | 204 |

`DELETE /account` requires the password again. It is irreversible and initiates the 30-day purge
(NFR-PRIV-01); a mis-click should not be sufficient. All sessions are revoked immediately.

---

## 4. Resumes — `/api/v1/resumes`

| Method | Path | Auth | Request | Response | Codes |
|---|---|---|---|---|---|
| POST | `/` | Bearer | `multipart/form-data` (file, isPrimary?) | `ResumeResponse` | 201, 409, 413, 415, 422 |
| GET | `/` | Bearer | pagination | `PageResponse<ResumeResponse>` | 200 |
| GET | `/{id}` | Bearer | — | `ResumeDetailResponse` | 200, 404 |
| GET | `/{id}/download` | Bearer | — | `{signedUrl, expiresAt}` | 200, 404 |
| DELETE | `/{id}` | Bearer | — | — | 204, 404 |
| PATCH | `/{id}/primary` | Bearer | — | `ResumeResponse` | 200, 404 |

Constraints enforced on upload: `application/pdf` or `.docx` only, verified by **magic bytes** not by
extension or `Content-Type`; ≤ 5 MB; filename sanitised; SHA-256 computed and checked against the
user's existing resumes (409 on duplicate, per FR-RES-05).

`/download` returns a **short-lived signed URL** rather than streaming the file through the API.
Streaming would put resume bytes through a container that is CPU-billed and cold-start-prone, for no
benefit. The signed URL expires in minutes, so the response body is not a durable credential
(NFR-SEC-06).

---

## 5. Parsing — `/api/v1/resumes/{resumeId}/parse`

| Method | Path | Auth | Request | Response | Codes |
|---|---|---|---|---|---|
| POST | `/` | Bearer | — | `JobAcceptedResponse` {jobId} | 202, 404, 409, 429 |
| GET | `/` | Bearer | — | `ParsedResumeResponse` | 200, 404, 409 |
| GET | `/raw-text` | Bearer | — | `{rawText, pageCount, wordCount}` | 200, 404 |

`ParsedResumeResponse` is the structured JSON required by FR-PARSE-04:

```json
{
  "parseId": "…",
  "resumeId": "…",
  "status": "PARSED",
  "parser": { "name": "PDFBOX", "version": "3.0.3" },
  "contact": { "fullName": "…", "email": "…", "phone": "…",
               "links": { "linkedin": "…", "github": "…" }, "confidence": 96 },
  "skills":         [ { "name": "Spring Boot", "normalized": "spring boot",
                        "category": "FRAMEWORK", "confidence": 91,
                        "sourceLines": [22, 22] } ],
  "education":      [ { "institution": "…", "degree": "…", "fieldOfStudy": "…",
                        "startDate": "2021-08", "endDate": "2025-05",
                        "grade": "8.4", "confidence": 88 } ],
  "experience":     [ { "company": "…", "jobTitle": "…", "startDate": "2024-05",
                        "endDate": null, "isCurrent": true,
                        "description": "…", "confidence": 84 } ],
  "projects":       [ { "title": "…", "techStack": "…", "confidence": 79 } ],
  "certifications": [ { "name": "…", "issuer": "…", "confidence": 93 } ],
  "warnings": [
    { "code": "MULTI_COLUMN_LAYOUT",
      "message": "Two-column layout detected; reading order may be unreliable." }
  ]
}
```

Three deliberate properties:

- **Per-field confidence**, not a single parse-level number. The UI shows low-confidence fields
  differently, which is what makes the "here's what the machine saw" screen (FR-PARSE-05) actionable
  rather than merely informative.
- **`sourceLines`** on every extracted entity, so the review screen can highlight where a value came
  from in the original text.
- **`warnings`** carries structural problems that are not extraction failures but predict them —
  a two-column layout, an embedded image, no selectable text at all. This is the mechanism by which
  risk R1 becomes visible to the user rather than silently producing garbage.

---

## 6. ATS analysis — `/api/v1/analyses`

| Method | Path | Auth | Request | Response | Codes |
|---|---|---|---|---|---|
| POST | `/` | Bearer | `{resumeId, jobDescriptionId?}` | `JobAcceptedResponse` | 202, 402, 404, 409, 429 |
| GET | `/{id}` | Bearer | — | `AnalysisResponse` | 200, 404 |
| GET | `/` | Bearer | `?resumeId=&page=&size=` | `PageResponse<AnalysisSummaryResponse>` | 200 |
| GET | `/trend` | Bearer | — | `ScoreTrendResponse` | 200 |
| DELETE | `/{id}` | Bearer | — | — | 204, 404 |

**402 Payment Required** is returned when the user's AI credit cap is exhausted (NFR-COST-01). It is
the honest code for "this operation is allowed but you have no budget" — 429 would imply waiting
helps, and 403 would imply a permissions problem.

`AnalysisResponse` carries scores, findings, and provenance:

```json
{
  "id": "…", "resumeId": "…", "jobDescriptionId": null,
  "scores": { "overall": 68, "keyword": 54, "formatting": 82,
              "content": 71, "grammar": 90 },
  "summary": "…",
  "findings": [
    { "category": "FORMATTING", "severity": "CRITICAL",
      "title": "Two-column layout",
      "detail": "ATS parsers commonly read columns as one interleaved stream.",
      "evidenceSnippet": "Skills          Experience\nJava            Intern, …",
      "suggestion": "Convert to a single-column layout.",
      "provenance": "RULE" },
    { "category": "CONTENT", "severity": "HIGH",
      "title": "Bullets describe activity, not outcome",
      "evidenceSnippet": "Worked on the payments module",
      "suggestion": "State what changed: 'Reduced payment failures by …'",
      "provenance": "MODEL" }
  ],
  "provenance": { "modelId": "claude-opus-5",
                  "promptVersion": "ats-v1.2", "rubricVersion": "r-2026-08" }
}
```

`provenance` on each finding (`RULE` vs `MODEL`) is surfaced in the UI. A deterministic rule and a
model judgement carry different epistemic weight, and presenting them identically would overstate
the second.

`GET /trend` returns `[{version, score, createdAt}]` across the user's resume versions — the data
behind the "watch the score move" moment, and the evidence for the improvement-proof metric.

---

## 7. Job descriptions & matching

### `/api/v1/job-descriptions`

| Method | Path | Auth | Request | Response | Codes |
|---|---|---|---|---|---|
| POST | `/` | Bearer | `{title, company?, rawText, sourceUrl?}` | `JobDescriptionResponse` | 201, 422 |
| GET | `/` | Bearer | pagination | `PageResponse<…>` | 200 |
| GET | `/{id}` | Bearer | — | `JobDescriptionResponse` | 200, 404 |
| PUT | `/{id}` | Bearer | same as POST | `JobDescriptionResponse` | 200, 404, 422 |
| DELETE | `/{id}` | Bearer | — | — | 204, 404 |

### `/api/v1/matches`

| Method | Path | Auth | Request | Response | Codes |
|---|---|---|---|---|---|
| POST | `/` | Bearer | `{resumeId, jobDescriptionId}` | `JobAcceptedResponse` | 202, 402, 404, 429 |
| GET | `/{id}` | Bearer | — | `MatchResponse` | 200, 404 |
| GET | `/` | Bearer | `?resumeId=&jobDescriptionId=` | `PageResponse<…>` | 200 |

`MatchResponse` returns the four percentages, a summary, and gaps grouped by importance:

```json
{
  "matchPercentage": 61,
  "breakdown": { "skills": 58, "keywords": 64, "experience": 55 },
  "gaps": {
    "required":   [ { "type": "MISSING_SKILL", "term": "Kubernetes",
                      "foundInResume": false,
                      "suggestion": "…" } ],
    "preferred":  [ … ],
    "niceToHave": [ … ]
  }
}
```

Grouping by importance server-side rather than returning a flat list is a product decision expressed
in the contract: the client should not have to decide what matters, and two clients should not decide
differently.

### `/api/v1/rewrites`

| Method | Path | Auth | Request | Response | Codes |
|---|---|---|---|---|---|
| POST | `/` | Bearer | `{resumeId, section, originalText, jobDescriptionId?}` | `RewriteResponse` | 200, 402, 422, 429 |
| POST | `/{id}/accept` | Bearer | — | — | 204, 404 |
| GET | `/` | Bearer | `?resumeId=` | `PageResponse<…>` | 200 |

`RewriteResponse` includes `guardStatus` and, when not `PASSED`, what the guard found:

```json
{
  "id": "…",
  "originalText": "Worked on the payments module",
  "rewrittenText": "Reduced payment failure rate by 18% …",
  "guardStatus": "FLAGGED",
  "guardDetail": { "introducedEntities": ["18%"],
                   "reason": "Numeric claim not present in source" }
}
```

This is the enforcement point for PRD §7.2. The model is not trusted to obey "do not invent" — its
output is diffed against its input, and any entity present in the rewrite but absent from the source
is flagged or rejected. A `REJECTED` rewrite is never shown to the user as a suggestion.

---

## 8. Interviews — `/api/v1/interviews`

| Method | Path | Auth | Request | Response | Codes |
|---|---|---|---|---|---|
| POST | `/` | Bearer | `{targetRole, seniority, interviewType, questionCount, resumeId?, jobDescriptionId?}` | `JobAcceptedResponse` | 202, 402, 429 |
| GET | `/{id}` | Bearer | — | `InterviewSessionResponse` | 200, 404 |
| GET | `/` | Bearer | pagination | `PageResponse<InterviewSummaryResponse>` | 200 |
| GET | `/{id}/questions` | Bearer | — | `List<QuestionResponse>` | 200, 404, 409 |
| POST | `/{id}/questions/{qid}/answer` | Bearer | `{answerText, durationMs}` | `JobAcceptedResponse` | 202, 404, 409 |
| POST | `/{id}/complete` | Bearer | — | `JobAcceptedResponse` | 202, 404, 409 |
| GET | `/{id}/report` | Bearer | — | `InterviewReportResponse` | 200, 404, 409 |
| DELETE | `/{id}` | Bearer | — | — | 204, 404 |

`409 Conflict` on `/questions` and `/report` means "the session is not in a state where this makes
sense" — asking for a report on an in-progress session, or for questions before generation finished.
Returning an empty 200 in those cases would be indistinguishable from a session that genuinely has
no questions.

`InterviewReportResponse` carries the overall score, per-question evaluation, aggregate strengths and
weaknesses, and — where `linkedGapId` was set — which JD gap each question was probing.

---

## 9. Jobs — `/api/v1/jobs`

| Method | Path | Auth | Request | Response | Codes |
|---|---|---|---|---|---|
| GET | `/{id}` | Bearer | — | `JobStatusResponse` | 200, 404 |
| GET | `/` | Bearer | `?status=&type=` | `PageResponse<…>` | 200 |
| POST | `/{id}/cancel` | Bearer | — | — | 204, 404, 409 |

```json
{
  "id": "…", "type": "ANALYZE_ATS", "status": "RUNNING",
  "attempts": 1, "referenceId": "…", "resultRef": null,
  "error": null,
  "createdAt": "…", "startedAt": "…", "finishedAt": null
}
```

On `SUCCEEDED`, `resultRef` is the ID of the produced resource — the analysis, the match, the parse.
The client polls this endpoint and then fetches the result; it does not need to know how to construct
the result URL for each job type beyond a type→path mapping it already has.

Polling interval: 2 s for the first 30 s, then 5 s, with a 5-minute ceiling. Server-Sent Events would
be a better fit and are a candidate for Phase 14, but polling has no connection-state to manage
across a platform that restarts containers freely.

---

## 10. Notifications — `/api/v1/notifications`

| Method | Path | Auth | Request | Response | Codes |
|---|---|---|---|---|---|
| GET | `/` | Bearer | `?unreadOnly=&page=&size=` | `PageResponse<…>` | 200 |
| GET | `/unread-count` | Bearer | — | `{count}` | 200 |
| PATCH | `/{id}/read` | Bearer | — | — | 204, 404 |
| PATCH | `/read-all` | Bearer | — | — | 204 |
| DELETE | `/{id}` | Bearer | — | — | 204, 404 |

---

## 11. Dashboard — `/api/v1/dashboard`

| Method | Path | Auth | Response | Codes |
|---|---|---|---|---|
| GET | `/summary` | Bearer | `DashboardSummaryResponse` | 200 |

One aggregated call returning resume count, latest score, score delta, interview count, average
interview score, credits used/remaining, and recent activity. A dashboard assembled from seven
parallel requests is seven chances to be slow and seven loading spinners; one endpoint that does the
aggregation server-side is one round trip on the highest-traffic screen in the product.

---

## 12. Admin — `/api/v1/admin` *(ROLE_ADMIN)*

| Method | Path | Response | Codes |
|---|---|---|---|
| GET | `/users` | `PageResponse<AdminUserResponse>` (`?search=&status=`) | 200, 403 |
| GET | `/users/{id}` | `AdminUserDetailResponse` | 200, 403, 404 |
| PATCH | `/users/{id}/status` | — | 204, 403, 404 |
| PATCH | `/users/{id}/roles` | — | 204, 403, 404 |
| GET | `/analytics/overview` | `AnalyticsOverviewResponse` | 200, 403 |
| GET | `/analytics/usage` | `UsageAnalyticsResponse` (`?from=&to=`) | 200, 403 |
| GET | `/analytics/ai-cost` | `AiCostResponse` (`?from=&to=&groupBy=`) | 200, 403 |
| GET | `/audit-logs` | `PageResponse<AuditLogResponse>` (`?userId=&action=&from=&to=`) | 200, 403 |
| GET | `/jobs` | `PageResponse<AdminJobResponse>` (`?status=`) | 200, 403 |
| POST | `/jobs/{id}/retry` | — | 204, 403, 404, 409 |

**Admin endpoints never return resume content or parsed personal data.** `AdminUserDetailResponse`
carries counts, statuses, timestamps, and cost — enough to investigate a support ticket or a cost
anomaly, not enough to browse a user's career history. The audit log records every admin read, so
that access is accountable even where it is permitted.

---

## 13. Operational

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/actuator/health` | Public | Platform liveness probe (NFR-OBS-02) |
| GET | `/actuator/health/readiness` | Public | Readiness — includes DB connectivity |
| GET | `/actuator/info` | Public | Build version, commit SHA |
| GET | `/actuator/metrics` | ADMIN | Micrometer metrics |
| GET | `/swagger-ui.html` | Non-prod | OpenAPI UI |
| GET | `/v3/api-docs` | Non-prod | OpenAPI JSON |

`/actuator/health` must return quickly and must not depend on the AI provider being reachable — a
health check that fails because a third party is slow will get the container killed for no reason.
Database connectivity belongs in readiness, not liveness, for the same reason.

Swagger is disabled in production. It is a complete map of the attack surface, and the Postman
collection covers the same need for anyone who should have it.

---

## 14. Endpoint count by phase

| Phase | Endpoints | Cumulative |
|---|---|---|
| 3 — Auth | 10 | 10 |
| 5 — Resume upload | 6 | 16 |
| 6 — Parsing | 3 | 19 |
| 7 — ATS analysis | 5 | 24 |
| 8 — Builder | 6 | 30 |
| 9 — Matching + rewrites | 11 | 41 |
| 10 — Interview | 8 | 49 |
| 12 — Profile + dashboard + jobs | 10 | 59 |
| 13 — Notifications + admin | 15 | 74 |
| — Operational | 6 | 80 |

Each row is a Phase deliverable and a Postman folder. NFR-TEST-02 requires an integration test per
endpoint, so this table is also the integration-test budget: 80 tests by Phase 14.
