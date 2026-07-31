# ADR-0005: Adopt explicit default answers to unanswered scoping questions

- **Status:** Accepted (provisional — supersede on receipt of real answers)
- **Date:** 2026-07-31
- **Phase:** 2
- **Deciders:** Project owner (by approving Phase 1 without amendment) + engineering team

## Context

Phase 1 §22 raised eight scoping questions whose answers change downstream design:
commercial intent, geography, team capacity, budget, facial analysis, product wedge,
recruiter panel, and mandated technology. Phase 1 was approved without those answers.

Operating Rule 6 says to ask rather than assume. The questions were asked and remain open.
Blocking all further work indefinitely is worse than proceeding, because the remaining design
phases are largely independent of the exact values — but silently assuming is unacceptable,
because a future reader would not know which decisions rest on guesses.

The resolution is to convert implicit assumptions into an **explicit, revisable, recorded
default set**, and to tag every dependent decision so it can be found and revised cheaply.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Block until answered | No guesses | Stalls the project; the questions may never be answered precisely |
| Assume silently | Fast | Future phases inherit invisible assumptions; unrevisable |
| **Record explicit provisional defaults (chosen)** | Progress continues; every guess is greppable and costed | Requires revisiting if answers arrive late |

## Decision

We will proceed on the following defaults, and treat this ADR as the single place they are
recorded:

| Q | Default | Reversal cost |
|---|---|---|
| Commercial intent | Real, monetisable SaaS | 🟢 Low before Phase 6 |
| Geography | India-first, English, global-capable | 🟡 Medium |
| Team & time | 1–2 developers part-time, ~12 weeks to public beta | 🟢 Low |
| Budget | ≤ $150/month infra + AI at MVP | 🟡 Medium |
| Facial analysis | Dropped (see ADR-0003) | 🔴 High |
| Product wedge | "Show what the ATS actually saw" | 🔴 High |
| Recruiter panel | Deferred to Horizon 4 | 🟢 Low |
| Mandated technology | None | 🟢 Low |

Any phase document whose content depends on one of these must say so at the point of
dependence, so the blast radius of a correction is discoverable by search.

## Consequences

### Positive
- Design work proceeds without stalling.
- Every guess is visible, attributed, and costed rather than buried in prose.
- Corrections become a bounded edit rather than an archaeology exercise.

### Negative / Costs
- Two defaults (wedge, facial analysis) are expensive to reverse; late correction is costly.
- Provisional status must be actively re-checked, or defaults harden into unexamined facts.

### Follow-up actions required
- Re-confirm before Phase 5 (stack) and Phase 6 (schema) — the two phases where the budget,
  geography, and commercial-intent defaults become concrete and expensive.
- When a real answer arrives, supersede this ADR rather than editing it.
