# ADR-0023: Permissive licences only, enforced in CI — and why we are not using the best PDF library

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 5
- **Deciders:** Project owner + engineering team

## Context

ADR-0005 establishes this as a real, monetisable, closed-source SaaS product. That makes
dependency licensing a genuine engineering constraint rather than a formality, and one specific
case forced the issue.

**PyMuPDF (`fitz`) is the fastest and most accurate PDF layout-extraction library in Python.** For
a product whose highest-rated technical risk is resume parsing (R-06), and whose wedge depends on
detecting multi-column layouts and table structures precisely (FR-ATS-003), it is the obvious
first choice.

PyMuPDF is licensed **AGPL-3.0**. The AGPL extends copyleft obligations to software *provided over
a network* — which is exactly what a SaaS platform does. On a plain reading, building our parsing
module on PyMuPDF would create a source-disclosure obligation for the platform. Artifex sells a
commercial licence, which is the legitimate route if we want it.

The general problem this exposes: **licence incompatibility is cheap to avoid at selection time
and expensive to discover after the module is built around a library's API.** Discovering this in
month three, with the parsing module written, would mean either a rewrite, an unplanned commercial
licence purchase, or shipping in violation.

A secondary risk is transitive: a permissively-licensed dependency can pull in a copyleft
dependency of its own, silently, in a routine version bump. Human review at selection time does
not catch that.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| No policy; decide case by case | No process overhead | Inconsistent; transitive dependencies slip through; the expensive discovery happens late |
| **Permissive-only allowlist, CI-enforced (chosen)** | Automated, catches transitive introductions, decisions become explicit | Excludes some genuinely better libraries; requires maintaining an allowlist and an exception register |
| Permit copyleft and open-source the platform | Removes the constraint entirely | Contradicts ADR-0005's commercial premise |
| Buy commercial licences as needed upfront | Best libraries available | Unbudgeted cost at MVP; premature before we know extraction accuracy is actually insufficient |

## Decision

**Only permissive licences — MIT, BSD (2/3-clause), Apache-2.0, ISC, MPL-2.0 — for anything linked
into the product.** Copyleft licences (GPL, AGPL, and LGPL where linking implicates it) require
either an explicit recorded exception with legal review, or a purchased commercial licence.

**Enforcement is automated.** A licence-scanning step in CI (`pip-licenses` and
`license-checker` against an allowlist) fails the build when a disallowed licence appears —
including one introduced transitively by a version bump.

**Consequences for the parsing stack:** we use **pdfplumber** (MIT, on pdfminer.six), **pypdf**
(BSD), **python-docx** (MIT), **Tesseract** (Apache-2.0), and **spaCy** (MIT). PyMuPDF is excluded
unless a commercial licence is purchased.

### Recorded exceptions

Two dependencies are not permissive, and are recorded here rather than passing silently — the
point of a policy is that its exceptions are visible:

| Dependency | Licence | Assessment | Alternative if it becomes a problem |
|---|---|---|---|
| **Dramatiq** | LGPL-3.0 | Used as an unmodified library dependency, not modified or statically linked — the standard permitted use. Accepted as an explicit exception | **Celery** (BSD), a drop-in replacement |
| **Redis** | RSALv2 / SSPL | Consumed as an unmodified managed service, not redistributed — unaffected by the source-availability terms | **Valkey** (BSD fork), a drop-in replacement |

## Consequences

### Positive
- Eliminates a category of late, expensive discovery — including the transitive case that human
  review reliably misses.
- The PyMuPDF decision becomes a **costed, deliberate choice** rather than an accident: if
  extraction accuracy proves insufficient, purchasing the Artifex licence is a planned decision
  with a known price, not an emergency.
- Every exception is visible and has a named fallback, so no dependency is a hidden liability.
- Removes a due-diligence problem before it exists, should the project ever be acquired,
  partnered, or audited.

### Negative / Costs
- **We are deliberately using a slower, less accurate PDF library than the best available.** This
  is a real cost against our riskiest requirement (NFR-AI-001, F1 ≥ 0.90) and is tracked as Risk
  R-32.
- The allowlist and exception register need maintenance.
- CI occasionally blocks a merge over a dependency that is, on inspection, fine — friction that is
  the price of catching the cases that are not.

### Follow-up actions required
- **Phase 14:** licence scan added to the CI pipeline with the allowlist above, from the first
  commit of slice S0.
- **Slice S1:** measure pdfplumber's section-detection F1 against the golden corpus **in week 3**.
  If it falls short of 0.90, escalate in order: (1) supplement with spaCy rules and pypdf
  structural signals, (2) evaluate alternative permissive libraries, (3) **purchase the Artifex
  commercial licence for PyMuPDF** — a bounded, budgeted decision.
- **Phase 7:** the parsing module is written against an internal extraction interface, not against
  pdfplumber's API directly, so that a library swap remains a contained change.
- Maintain the exception register in this ADR; new exceptions supersede this record rather than
  editing it.
