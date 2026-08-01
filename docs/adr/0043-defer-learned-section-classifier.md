# ADR-0043: Defer the learned section classifier until rules are measured

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 9
- **Deciders:** Project owner + engineering team

## Context

ADR-0029 defined a four-layer cascade for section detection: lexicon matching, typographic
heuristics, a **learned sequence classifier**, and an LLM fallback for the remaining tail. Layer 3
is the only genuinely trained model anywhere in the system.

Building it at MVP is tempting. It is a well-understood task, the features are already computed for
the layout analyser, and it would be the project's one piece of real supervised learning — which
has some appeal for a product described as AI-powered.

Two facts make it the wrong thing to build now.

**The corpus is 50 documents, and it is synthetic.** A classifier trained on it would learn the
patterns present in the training data — and Phase 8 already flags generator homogeneity as R-52.
The specific failure mode is subtle and serious: the model learns *our templates' conventions*
("Skills follows Education", "headings are 2pt larger") rather than resume structure in general. It
would score excellently on the corpus and degrade on real documents, and the D12 validation set at
15–20 documents is too small to reliably detect that. **We would convert a corpus limitation into a
model limitation, where it is much harder to see and much harder to fix.**

**We have not measured whether Layers 1–2 are sufficient.** Section headings in resumes are highly
conventional: a small locale-scoped synonym list plus font-size and boldness heuristics resolves the
large majority of real documents. If rules and typography reach F1 ≥ 0.90, Layer 3 adds a training
pipeline, a deployment artefact, non-determinism, and a retraining obligation for **zero measured
gain**.

Building ML before establishing that the deterministic approach is insufficient is exactly the
pattern ADR-0029 was written to avoid, applied to ADR-0029's own design.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Build Layer 3 now | Complete cascade as designed; real ML in the stack | Trains on 50 synthetic documents; likely learns generator conventions; unmeasured need; adds a deployment artefact |
| Skip Layer 3 permanently | Simplest | Removes the option; if rules fall short we would go straight to the expensive LLM fallback for every ambiguous document |
| **Design it, defer building until measured need (chosen)** | No premature ML; the design exists so adoption is quick; forces measurement first | The cascade is incomplete at MVP; if rules do fall short, there is a build step at that moment |
| Replace Layer 3 with a bigger LLM fallback | Fewer components | Higher cost and latency on every ambiguous document; more variance against σ ≤ 2 |

## Decision

**Layer 3 is specified but not built at MVP.** Layers 1, 2, and 4 ship. Section-detection F1 is
measured against the corpus at the S1 gate.

**Layer 3 is introduced only if all three hold:**

1. Layers 1–2 fail to reach F1 ≥ 0.90 (NFR-AI-001) after genuine rule iteration
2. Layer-4 LLM invocation rate exceeds 20% of documents, making the fallback a cost and latency
   problem rather than a tail case
3. The corpus has been expanded to ≥ 150 documents, so training does not overfit the generator

**The design, specified now so adoption is a build rather than a research task:**

| Aspect | Choice | Why not the alternative |
|---|---|---|
| Algorithm | Gradient-boosted trees or a linear-chain CRF | **Not a transformer** — this task is typography and ordering, not language understanding |
| Features | The Phase 9 §9.1 line features, already computed | No new feature engineering needed |
| Size | Small enough to train in seconds, run CPU-only | No GPU, no inference service, no latency cost |
| Interpretability | Feature importances inspectable | Aligns with the explainability the product claims elsewhere |
| Artefact | Serialised model file, versioned in the manifest (ADR-0042) | Fits the existing registry with no new infrastructure |

A CRF is the more natural fit if sequence structure proves important (sections have strong ordering
priors); gradient-boosted trees are simpler if per-line classification suffices. That choice is made
against data, at the point of need.

## Consequences

### Positive
- No ML infrastructure, training pipeline, or model artefact at MVP.
- Avoids the specific failure of encoding synthetic-generator conventions into weights, where the
  problem would be invisible and expensive to undo.
- Forces the measurement that determines whether Layer 3 is needed at all — which is the honest
  order of operations.
- The cascade still functions: Layer 4 handles what Layers 1–2 cannot, so there is no capability
  gap, only a cost and latency one.
- The specification means adoption later is an implementation task with the design already settled.
- Consistent with the project's broader discipline of deferring with explicit conditions rather than
  building speculatively.

### Negative / Costs
- If Layers 1–2 fall short, the LLM fallback carries more traffic than intended — costing money and
  latency and contributing variance — until Layer 3 is built.
- Building Layer 3 later means a build step at a moment when the schedule is already under pressure,
  since the trigger is a missed gate.
- The cascade in Phase 7's documentation describes a layer that does not exist at MVP, which
  requires this ADR to keep the documentation honest.

### Follow-up actions required
- **Slice S1, week 3:** measure section F1 with Layers 1–2 only, and record the Layer-4 invocation
  rate as a distinct metric.
- **If F1 < 0.90:** iterate on rules first — the locale-scoped heading lexicon is the cheapest lever
  and improves explainability at the same time. Only then evaluate Layer 3 against the three
  conditions.
- **Phase 20:** Layer-4 invocation rate is a dashboarded metric with a 20% alert threshold, since it
  is condition 2's trigger and also an early signal of input drift.
- **If Layer 3 is adopted:** train on the development split only (ADR-0040), version the artefact in
  the manifest (ADR-0042), and add a drift check — a trained model degrades as input distribution
  changes, which rules do not.
