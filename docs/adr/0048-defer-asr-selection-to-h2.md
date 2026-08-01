# ADR-0048: Defer ASR model selection to Horizon 2, with criteria recorded now

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 10
- **Deciders:** Project owner + engineering team

## Context

Whisper is listed in the project charter's model comparison and is the natural candidate for speech
recognition in voice mock interviews (F-51, F-52). It is MIT-licensed, so ADR-0023's policy poses no
obstacle, and it has broad language coverage and mature tooling.

But voice interviews are deferred to Horizon 2 (ADR-0006), gated on H1 reaching ≥70% activation and
≥35% seven-day retention. Selecting a speech model now would mean pinning a choice six or more
months before first use, in a field moving at least as fast as text models — and re-doing it anyway
at implementation time.

The temptation is to select it now regardless, since the analysis is easy and the licence is clean.
That is the wrong instinct: the *decision* is easy, but it would be made with none of the
information that actually determines it — measured accented-English accuracy on our users' speech,
real streaming latency inside the NFR-PERF-007 budget, and cost per minute against the 2-credits
pricing in ADR-0007.

There is, however, something that should be decided now: **the criteria**. The dominant one is not
obvious from a general model comparison, and recording it prevents a future selection being made on
the wrong axis.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Select Whisper now | Simple; MIT licence; well understood | Six months premature; would be re-evaluated at implementation anyway; risks anchoring on a choice made without measurement |
| Say nothing about ASR | Honest that it is out of scope | Leaves a charter item unaddressed and the future selection with a blank page |
| **Defer selection, record criteria (chosen)** | Avoids a premature pin; the deciding criterion is captured while it is fresh | Someone must actually read this ADR when H2 arrives |
| Build a voice prototype now to inform the choice | Real data | Contradicts ADR-0006's scope discipline; spends H1 effort on H2 work |

## Decision

**No ASR model is selected at MVP.** Selection happens at Horizon 2, when voice interviews are
actually built, and follows these criteria:

| Criterion | Why it matters for this product |
|---|---|
| **Accented-English word error rate** | ⭐ **The deciding criterion.** We are India-first (ADR-0005). A model with excellent US-English WER and poor Indian-English WER is unusable here regardless of its benchmark position — and general ASR leaderboards will not surface that |
| Streaming support | NFR-PERF-007 allows 2.5 s per interview turn; batch-only transcription cannot meet it |
| Word-level timestamps | Required for the speech analytics that make voice worth building — pace, pause distribution, filler-word rate (F-52). Without timestamps, voice is transcription rather than coaching |
| Self-hosted vs API | Self-hosting for real-time typically needs GPU, which reopens ADR-0018's rejection of ML infrastructure. An API keeps that closed but adds a sub-processor handling voice data |
| Cost per minute | ADR-0007 prices voice at 2 credits/minute; the model must fit inside that |
| Elimination criteria E1–E6 | ADR-0044 applies unchanged. **Voice is more sensitive than text**, not less — it is the user's actual voice |

**Two constraints hold regardless of which model is chosen:**

1. **Text parity is permanent** (ADR-0011). Voice never replaces the text interview path. A speech
   model's failure on a given accent must degrade to text, not to exclusion.
2. **No emotion or affect inference from audio** (ADR-0003's reasoning extends here). Delivery
   analytics measure pace, pauses, fillers, latency, and structure — defined, disclosed acoustic and
   linguistic measures — never inferred emotional state.

Whisper is the presumptive starting candidate on licence and maturity grounds, but the H2 selection
runs the Phase 10 §14 bake-off protocol adapted to ASR, with accented-English WER as the gate.

## Consequences

### Positive
- Avoids pinning a choice six months before use in a fast-moving field.
- Captures the criterion that a future evaluation would most plausibly get wrong — general ASR
  comparisons rank on aggregate WER, and aggregate WER is not our problem.
- Keeps H1 effort on H1, consistent with ADR-0006's scope discipline.
- Records that ADR-0044's eliminations apply to voice data too, which is easy to overlook when the
  provider is framed as "just transcription".
- Reaffirms text parity and the no-emotion-inference boundary before voice work begins, when they
  are cheapest to honour.

### Negative / Costs
- H2 starts with a selection task rather than a settled choice, adding a few days at the point
  where voice is being built.
- Someone must actually consult this ADR at H2 — a deferred decision that nobody reads is the same
  as no decision.
- If a dramatically better option emerges and is missed because we were not tracking the field,
  that is a cost of deferral.

### Follow-up actions required
- **Horizon 2, before voice implementation:** run the §14 bake-off adapted to ASR, with an evaluation
  set of **accented English relevant to our actual users** — not a public benchmark corpus.
- **Phase 8 equivalent for H2:** an audio corpus will be needed, and ADR-0034's synthetic-first
  reasoning should be re-examined for speech, where the trade-offs differ substantially from
  documents.
- **ADR-0037 revisit:** an audio corpus is the change most likely to breach the Git LFS size
  assumption.
- **Phase 3 equivalent for H2:** voice adds a new PII category — the user's actual voice — with its
  own retention, consent, and sub-processor implications. Treat it as new personal data, not as an
  extension of text.
- Supersede this ADR with the actual selection when it is made.
