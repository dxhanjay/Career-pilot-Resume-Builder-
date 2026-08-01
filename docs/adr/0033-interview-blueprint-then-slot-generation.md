# ADR-0033: Build the interview blueprint deterministically, then generate each slot

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 7
- **Deciders:** Project owner + engineering team

## Context

The interview module must satisfy several requirements at once:

- FR-INT-001: questions grounded in the resume and, when present, the JD
- FR-INT-002: behavioural, technical, and HR types all represented
- FR-INT-003: difficulty matched to the user's experience level
- **FR-INT-004: gap-targeted questions when a gap report exists** — this is the closed loop that
  Phase 1 identified as the product's structural differentiator
- FR-INT-005: 5 questions (Free) or 15 (Pro)

The obvious implementation is one call: *"Here is the resume, the JD, and the gaps. Generate eight
interview questions."*

That approach cannot guarantee any of the above. Type coverage becomes luck — ask for eight
questions and you may get seven technical and one behavioural. Difficulty drifts across the set.
Most importantly, **gap targeting is unenforceable**: the model may cover two of four gaps, or
none, and there is no way to detect that without re-parsing its own output and reasoning about it.

There are two further practical problems. A single large generation is a single point of failure —
if it returns malformed output, the whole session fails. And it caches poorly: any change to any
input invalidates the entire set.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| One call for all questions | Simplest; coherent set; one round trip | No coverage guarantee; difficulty drifts; **gap targeting unenforceable**; one failure loses the session; poor caching |
| One call, then validate and retry | Adds a safety net | Retries are expensive; validation of "is this behavioural?" is itself a judgement; still no binding to specific gaps |
| **Deterministic blueprint, then per-slot generation (chosen)** | Coverage, difficulty, and gap binding are structural guarantees; failures isolate; each slot caches independently | More calls; risk of a less coherent set; blueprint rules need designing |
| Fixed question bank, selected by rules | Fully deterministic; free | Generic — the exact commodity a free tool already provides; not grounded in *this* resume |

## Decision

**The blueprint is constructed by deterministic rules before any generation happens. Each slot is
then generated independently against its own binding.**

```
Blueprint · 8-question mid-level session, JD present, 4 ranked gaps:

  slot 1  behavioural   warm-up            ← bound to a resume project
  slot 2  technical     gap #1             ← highest weight × (1 − coverage)
  slot 3  resume-deep   most recent role
  slot 4  technical     gap #2
  slot 5  behavioural   conflict / failure
  slot 6  technical     gap #3
  slot 7  resume-deep   a claimed skill, probed
  slot 8  hr            motivation / fit
```

Each slot carries a type, a difficulty, and an explicit binding — a ranked gap from ADR-0031's
coverage scoring, a resume section, or a JD requirement. Generation receives one slot's binding and
returns one question.

**Difficulty is defined, not left to interpretation:**

| Level | Scope | Ambiguity | Follow-up depth |
|---|---|---|---|
| Fresher | Single concept, coursework/project scale | Fully specified | 0–1 |
| Mid | Multi-component, production scale | Some ambiguity to resolve | 1–2 |
| Senior | System-level, trade-offs, org impact | Deliberately under-specified | 2–3 |

Slot counts by session length are rule-driven, with technical slots always bound to the
highest-ranked uncovered gaps first — so **the closed loop is a structural property of the
blueprint, not an instruction we hope the model follows.**

## Consequences

### Positive
- **FR-INT-004 is guaranteed rather than hoped for.** Gap-targeted questions exist because slots
  are bound to gaps before generation, and the binding is data we can assert on in tests.
- Type coverage and difficulty are deterministic and verifiable without inspecting question text.
- Each slot generation is small, cheap, independently cacheable, and parallelisable — improving
  both cost and the interview-turn latency budget (NFR-PERF-007).
- A failed slot loses one question, not the session; regeneration is scoped.
- The blueprint is inspectable, so "why did it ask me that?" has a concrete answer — the same
  evidence discipline as the ATS findings.
- Session length changes (5 Free / 15 Pro) become a blueprint-rule change, not a prompt change.

### Negative / Costs
- More model calls than a single generation — offset by smaller prompts, parallelism, and
  per-slot caching.
- The set may be **less coherent**, since each question is generated without full awareness of the
  others. Mitigated by passing prior question *summaries* as context and by a duplicate check
  across the set.
- Blueprint rules are logic we own and must maintain as the product learns what makes a good
  session.
- A poorly-designed blueprint produces a poorly-structured interview even if every individual
  question is excellent — the quality responsibility moves to us, which is more work and the right
  place for it.

### Follow-up actions required
- **Phase 9:** blueprint rules validated against real sessions — measure abandonment by slot
  position to find where interviews lose people.
- **Phase 12:** the blueprint is persisted with the session (`interview.questions.generation_basis`
  records the binding), so the loop is auditable after the fact.
- **Phase 12:** a duplicate-similarity check across generated slots, since independent generation
  can repeat.
- **Phase 19:** a test asserts that when a gap report exists, technical slots bind to the
  highest-ranked gaps — FR-INT-004 verified structurally rather than by reading question text.
- **Phase 20:** per-slot abandonment is a monitored product metric; a spike at a slot position is a
  blueprint problem, not a generation problem.
