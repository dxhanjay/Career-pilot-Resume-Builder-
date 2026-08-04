# ADR-0050: The rubric is the explanation; reject post-hoc attribution methods

- **Status:** Accepted
- **Date:** 2026-08-02
- **Phase:** 11
- **Deciders:** Project owner + engineering team

## Context

"Explainable AI" in practice usually means SHAP or LIME: post-hoc attribution methods that estimate
how much each input feature contributed to a model's output. Both work by fitting a simpler
surrogate around a black box and reporting what *the surrogate* attends to.

They exist because the underlying model is opaque. That premise does not hold here.

ADR-0030 established that the model never produces a score — it answers decidable questions and
returns categorical or extractive results with source spans. A pure function then converts those
judgements into points using a versioned rubric (ADR-0013). The consequence is that **each triggered
rule's `points_delta` is its contribution, exactly and by construction** — not estimated, not
approximated, not inferred from perturbing inputs around a decision boundary.

Applying SHAP to that would substitute an approximation for an exact answer. It would also add
compute cost, introduce an artefact users must be taught to read, and — most importantly — imply
that the underlying decision is opaque when it is not. That last point is the real cost: presenting
an approximate attribution alongside an exact rule invites the reader to trust the wrong one.

There is a real question about where post-hoc methods *would* be needed, and it should be answered
now rather than assumed away.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Adopt SHAP or LIME | Familiar to reviewers; standard "XAI" answer | **Approximates something we can state exactly**; compute cost; a second explanation artefact users must learn; implies opacity that does not exist |
| Attention visualisation | Visually compelling | Attention weights are not a faithful account of causal contribution; not available through provider APIs anyway |
| Model-generated rationale | Reads naturally; easy | A plausible narrative, not a verified account — see ADR-0049's reasoning about manufactured rigour, and Phase 11 §8.2 |
| **The rubric breakdown is the explanation (chosen)** | Exact, free, already stored, human-readable, publishable | Only applies to the deterministic scoring layer; says nothing about the model's internals |

## Decision

**The rubric breakdown is the explanation. No post-hoc attribution method is used.**

The score explanation is a projection over stored data: for each triggered rule, its identifier,
category, weight, point contribution, provenance label (ADR-0051), and the source span that caused
it. Every number in the report traces to a published rule and a line of the user's document.

**Post-hoc methods become necessary only if an opaque model enters the scoring path.** Two deferred
decisions would do that, and each carries the obligation with it:

| Deferred decision | If adopted, requires |
|---|---|
| **ADR-0043** — learned Layer-3 section classifier | Feature importances for the classifier; interpretable-by-construction models (GBT/CRF) were chosen partly for this reason |
| **ADR-0039** — fine-tuning | A genuine explainability strategy, since a fine-tuned model's behaviour is not inspectable through prompts |

Neither is adopted at MVP. Recording the linkage means the obligation travels with the decision
rather than being discovered afterwards.

**What this decision does not claim.** It explains the *system's* decision — the rubric applied to a
set of judgements — not the *model's* reasoning for any individual judgement. Phase 11 §8.2 states
that limit explicitly and refuses to fill it with a generated rationale.

## Consequences

### Positive
- The explanation is **exact rather than approximate** — a stronger claim than any attribution
  method can make.
- Zero compute cost: the explanation is a database projection, available instantly and identically
  on every view, and survives an AI provider outage.
- One explanation artefact for users to understand, not two with differing fidelity.
- The rubric is human-readable and published (ADR-0053), so the explanation is checkable by anyone
  rather than trusted on authority.
- Reinforces the architectural direction: keeping the decision layer deterministic pays off again,
  after determinism (ADR-0030), testability (Phase 9 T1), and degraded mode (Phase 7 §26).

### Negative / Costs
- To a reviewer expecting SHAP plots, the absence reads as a gap — this ADR is the answer, and it
  will need repeating.
- Explains nothing about individual model judgements; that limit is real and permanent.
- If an opaque model is ever added to the scoring path, an explainability strategy must be built
  then, at a less convenient time than now.
- The exactness depends on the rubric remaining the sole scoring mechanism — any shortcut that lets
  a model influence points directly would silently invalidate this decision.

### Follow-up actions required
- **Phase 12:** the explanation payload is a projection over stored `findings`, `category_scores`,
  and rubric data — never a recomputation, so it cannot drift from the score it explains.
- **Phase 13:** the score breakdown is presented as a waterfall showing each rule's contribution,
  with the span linked.
- **Phase 19:** a test asserts that the sum of displayed contributions equals the stored
  `overall_score` — an explanation that does not reconcile is a defect, not a rounding artefact.
- **If ADR-0043 or ADR-0039 is ever adopted:** that ADR must specify its explainability strategy,
  and this one is superseded in part.
- Any proposal to let a model emit points directly supersedes this ADR and ADR-0030 together.
