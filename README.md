# AI Resume Analyzer & Mock Interview Platform

A production-ready SaaS platform that helps job seekers understand why their resume gets
rejected, rehearse for the interview a specific job will actually give them, and prove they
improved.

> **One-line problem statement:** Job seekers are rejected before a human ever reads their
> resume, and rejected again in interviews for reasons no one ever tells them — because the
> feedback loop in hiring is broken in both directions.

---

## Current status

| | |
|---|---|
| **Phase** | 1 of 22 — Problem Definition |
| **State** | 📝 Awaiting approval |
| **Code** | None yet — by design. Design phases precede implementation. |

---

## How this project runs

Development follows a 22-phase Agile + Production Engineering methodology. Each phase is
designed, explained, and approved before the next begins. No phase is skipped, and no code
is written for a phase that has not been approved.

Each phase documents: objective · why it matters · deliverables · architecture · folder
structure · technology choices with alternatives · implementation strategy · best practices
· security · scalability · risks · production-readiness checklist.

---

## Navigation

| Document | Contents |
|---|---|
| [docs/phase-01-problem-definition.md](docs/phase-01-problem-definition.md) | Problem, personas, competitors, USP, metrics, scope, non-goals, roadmap, risks |
| [docs/adr/README.md](docs/adr/README.md) | Architecture Decision Record log — every significant decision and its reasoning |

Directories for later phases (`docs/product/`, `docs/requirements/`, `docs/architecture/`,
`docs/ai/`, `docs/ops/`, `docs/security/`) are created as those phases are approved.

---

## Phase progress

| # | Phase | Status |
|---|---|---|
| 1 | Problem Definition | 📝 Awaiting approval |
| 2 | Product Planning | ⬜ |
| 3 | Requirement Engineering | ⬜ |
| 4 | System Architecture | ⬜ |
| 5 | Technology Stack | ⬜ |
| 6 | Database Design | ⬜ |
| 7 | AI System Design | ⬜ |
| 8 | Dataset Strategy | ⬜ |
| 9 | AI/ML Pipeline | ⬜ |
| 10 | Model Comparison | ⬜ |
| 11 | Explainable AI | ⬜ |
| 12 | Backend Architecture | ⬜ |
| 13 | Frontend Architecture | ⬜ |
| 14 | DevOps | ⬜ |
| 15 | MLOps | ⬜ |
| 16 | Cloud Deployment | ⬜ |
| 17 | Security | ⬜ |
| 18 | Performance Optimization | ⬜ |
| 19 | Testing | ⬜ |
| 20 | Monitoring | ⬜ |
| 21 | Scaling | ⬜ |
| 22 | Maintenance | ⬜ |

---

## Standing commitments

Four decisions already constrain everything that follows:

1. **[ADR-0001](docs/adr/0001-record-architecture-decisions.md)** — every significant
   decision is recorded, with its rejected alternatives.
2. **[ADR-0002](docs/adr/0002-candidate-side-only-scope.md)** — candidate-side only. We
   never screen, rank, or reject real applicants on an employer's behalf.
3. **[ADR-0003](docs/adr/0003-no-facial-emotion-analysis.md)** — no facial-expression or
   emotion inference. Delivery feedback comes from audio and transcript signals only.
4. **[ADR-0004](docs/adr/0004-no-fabricated-experience.md)** — the system improves how the
   truth is expressed. It never invents experience the user does not have.
