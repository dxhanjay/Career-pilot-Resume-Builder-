# CareerPilot AI

An AI-powered career assistant that helps students understand why their resume gets rejected,
rehearse for the interview a specific job will actually give them, and prove they improved.

> **The problem:** A student applies to 200 internships, hears nothing from 190, gets rejected by 9,
> and is told why by exactly 0.

---

## Current status

| | |
|---|---|
| **Phase** | 1 of 14 — Project Planning |
| **State** | 📝 Awaiting approval |
| **Code** | None yet — by design. Planning precedes implementation. |

---

## How this project runs

Each phase is designed, explained, and approved before the next begins. No phase is skipped, and no
code is written for a phase that has not been approved. Every requirement carries an ID (`FR-*`,
`NFR-*`); every later artefact — a table, an endpoint, a test — traces back to one.

---

## Phase 1 documents

| Document | Contents |
|---|---|
| [01 — PRD](docs/phase-01-planning/01-prd.md) | Problem, personas, competitors, 40 functional + 19 non-functional requirements, non-goals, metrics, risks, roadmap |
| [02 — Folder Structure](docs/phase-01-planning/02-folder-structure.md) | Monorepo layout, Clean Architecture packages, ArchUnit enforcement rules, naming conventions |
| [03 — Database Design](docs/phase-01-planning/03-database-design.md) | 24 tables with columns, constraints, indexes, retention policy, and the reasoning for each choice |
| [04 — ER Diagrams](docs/phase-01-planning/04-er-diagram.md) | The same schema as Mermaid ER diagrams across five domain clusters |
| [05 — API Specification](docs/phase-01-planning/05-api-list.md) | 80 endpoints with auth, DTOs, status codes, and response contracts |
| [06 — User Flows](docs/phase-01-planning/06-user-flows.md) | Eight critical journeys as sequence diagrams, including the failure paths |
| [07 — Architecture](docs/phase-01-planning/07-architecture.md) | C4 diagrams, layering rules, async job engine, AI pipeline, security model, known limitations |

Earlier design work for a superseded Python/Next.js iteration is preserved under
[docs/archive-v1/](docs/archive-v1/README.md).

---

## Tech stack

| Layer | Choice |
|---|---|
| Frontend | React · Vite · Tailwind CSS · React Router · Axios · React Hook Form |
| Backend | Java 21 · Spring Boot 3 · Spring Security · Spring Data JPA · Maven |
| Database | PostgreSQL 16 (Flyway migrations) |
| Storage | Cloudinary |
| AI | Claude API |
| Parsing | Apache PDFBox · Apache Tika |
| Deployment | Railway (backend) · Vercel (frontend) |
| Docs | Swagger / OpenAPI |
| Testing | JUnit 5 · Mockito · Testcontainers · Postman |

---

## What we're building

One complete loop, not a feature grid:

```
Sign up → upload resume → ⭐ "here's exactly what the machine saw" → ATS score with evidence
        → paste a job description → match % + ranked skill gaps → grounded rewrite suggestions
        → mock interview targeting your remaining gaps → report → fix → re-upload → watch the score move
```

The starred step is the one that makes the product credible. Every competitor gives a score; almost
none show the parse. Showing a student that their two-column layout made their entire work history
invisible to a screener is the moment the tool stops being another opinion.

---

## Standing commitments

Four decisions constrain everything that follows:

1. **Candidate-side only.** We never screen, rank, score, or reject real applicants on an employer's
   behalf. The person whose resume is analysed always owns it. *(PRD §7.1)*
2. **Never fabricate experience.** The system improves how the truth is expressed. Every AI rewrite
   is diffed against its source, and invented entities are flagged before the user sees them.
   *(PRD §7.2)*
3. **No facial or emotion analysis.** Interview feedback comes from answer content only. *(PRD §7.3)*
4. **Every finding carries evidence.** A score without a quote from the resume it came from is an
   assertion, not analysis. *(FR-ATS-03)*

---

## Roadmap

| # | Phase | Status |
|---|---|---|
| 1 | Project Planning | 📝 Awaiting approval |
| 2 | Backend Setup | ⬜ |
| 3 | Authentication | ⬜ |
| 4 | Deploy Backend | ⬜ |
| 5 | Resume Upload | ⬜ |
| 6 | Resume Parsing | ⬜ |
| 7 | ATS Analyzer | ⬜ |
| 8 | Resume Builder | ⬜ |
| 9 | Job Description Matching | ⬜ |
| 10 | AI Mock Interview | ⬜ |
| 11 | Frontend Foundation | ⬜ |
| 12 | Frontend Features | ⬜ |
| 13 | Admin Dashboard | ⬜ |
| 14 | Hardening | ⬜ |
