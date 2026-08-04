# ADR-0029: Deterministic methods first; the LLM handles only the tail

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 7
- **Deciders:** Project owner + engineering team

## Context

The natural instinct when building an "AI product" is to route every hard problem through a large
language model. It is fast to build, it demos well, and for section detection, entity extraction,
and layout analysis it would plausibly work.

Four requirements make it the wrong choice here:

- **NFR-AI-003** requires ATS score reproducibility of σ ≤ 2 across five runs. Every model call in
  the path is a source of variance, and it is a blocking S2 gate.
- **NFR-COST-001** allows roughly ₹8 per analysis, and Phase 5 §22 showed AI inference is the
  variable that decides whether we stay inside the $150/month ceiling.
- **NFR-PERF-004** gives inference 25 s of a 60 s budget; each additional call competes for it.
- **FR-ATS-006** requires every deduction to cite the resume line that caused it. A model that
  returns "your formatting is poor" cites nothing.

There is also a testability argument that turns out to be decisive in practice. Deterministic
logic can be tested in CI without a provider key, without network access, and without cost. The
S1 and S2 quality gates (section F1 ≥ 0.90, σ ≤ 2) are only enforceable in CI if the majority of
the pipeline is deterministic.

The counter-consideration is real: rules require ongoing maintenance and will not cover every
resume format in the world. A pure-LLM approach would generalise better to unusual inputs.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| LLM for everything | Fast to build; generalises well; less rule maintenance | Fails σ ≤ 2; expensive; slow; no evidence citations; untestable in CI without a key |
| Rules only, no LLM | Perfectly deterministic, free, fast | Cannot handle content-quality judgement, rewriting, or question generation at all |
| **Cascade: rules → typography → statistical → LLM on low confidence (chosen)** | Deterministic for the bulk; model for the tail; cost and variance both scale with difficulty, not volume | More components to build and maintain; requires confidence calibration |
| LLM first, rules to validate | Generalises, with a safety net | Pays full model cost on every document; variance already introduced before validation |

## Decision

**Every extraction and classification task is attempted deterministically first. The LLM is invoked
only where deterministic layers report low confidence.**

Applied concretely to section detection:

```
Layer 1 · LEXICON      locale-scoped heading synonyms
Layer 2 · TYPOGRAPHY   font-size z-score, bold, caps, whitespace, DOCX style names
Layer 3 · SEQUENCE     lightweight classifier over line features and ordering
Layer 4 · LLM          only ambiguous spans, with a constrained label set
```

The same principle governs entity extraction (regex → spaCy → LLM for the tail), skill extraction
(taxonomy → alias → fuzzy → LLM for implicit skills), and layout analysis (**pure geometry, no
model at any layer**).

**Layer-4 invocation rate is a monitored metric.** A rising rate means our deterministic layers
have drifted against real-world inputs, and it triggers rule work rather than silently increasing
cost.

## Consequences

### Positive
- The bulk of the pipeline is free, instant, and perfectly reproducible — which is what makes
  σ ≤ 2 achievable at all.
- Quality gates run in CI without a provider key, network access, or spend.
- Cost scales with *document difficulty*, not with volume, so growth increases deterministic work
  far faster than model work.
- Deterministic layers naturally produce spans and coordinates, so evidence citation
  (FR-ATS-006) is a by-product rather than an extra requirement.
- **The entire ⭐ wedge — layout analysis and the Parseability/Structure categories — needs no
  model at all**, which is what makes the degraded mode in Phase 7 §26 possible.
- Layer-4 rate becomes an early-warning signal for input drift.

### Negative / Costs
- Four layers to build and maintain instead of one prompt.
- Rules require ongoing maintenance as resume conventions change and new templates appear.
- Confidence thresholds need calibration, and a badly-set threshold either wastes money (too
  eager) or produces wrong answers (too reluctant).
- Deterministic layers fail *silently and confidently* on inputs they were not designed for — a
  worse failure mode than an LLM's hedging (Risk R-44).

### Follow-up actions required
- **Phase 8:** the golden corpus must include adversarial layouts — two-column, table-based,
  image-only, header-heavy — or R-44 goes undetected.
- **Phase 9:** confidence thresholds for each cascade layer are calibrated against the corpus, not
  guessed.
- **Phase 12:** every deterministic signal emits a confidence value; low confidence surfaces to the
  user as "we're unsure about this" rather than as a false negative.
- **Phase 19:** the deterministic layers have their own test suite that runs with no network access
  at all, proving the independence claim.
- **Phase 20:** Layer-4 invocation rate is dashboarded with an alert threshold.
