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
