# Architecture Decision Records

A running, immutable log of significant decisions made on this project, in
[Michael Nygard's format](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions).

## Why

This project runs across 22 phases. Without a decision log, Phase 14 will silently
contradict Phase 5 and nobody will notice until deployment. An ADR captures not just
*what* was decided but *why*, and *what it costs us* — so a future reader (including
future-you) can tell the difference between a deliberate constraint and an accident.

## Rules

1. **Immutable.** Never edit an accepted ADR's decision. To change it, write a new ADR
   that supersedes it, and mark the old one `Superseded by ADR-NNNN`.
2. **Numbered sequentially**, zero-padded to 4 digits, never reused.
3. **One decision per record.**
4. **Write it when the decision is made**, not retroactively.
5. A decision is "significant" if reversing it later would cost more than a day, affect
   more than one module, or change the product's legal/ethical posture.

## Statuses

`Proposed` → `Accepted` → (`Deprecated` | `Superseded by ADR-NNNN`)

## Index

| ID | Title | Status | Phase |
|---|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions in ADRs | Accepted | 1 |
| [0002](0002-candidate-side-only-scope.md) | Build candidate-side only; no employer screening | Accepted | 1 |
| [0003](0003-no-facial-emotion-analysis.md) | Do not build facial-expression / emotion analysis | Proposed | 1 |
| [0004](0004-no-fabricated-experience.md) | The system never fabricates user experience | Accepted | 1 |
| [0005](0005-default-product-assumptions.md) | Adopt explicit defaults for unanswered scoping questions | Accepted (provisional) | 2 |
| [0006](0006-mvp-scope-boundary.md) | MVP is a text-only golden path in six vertical slices | Accepted | 2 |
| [0007](0007-credit-ledger-model.md) | Meter AI usage with an append-only credit ledger | Accepted | 2 |
| [0008](0008-job-search-pass-pricing.md) | Sell a 90-day Job Search Pass, not a monthly subscription | Accepted | 2 |
| [0009](0009-availability-slo-and-error-budget.md) | Target 99.5% availability with an explicit error budget | Accepted | 3 |
| [0010](0010-async-first-processing.md) | All AI work is asynchronous; the API never blocks on a model call | Accepted | 3 |
| [0011](0011-accessibility-and-text-parity.md) | WCAG 2.2 AA enforced in CI, and text mode is permanent | Accepted | 3 |
| [0012](0012-no-training-on-user-data.md) | No training on user content without explicit opt-in | Accepted | 3 |
| [0013](0013-i18n-ready-l10n-deferred.md) | i18n-ready now, l10n deferred, rubric parameterised by locale | Accepted | 3 |
| [0014](0014-modular-monolith-with-enforced-boundaries.md) | Modular monolith with machine-enforced boundaries + worker tier | Accepted | 4 |
| [0015](0015-ai-provider-port-and-guard-stage.md) | All model access through one port; all output passes a guard | Accepted | 4 |
| [0016](0016-transactional-outbox.md) | Publish domain events via a transactional outbox | Accepted | 4 |
| [0017](0017-cookie-sessions-with-rotating-refresh.md) | httpOnly cookie sessions with rotating refresh + family revocation | Accepted | 4 |
| [0018](0018-rejected-architectural-patterns.md) | Patterns deliberately not adopted, and what would reopen them | Accepted | 4 |
| [0019](0019-python-fastapi-backend-nextjs-frontend.md) | Python + FastAPI backend; TypeScript + Next.js frontend | Accepted | 5 |
| [0020](0020-postgresql-single-datastore.md) | PostgreSQL as the single datastore, including vectors and documents | Accepted | 5 |
| [0021](0021-redis-broker-postgres-truth.md) | Redis as broker, because PostgreSQL already holds job truth | Accepted | 5 |
| [0022](0022-managed-paas-then-aws.md) | Managed PaaS (Render) for MVP; AWS ap-south-1 as a triggered migration | Accepted | 5 |
| [0023](0023-permissive-licences-only.md) | Permissive licences only, enforced in CI | Accepted | 5 |
| [0024](0024-schema-per-module-no-cross-schema-fks.md) | One schema per module; no foreign keys across schemas | Accepted | 6 |
| [0025](0025-uuidv7-primary-keys.md) | UUIDv7 primary keys, generated in the application | Accepted | 6 |
| [0026](0026-user-id-on-every-pii-table.md) | Every PII table carries `user_id`, even when derivable | Accepted | 6 |
| [0027](0027-jsonb-usage-policy.md) | JSONB only for versioned, whole-read structures | Accepted | 6 |
| [0028](0028-hash-chained-audit-log.md) | The audit log is hash-chained and verified nightly | Accepted | 6 |
| [0029](0029-deterministic-cascade-llm-last.md) | Deterministic methods first; the LLM handles only the tail | Accepted | 7 |
| [0030](0030-llm-emits-judgements-not-scores.md) | The LLM emits categorical judgements with spans, never scores | Accepted | 7 |
| [0031](0031-requirement-level-hybrid-matching.md) | Match at requirement level with hybrid retrieval | Accepted | 7 |
| [0032](0032-guard-novel-entity-diff.md) | The guard detects fabrication by entity diff against source and JD | Accepted | 7 |
| [0033](0033-interview-blueprint-then-slot-generation.md) | Deterministic interview blueprint, then per-slot generation | Accepted | 7 |
| [0034](0034-synthetic-first-no-scraped-resumes.md) | Synthetic-first corpus; no scraped or unverified resume datasets | Accepted | 8 |
| [0035](0035-generator-emits-ground-truth.md) | Generate structured truth first, then render — labels come free | Accepted | 8 |
| [0036](0036-real-resumes-never-in-repo.md) | Real resumes never enter the repository or CI | Accepted | 8 |
| [0037](0037-git-lfs-not-dvc.md) | Version the corpus with Git LFS, not DVC | Accepted | 8 |
| [0038](0038-dataset-version-recorded-with-every-eval.md) | Every eval result records its dataset version | Accepted | 8 |
| [0039](0039-no-finetuning-at-mvp.md) | No fine-tuning at MVP; three reopening conditions | Accepted | 9 |
| [0040](0040-frozen-holdout-and-k-fold-calibration.md) | Frozen holdout at release gates only; k-fold calibration on dev | Accepted | 9 |
| [0041](0041-no-rag-in-core-loop.md) | No RAG in the core loop; retrieval ≠ RAG | Accepted | 9 |
| [0042](0042-pinned-manifest-as-registry.md) | A pinned YAML manifest in Git is the model registry | Accepted | 9 |
| [0043](0043-defer-learned-section-classifier.md) | Defer the learned section classifier until rules are measured | Accepted | 9 |

## Template

```markdown
# ADR-NNNN: <short imperative title>

- **Status:** Proposed | Accepted | Deprecated | Superseded by ADR-NNNN
- **Date:** YYYY-MM-DD
- **Phase:** N
- **Deciders:** <who>

## Context
What forces are at play? What constraint, requirement, or problem prompted this?
State facts, not conclusions.

## Options Considered
| Option | Pros | Cons |
|---|---|---|

## Decision
What we will do, stated as "We will …".

## Consequences
### Positive
### Negative / Costs
### Follow-up actions required
Which later phases inherit obligations from this decision.
```
