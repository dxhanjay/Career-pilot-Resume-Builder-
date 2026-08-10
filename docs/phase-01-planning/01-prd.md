# Phase 1.1 — Product Requirements Document

**Project:** CareerPilot AI
**Version:** 1.0
**Date:** 2026-08-04
**Status:** ✅ Approved 2026-08-10 (re-confirmed against the built backend; see §12)
**Owner:** Project owner + engineering team

---

## 1. Why this document exists

A PRD is not documentation for its own sake. It exists so that every later decision — a database
column, an endpoint, a test — can be traced back to a stated user need. When Phase 7 asks "should
the ATS analyzer return a numeric score or a band?", the answer comes from **§8 Success Metrics**
here, not from taste.

Every requirement in this document has an ID (`FR-*`, `NFR-*`). Later phases cite those IDs. If
code exists that traces to no requirement, it is scope creep; if a requirement traces to no code by
the end, it was dropped and should be recorded as such.

---

## 2. Problem statement

> A student applies to 200 internships, hears nothing from 190, gets rejected by 9, and is told why
> by exactly 0.

Two invisible walls stand between a student and a job, and neither gives feedback:

**Wall 1 — the ATS.** Most mid-size and large employers run applicant tracking software that parses
a resume into structured fields and filters on keywords before a human sees it. A resume that a
person would read favourably can be discarded because a two-column layout confused the parser, the
skills section used "ML" where the posting said "Machine Learning", or the file was a scanned image.
The candidate receives the same silence either way.

**Wall 2 — the interview.** Interviews are unstructured, feedback is legally discouraged, and a
student who fails on the same weakness five times has no way to learn what it is. Practice is
possible in principle — but practising *the wrong thing* is worse than not practising.

The common factor is **absence of a feedback loop**. CareerPilot AI's product thesis is that the
loop can be closed on the candidate's side alone, without the employer's cooperation.

---

## 3. Vision & mission

**Vision.** A student should never be rejected for a reason they could have fixed and were never told.

**Mission.** Show candidates exactly what an automated screener sees in their resume, explain the gap
between that and a specific job, and let them rehearse the interview that job will actually give them
— with evidence, not vibes.

---

## 4. Personas

### P1 — Aditi, final-year engineering student *(primary)*

- 21, B.Tech CSE, tier-2 college, campus placements start in 3 months
- Has a resume built from a Word template a senior sent her; has never seen it parsed
- Applies to 15–30 roles a week through portals she does not control
- **Needs:** to know if her resume survives machine screening, and what to change
- **Fears:** wasting the placement season on a fixable formatting problem
- **Success looks like:** an offer, or at minimum an interview she felt prepared for
- **Pays if:** the free tier proves the product works on *her* resume first

### P2 — Rahul, 2-years-experience switcher *(secondary)*

- 25, service-company developer trying to move into product engineering
- Resume reads as a list of ticket numbers; cannot articulate impact
- **Needs:** to rewrite experience bullets truthfully but competitively, and to practise the
  system-design and behavioural rounds his target companies run
- **Success looks like:** a callback rate that moves

### P3 — Platform administrator *(internal)*

- Needs usage analytics, abuse detection, cost visibility on AI spend, and the ability to
  investigate a user complaint without reading their resume content casually
- **Not a customer.** Admin features exist to keep the platform healthy, not to sell.

---

## 5. Competitive position

| Product | What it does | Gap we exploit |
|---|---|---|
| Jobscan | ATS keyword matching against a JD | Score without explanation; no interview side |
| Resume Worded | Resume scoring + LinkedIn review | Generic rules, not job-specific; no rehearsal |
| Big Interview / Pramp | Interview practice | Not connected to your resume or your target job |
| ChatGPT (direct) | Will rewrite anything you paste | No structure, no persistence, no score you can track, hallucinates experience |

**Our differentiator is the loop, not any single feature.** Competitors sell a resume tool *or* an
interview tool. The compounding value is: analyse → fix → re-analyse → watch the score move →
interview against the *remaining* gaps. No competitor closes that circle.

---

## 6. Feature catalogue

MoSCoW priority, mapped to the build phase that delivers it.

| ID | Feature | Priority | Phase | Notes |
|---|---|---|---|---|
| **FR-AUTH-01** | Email/password registration with verification | Must | 3 | |
| **FR-AUTH-02** | Login issuing access + refresh JWT | Must | 3 | Refresh rotates |
| **FR-AUTH-03** | Refresh token rotation + reuse detection | Must | 3 | Reuse revokes the family |
| **FR-AUTH-04** | Logout (single session + all sessions) | Must | 3 | |
| **FR-AUTH-05** | Password reset via emailed token | Must | 3 | |
| **FR-AUTH-06** | Role-based authorisation (`USER`, `ADMIN`) | Must | 3 | |
| **FR-PROF-01** | View / edit profile (headline, target role, links) | Must | 12 | |
| **FR-PROF-02** | Avatar upload | Should | 12 | Cloudinary |
| **FR-PROF-03** | Account deletion with full data erasure | Must | 13 | Legal requirement, not a nicety |
| **FR-RES-01** | Upload resume (PDF/DOCX, ≤ 5 MB) | Must | 5 | |
| **FR-RES-02** | List own resumes with status | Must | 5 | |
| **FR-RES-03** | Download own resume via signed URL | Must | 5 | |
| **FR-RES-04** | Soft-delete resume; hard-purge after 30 days | Must | 5 | |
| **FR-RES-05** | Duplicate detection by SHA-256 checksum | Should | 5 | Avoids paying to parse the same file twice |
| **FR-PARSE-01** | Extract raw text (PDFBox, Tika fallback) | Must | 6 | |
| **FR-PARSE-02** | Detect sections (education, experience, skills, projects, certs) | Must | 6 | |
| **FR-PARSE-03** | Extract contact block (name, email, phone, links) | Must | 6 | |
| **FR-PARSE-04** | Return structured JSON with per-field confidence | Must | 6 | |
| **FR-PARSE-05** | "Here's what the machine saw" review screen | Must | 12 | The ⭐ moment — see §9 |
| **FR-ATS-01** | ATS score 0–100 with sub-scores | Must | 7 | |
| **FR-ATS-02** | Keyword / formatting / grammar / content analysis | Must | 7 | |
| **FR-ATS-03** | Per-finding evidence quote from the resume | Must | 7 | No unevidenced claims |
| **FR-ATS-04** | Prioritised, actionable suggestions | Must | 7 | |
| **FR-ATS-05** | Score history / trend over resume versions | Should | 12 | Proof the fix worked |
| **FR-BUILD-01** | Resume builder with ATS-safe templates | Should | 8 | |
| **FR-BUILD-02** | Live preview | Should | 8 | |
| **FR-BUILD-03** | PDF export | Should | 8 | |
| **FR-JD-01** | Paste / save a job description | Must | 9 | |
| **FR-JD-02** | Match percentage resume ↔ JD | Must | 9 | |
| **FR-JD-03** | Missing skills and keywords, ranked by importance | Must | 9 | |
| **FR-JD-04** | Targeted improvement suggestions | Must | 9 | |
| **FR-JD-05** | AI rewriting of selected bullets | Must | 9 | Never invents experience — §7 |
| **FR-INT-01** | Start interview: role, seniority, optional JD | Must | 10 | |
| **FR-INT-02** | Generate question set from blueprint | Must | 10 | |
| **FR-INT-03** | Submit text answers | Must | 10 | |
| **FR-INT-04** | Per-answer evaluation against a rubric | Must | 10 | |
| **FR-INT-05** | Session report with score + strengths + gaps | Must | 10 | |
| **FR-INT-06** | Interview history | Must | 10 | |
| **FR-INT-07** | Questions targeted at gaps found in FR-JD-03 | Should | 10 | The loop closing |
| **FR-DASH-01** | User dashboard: resumes, scores, recent activity | Must | 12 | |
| **FR-NOTIF-01** | In-app notifications for async job completion | Must | 13 | |
| **FR-NOTIF-02** | Email notification on long-running job completion | Could | 13 | |
| **FR-ADM-01** | Admin: user list, search, suspend | Must | 13 | |
| **FR-ADM-02** | Admin: platform analytics | Must | 13 | |
| **FR-ADM-03** | Admin: AI usage + cost dashboard | Must | 13 | Unbounded AI spend is the #1 cost risk |
| **FR-ADM-04** | Admin: audit log viewer | Must | 13 | |
| **FR-ADM-05** | Admin: system health / job queue | Should | 13 | |

### Non-functional requirements

| ID | Requirement | Target |
|---|---|---|
| **NFR-PERF-01** | API p95 latency, non-AI endpoints | < 300 ms |
| **NFR-PERF-02** | Resume parse completion | < 10 s p95 |
| **NFR-PERF-03** | ATS analysis completion | < 45 s p95 |
| **NFR-AVAIL-01** | Monthly availability | 99.5 % |
| **NFR-SEC-01** | Passwords hashed with BCrypt (strength ≥ 12) | Mandatory |
| **NFR-SEC-02** | All traffic over TLS | Mandatory |
| **NFR-SEC-03** | Access token TTL ≤ 15 min; refresh ≤ 7 days | Mandatory |
| **NFR-SEC-04** | Every request validated at the DTO boundary | Mandatory |
| **NFR-SEC-05** | No entity ever serialised directly to HTTP | Mandatory |
| **NFR-SEC-06** | Resume files not publicly addressable | Signed URLs only |
| **NFR-SEC-07** | Rate limiting on auth and AI endpoints | Mandatory |
| **NFR-PRIV-01** | User data deleted within 30 days of account deletion | Mandatory |
| **NFR-PRIV-02** | Resume content never used to train models | Mandatory |
| **NFR-COST-01** | Per-user monthly AI spend cap | Enforced, configurable |
| **NFR-TEST-01** | Line coverage on service layer | ≥ 80 % |
| **NFR-TEST-02** | Every endpoint has an integration test | Mandatory |
| **NFR-OBS-01** | Structured JSON logging with correlation ID | Mandatory |
| **NFR-OBS-02** | Health endpoint for platform probes | Mandatory |
| **NFR-A11Y-01** | Frontend meets WCAG 2.1 AA | Target |

---

## 7. Non-goals and standing constraints

These are decisions, not omissions. Each closes off a direction deliberately.

**7.1 — Candidate-side only.** *(Carried forward from the archived ADR-0002 and re-affirmed at the
2026-08-04 restart.)* CareerPilot AI will never let a third party screen, rank, score, or reject real
job applicants. The person whose resume is analysed is always the person who owns it.

*Reasoning:* employer-side hiring AI falls into elevated regulatory categories — the EU AI Act treats
recruitment and candidate-evaluation systems as high-risk; NYC Local Law 144 requires an independent
annual bias audit and candidate notification for automated employment decision tools. Those regimes
bring conformity assessments, documented risk management, and real liability for discriminatory
outcomes. A small team cannot carry that, and the incentives conflict: we cannot honestly advise a
candidate *and* filter them.

*Consequence:* the recruiter dashboard is **not** in the roadmap. Terms of service must prohibit
employer use of candidate-side outputs for screening.

> This is an engineering risk assessment, not legal advice.

**7.2 — Never fabricate experience.** The system improves how the truth is *expressed*. It does not
invent a project, a job, or a skill the user does not have. Every AI rewrite must be traceable to
content the user supplied; a rewrite that introduces a new entity is a bug, and Phase 9 must detect
it programmatically rather than trusting the model.

**7.3 — No facial or emotion analysis.** Interview feedback is derived from answer text only. Even
when audio arrives in a later horizon, delivery feedback comes from transcript and prosody signals,
never from inferred emotional state.

**7.4 — Text-only MVP.** No audio or video interviews before the text loop is proven and stable.

**7.5 — English-only at launch.** The architecture must not *prevent* i18n (no hardcoded user-facing
strings in the backend), but no second locale ships in the MVP.

**7.6 — No mobile app.** Responsive web only.

---

## 8. Success metrics

| Metric | Definition | Target (90 days post-launch) |
|---|---|---|
| **Activation** | % of registrations that complete one resume analysis | ≥ 60 % |
| **⭐ Core loop completion** | % of analysed users who then run a JD match | ≥ 35 % |
| **Improvement proof** | % of users whose 2nd resume version scores higher | ≥ 70 % |
| **Interview engagement** | % of users completing ≥ 1 full mock interview | ≥ 25 % |
| **Retention (W4)** | % active in week 4 | ≥ 20 % |
| **Parse reliability** | resumes parsed with no section-detection failure | ≥ 92 % |
| **AI cost per active user** | monthly AI spend ÷ MAU | ≤ ₹35 / $0.42 |

**Improvement proof is the metric that matters most.** Activation measures curiosity; improvement
proof measures whether the product does the thing it claims. If users analyse once and never
re-upload, the loop is broken regardless of what the other numbers say.

---

## 9. The core user journey

The MVP is one complete journey, not a feature grid:

```
Sign up
  → upload resume
    → ⭐ "here's exactly what the machine saw"      ← the trust-building moment
      → ATS score, with a quote from your resume for every finding
        → paste a job description
          → match %, missing skills, ranked by how much they matter
            → grounded rewrite suggestions (never invented)
              → mock interview targeting your *remaining* gaps
                → report
                  → fix the resume
                    → re-upload
                      → watch the score move          ← the retention moment
```

The starred step is disproportionately important. Every competitor gives a score; almost none show
the *parse*. Showing a student that their two-column layout made their entire work history invisible
is the moment the product becomes credible — and it costs one screen.

---

## 10. Constraints & assumptions

**Constraints**

- Team of 1–2 developers. Every architectural choice must be maintainable by one person at 2 a.m.
- Free/low tier hosting (Railway, Vercel, Cloudinary, Neon-class Postgres) at launch.
- Claude API costs are the dominant variable cost and must be capped per user.
- Railway containers restart; nothing may live only in process memory.

**Assumptions** *(each is a risk if wrong)*

| # | Assumption | If wrong |
|---|---|---|
| A1 | Students will upload a real resume to an unknown site | Activation collapses; needs trust signals earlier |
| A2 | A text-only mock interview is useful enough to engage with | Interview engagement target missed; audio moves up the roadmap |
| A3 | Claude can score a resume consistently enough to be trusted | Scores drift between runs; needs deterministic pre-scoring |
| A4 | Free tier is sustainable at expected volume | Cost cap must tighten or paid tier arrives early |

---

## 11. Top risks

| ID | Risk | P × I | Mitigation |
|---|---|---|---|
| R1 | Resume parsing fails on real-world layouts (two-column, tables, scans) | High × High | Adversarial test corpus in Phase 6; Tika fallback; explicit "we couldn't parse this" state rather than silent garbage |
| R2 | Claude API cost runs away | Med × High | Per-user cap (NFR-COST-01); cheaper model tier for classification; prompt caching on the stable rubric prefix |
| R3 | AI produces inconsistent scores for the same resume | High × Med | Deterministic rule-based scoring for formatting/structure; the model judges only what rules cannot |
| R4 | AI invents experience in a rewrite | Med × High | Programmatic entity-diff between input and output; reject and retry |
| R5 | Railway free tier cold starts break UX | High × Low | Async job model + polling; the UI never blocks on a cold container |
| R6 | Solo-developer bandwidth | High × High | Vertical slices — each phase ships something usable end-to-end |
| R7 | No distribution; nobody finds it | High × High | Frontend is a Vite SPA (per stack decision), so SEO is weak by construction — accepted; distribution relies on campus channels, not organic search |

**R7 is worth stating plainly.** The chosen React + Vite SPA renders client-side, which is
structurally weak for organic search. That is a real cost of the stack decision, accepted knowingly.
If organic discovery later becomes the primary channel, adding SSR is a significant change, not a
config flag.

---

## 12. Release roadmap

The build process from the project brief, with two changes: the recruiter dashboard is removed
(§7.1) and frontend phases are inserted — the original 12-phase plan contained no phase that built
the React application, despite the entire frontend stack being specified.

| # | Phase | Delivers | Status |
|---|---|---|---|
| 1 | **Project Planning** | This document set | ✅ Approved 2026-08-10 |
| 2 | Backend Setup | Spring Boot skeleton, Postgres, config, health | ✅ |
| 3 | Authentication | User/Role, JWT, refresh rotation, tests | ✅ *(no mail provider — Q3)* |
| 4 | Deploy Backend | Railway, env vars, live health endpoint | ✅ |
| 5 | Resume Upload | Cloudinary, validation, history, delete | ✅ |
| 6 | Resume Parsing | PDFBox/Tika, section detection, structured JSON | 🔨 6a ✅ · 6b next |
| 7 | ATS Analyzer | Claude integration, score, evidence, suggestions | ⬜ |
| 8 | Resume Builder | Templates, preview, PDF export | ⬜ |
| 9 | JD Matching | Match %, gaps, grounded rewriting | ⬜ |
| 10 | AI Mock Interview | Blueprint, questions, evaluation, history | ⬜ |
| 11 | **Frontend Foundation** *(replaces Recruiter Dashboard)* | Vite, Tailwind, routing, auth UI, Vercel deploy | ⬜ |
| 12 | **Frontend Features** *(new)* | Dashboard, upload, parse review, analysis, interview UI | ⬜ |
| 13 | Admin Dashboard | Analytics, users, AI cost, audit log, notifications | ⬜ |
| 14 | **Hardening** *(new)* | Security review, perf, observability, CI/CD, docs | ⬜ |

Phases 11–14 are a proposal, not a decision — the phase count and split are the project owner's
call and can be adjusted at Phase 2 approval.

---

## 13. Open questions

| # | Question | Needed by |
|---|---|---|
| Q1 | Monetisation: free-only at launch, or a paid tier from day one? | Phase 5 (affects quota model) |
| Q2 | Is there a target launch date / placement season to hit? | Phase 2 (affects scope trimming) |
| Q3 | Email provider for verification and reset mail? | Phase 3 |
| Q4 | Do we need Indian data-residency, or is any region acceptable? | Phase 4 |

None of these block Phase 2. Q1 and Q3 need answers before the phases that consume them.
