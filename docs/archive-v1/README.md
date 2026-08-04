# Archive — v1 design (superseded)

This directory holds eleven approved design phases for an earlier iteration of this project,
built on a **Python 3.12 / FastAPI backend with a Next.js 15 frontend**.

On **2026-08-04** the project was restarted as **CareerPilot AI** on a **Java 21 / Spring Boot
backend with a React + Vite frontend**. These documents are retained for reference but are **no
longer binding**. Where they conflict with anything under `docs/phase-01-planning/`, the newer
document wins.

## Why it was archived rather than deleted

Roughly two thirds of the content here is stack-independent and still useful as prior art:

| Still worth reading | Why |
|---|---|
| [`requirements/phase-03-requirement-engineering.md`](requirements/phase-03-requirement-engineering.md) | 95 functional + 62 non-functional requirements. The requirement *set* barely changes with the language. |
| [`architecture/phase-06-database-design.md`](architecture/phase-06-database-design.md) | 38 tables, erasure architecture, retention policy. PostgreSQL either way. |
| [`ai/phase-07-ai-system-design.md`](ai/phase-07-ai-system-design.md) | ATS rubric, hybrid matching, the guard algorithm. Prompt design, not Python. |
| [`ai/phase-11-explainable-ai.md`](ai/phase-11-explainable-ai.md) | Explanation ladder, confidence bands, fairness-as-invariance. |
| [`adr/`](adr/) | 53 decision records with their rejected alternatives. ADR-0002 (candidate-side only) **still stands** and is re-affirmed in the new PRD. |

## What is definitively superseded

- **ADR-0019** — Python/FastAPI + Next.js. Replaced by Java 21 / Spring Boot + React/Vite.
- **ADR-0014** — modular monolith with `import-linter` enforcement. The mechanism is now ArchUnit.
- **ADR-0021, ADR-0022** — Redis broker, managed-PaaS-then-AWS. Replaced by a DB-backed job table on Railway.
- **ADR-0025** — UUIDv7 primary keys via a Python library. Retained as a *goal*; the generation mechanism changes.
- The 22-phase methodology and its numbering. The project now runs a 14-phase plan.

## What carried forward unchanged

**ADR-0002 — candidate-side only.** No employer screening, ranking, or rejection features. This was
re-confirmed at the restart and is recorded again in
[`../phase-01-planning/01-prd.md`](../phase-01-planning/01-prd.md) §7.
