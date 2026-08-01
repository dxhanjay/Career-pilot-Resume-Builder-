# ADR-0039: No fine-tuning at MVP

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 9
- **Deciders:** Project owner + engineering team

## Context

The project charter lists fine-tuning and transfer learning as pipeline topics, and it is a
reasonable expectation for a product described as AI-powered. Several tasks look like plausible
candidates: section classification, content-quality judgement, and rewrite generation.

Four constraints make it the wrong move now.

**Data.** ADR-0012 forbids training on user content without explicit opt-in, and at MVP no such
corpus exists. The available alternative is synthetic data — but a model fine-tuned on synthetic
resumes would learn *our generator's conventions*, which Phase 8 already flags as a homogeneity risk
(R-52). Training on it would convert a corpus limitation into a model limitation, and the D12
validation set is too small to reliably detect that.

**Prompting is not exhausted.** Phase 7's design deliberately narrows each model call to a decidable
question with a constrained schema (ADR-0030). We have not yet measured what that achieves. Fine-
tuning before knowing what prompting delivers is optimising an unmeasured baseline.

**Infrastructure.** A fine-tuned model is an artefact to version, host, monitor, evaluate for drift,
and periodically retrain. ADR-0018 rejected self-hosted ML infrastructure for a 1–2 person part-time
team, and nothing has changed.

**Portability.** ADR-0015's provider port exists so a provider change is a configuration change —
the mitigation for R-05, vendor lock-in. A fine-tuned model on one provider is the strongest
possible form of that lock-in.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Fine-tune now on synthetic data | Might improve task accuracy | Learns generator conventions, not reality; no way to detect that at our validation-set size |
| Fine-tune on user data | Real distribution | **Forbidden without opt-in** (ADR-0012); no opted-in corpus exists |
| **Prompting only at MVP, with reopening conditions (chosen)** | No infrastructure; provider-portable; forces us to measure prompting first | Leaves potential accuracy on the table; may look under-ambitious |
| Fine-tune a small open model, self-hosted | Full control; no per-token cost | Hosting, GPU cost, monitoring — precisely the infrastructure ADR-0018 rejected |

## Decision

**No fine-tuning at MVP.** Domain adaptation is achieved through rules, taxonomies, prompt design,
and calibration — all of which are cheaper, deterministic, inspectable, and immediately correctable
when wrong.

**Reopening requires all three conditions to hold, with evidence:**

1. **Prompting has plateaued** below a quality gate after genuine documented iteration — visible in
   the experiment log (Phase 9 §14), not asserted.
2. **≥ 5,000 opted-in, de-identified, labelled examples** exist for the specific task, obtained
   under ADR-0012's consent mechanism.
3. **A measured cost or latency model shows fine-tuning wins** — including hosting, monitoring, and
   retraining, not just inference price.

**The most likely first candidate is section classification**, not generation. It is bounded, has
unambiguous labels, and a small task-specific model could replace the Layer-4 LLM fallback entirely
— removing cost, latency, and variance at once. By contrast, fine-tuning rewrite generation would
risk eroding the grounding behaviour that ADR-0032's guard depends on, replacing a controllable
prompt with opaque learned behaviour.

Transfer learning **is** used extensively — pretrained spaCy NER, a pretrained sentence encoder, and
the LLM itself. Using pretrained models without weight updates is the version of transfer learning
that fits this project.

## Consequences

### Positive
- No training infrastructure, model hosting, drift monitoring, or retraining cadence to operate.
- Provider portability preserved — ADR-0015's port keeps its value.
- Avoids baking synthetic-generator conventions into model weights, where they would be invisible
  and hard to undo.
- Forces the discipline of measuring prompting properly, which produces the experiment log that
  would justify fine-tuning later.
- Domain adaptation via taxonomies and rules is inspectable and fixable in minutes; a fine-tuned
  model's mistake takes a retraining cycle to correct.

### Negative / Costs
- Accuracy on some tasks is likely lower than a well-fine-tuned model would achieve.
- Per-token inference costs persist where a small local model might be cheaper at volume.
- Latency on the Layer-4 fallback stays higher than a local classifier would be.
- To some audiences, "no fine-tuning" reads as less sophisticated — a perception cost we accept, and
  one this ADR exists to answer.

### Follow-up actions required
- **Phase 10:** provider selection weights **stability on categorical tasks and structured-output
  reliability** over general benchmark scores, since prompting is our only lever.
- **Phase 9 experiment log:** records prompting plateaus explicitly, so condition 1 is evidenced
  rather than claimed.
- **Phase 12:** if ADR-0012's training opt-in is offered, consented examples accumulate with task
  labels from the start — otherwise condition 2 can never be met.
- **Phase 15:** MLOps design covers what *would* be needed for fine-tuning without building it, so
  reopening is a project rather than a research exercise.
- Reopening requires a superseding ADR citing which conditions are met and the evidence for each.
