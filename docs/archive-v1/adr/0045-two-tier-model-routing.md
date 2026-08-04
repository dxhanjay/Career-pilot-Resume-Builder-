# ADR-0045: Two model tiers — small for categorical work, medium for guarded generation

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 10
- **Deciders:** Project owner + engineering team

## Context

Phase 7 §24 identified seven distinct LLM-invoking tasks and sketched a mapping to model classes.
Phase 10 must turn that into a concrete routing decision, and the natural instinct is three tiers:
small for cheap high-volume work, medium for the middle, large for anything where quality is
user-visible.

Two facts from earlier phases make the three-tier answer wrong here.

**First, Phase 7's design deliberately removed the need for frontier capability.** ADR-0030
established that the model never emits a score — it answers decidable questions and returns
categorical or extractive results with spans. "Does this bullet begin with an action verb, and
where?" does not require a frontier model; it requires reliable structured output. The tasks that
*are* generative — rewrites, question generation — sit behind ADR-0032's guard, which verifies
grounding mechanically rather than trusting model judgement.

**Second, cost.** NFR-COST-001 allows ~₹8 per analysis and Phase 5 §22 showed AI spend is the
variable that decides whether the project stays inside its infrastructure budget. A large tier
would raise per-operation cost for quality we cannot demonstrate we need — and Phase 10 §12's
arithmetic shows even routing *everything* through the medium tier lands around ₹4–5, inside budget.
The headroom is real, but it is headroom for the medium tier, not for a third one above it.

There is also an operational argument. Every tier is a model to pin in the manifest (ADR-0042),
evaluate in the bake-off, monitor for drift, and re-verify on selection. Tiers are not free.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| One tier for everything | Simplest; one model to evaluate and monitor | Either overpays on high-volume categorical work or underperforms on generation |
| **Two tiers: small + medium (chosen)** | Matches the actual task shapes; cost-justified by §12; two models to manage | Requires routing logic and per-task tier assignment |
| Three tiers: small + medium + large | Best possible quality on the hardest task | No task demonstrably needs it; raises cost against a tight ceiling; a third model to evaluate, pin, and monitor |
| Per-task model selection | Theoretically optimal per task | Combinatorial evaluation cost; unmanageable manifest; over-fitting to eval noise |

## Decision

**Two model tiers.**

| Tier | Tasks | Why |
|---|---|---|
| **Small** | Section-detection fallback (L4), content-quality judgements, grammar | Categorical, batched, high-volume. ADR-0030 made these decidable questions, so capability is not the constraint — schema reliability is |
| **Medium** | JD requirement extraction, rewrite generation, question generation, answer evaluation | Structured reasoning or bounded generation; user-visible quality; each sits behind the guard or a rubric |

**No large tier at MVP.** Reopening requires all three:

1. A specific task demonstrably fails its quality gate on the medium tier, after prompt iteration
   documented in the Phase 9 experiment log.
2. The cost model still fits NFR-COST-001 with that task routed to a large tier.
3. The bake-off (Phase 10 §14) shows a measurable improvement on **our** evals — D1, D6, D7, or
   D10 — not on public benchmarks.

Tier assignment lives in the manifest (ADR-0042), so moving a task between tiers is a reviewable
diff rather than a code change, and every artefact records which tier produced it.

## Consequences

### Positive
- Cost stays comfortably inside NFR-COST-001 — §12 estimates ₹1.4–2.0 per analysis against a ₹8
  ceiling.
- Only two models to evaluate in the bake-off, pin in the manifest, and monitor for drift.
- The split follows the architecture rather than vendor marketing: categorical work is cheap
  because Phase 7 made it categorical, not because we accepted lower quality.
- Headroom exists to route a task upward from small to medium if the bake-off demands it, without
  breaching budget.
- Fewer tiers means fewer places for a provider change to have unnoticed effects.

### Negative / Costs
- If a generative task genuinely needs frontier capability, we will discover it in the bake-off
  rather than having provisioned for it — costing a round of re-evaluation.
- Two tiers means routing logic and a per-task assignment to maintain.
- The small tier's viability depends on schema-validity performance that is unverified until the
  bake-off runs (Risk R-63).

### Follow-up actions required
- **Phase 10 bake-off:** evaluate both tiers separately; the small tier's schema-validity gate
  (≥99.5%) is the one most likely to eliminate candidates.
- **Phase 12:** tier assignment read from the manifest; `platform.ai_calls` records the tier and
  model per call, so cost attribution is per-tier (NFR-COST-006).
- **Phase 20:** cost and quality tracked per tier — a tier drifting on either is the signal to
  reconsider the assignment.
- **If R-63 fires** (small tier fails schema validity): route everything to medium and re-run the
  cost model. §12 shows this is affordable — a cost increase, not a blocker.
