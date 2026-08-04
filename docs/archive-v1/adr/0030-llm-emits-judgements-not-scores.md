# ADR-0030: The LLM emits categorical judgements with spans, never scores

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 7
- **Deciders:** Project owner + engineering team

## Context

NFR-AI-003 requires that the ATS score be reproducible: standard deviation ≤ 2 points across five
runs on identical input. It is a **blocking gate for slice S2**, and it is the single requirement
that most constrains how AI is used in this product.

The requirement is not arbitrary. Phase 1 established that the ⭐ progress feature — "your score
improved 34 points in three weeks" — is our retention mechanism, and Phase 2 made
`resume_v2_uploaded` with a score delta our truest early behavioural signal. **If scores are not
reproducible, the delta measures noise, and the feature actively lies to users.** A user who
re-uploads an unchanged resume and sees a different score has learned that the product is
unreliable, and that is not recoverable.

The obvious implementation — asking a model to "rate this resume's content quality from 0 to 100"
— cannot meet this. Free-form numeric judgements from language models vary across runs even at
temperature 0, because sampling is not the only source of non-determinism: provider-side batching,
hardware differences, and model updates all contribute. Worse, a returned number carries no
evidence, so FR-ATS-006's requirement that every deduction cite its source line becomes
unsatisfiable.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Model returns the score directly | Simplest; one call | **Cannot meet σ ≤ 2**; no evidence; unexplainable; a model update silently shifts every score |
| Model returns a score, we average N runs | Reduces variance | N× the cost and latency; still no evidence; variance reduced, not eliminated |
| **Model returns categorical judgements + spans; arithmetic scores (chosen)** | Stable across runs; evidence by construction; rubric is auditable and diffable | More prompt engineering; rules must be designed as decidable questions |
| No LLM at all in scoring | Perfectly deterministic | Content quality, grammar, and vagueness genuinely need linguistic judgement |

## Decision

**The model is never asked for a number. It is asked decidable questions and returns categorical
or extractive answers with source spans. A pure function converts those into points.**

| ❌ Not this | ✅ This |
|---|---|
| "Rate content quality 0–100" | "For each bullet, does it begin with an action verb? `true`/`false`" |
| "How well quantified is this?" | "Return the spans of bullets containing a quantified outcome" |
| "Is the grammar good?" | "Return grammar issues as `{span, type, suggestion}`" |
| "How relevant was this answer?" | "Relevance 1–5 against these anchors, citing the span that justifies it" |

Categorical and extractive outputs are dramatically more stable than free-form numeric ones, and
they are the only kind that can carry evidence.

**Five reinforcing controls:**

1. Temperature 0 and fixed decoding parameters; provider seed where supported.
2. Schema-constrained output — no free text available to vary.
3. Content-hash caching including `rubric_version` and `prompt_version`: an unchanged resume
   returns the stored analysis without reaching the model at all.
4. Rubric arithmetic is a pure function of the judgement set, contributing zero variance.
5. A determinism suite runs five times per golden-corpus resume in CI and fails the build on
   σ > 2.

**The target is not negotiable.** If a specific rule proves unstable in the suite, that rule is
redesigned into a more decidable question or demoted to a deterministic detector — the threshold is
never relaxed to accommodate it.

## Consequences

### Positive
- σ ≤ 2 becomes achievable, so the progress feature measures real change rather than noise.
- **Every deduction cites a span by construction**, satisfying FR-ATS-006 without extra work.
- The rubric is human-readable, diff-reviewable, and publishable to users — which is what makes
  FR-ATS-004's "published rubric" honest.
- Scores are auditable: a disputed score can be traced to specific judgements and specific rules.
- A model change alters *judgements*, which the eval suite detects, rather than silently shifting
  scores.
- Categorical outputs are shorter, so token cost and latency both fall.
- The same discipline transfers directly to interview answer evaluation (Phase 7 §21).

### Negative / Costs
- Every rule must be expressible as a decidable question — some genuinely holistic judgements
  ("does this resume feel senior?") resist this and are simply not implemented.
- More prompt engineering per rule than one general-purpose prompt.
- Possibly more calls, mitigated by batching independent judgements into a single request.
- The rubric's point values become a design responsibility we own, rather than something delegated
  to the model. This is more work, and it is also the correct place for that responsibility to sit.

### Follow-up actions required
- **Phase 8:** the golden corpus carries expected judgement sets, not just expected scores, so
  regressions are attributable to a specific rule.
- **Phase 9:** rubric point values calibrated against expert human ratings (NFR-AI-005, κ ≥ 0.6).
- **Phase 10:** model selection weights *output stability on categorical tasks*, not general
  benchmark performance.
- **Phase 19:** the determinism suite is a blocking S2 CI gate; per-rule variance is reported so an
  unstable rule is identifiable rather than merely failing the aggregate.
- **Phase 20:** score variance on repeat analyses of identical content is a production SLI — drift
  here means a provider-side model change we were not told about.
