# ADR-0006: MVP is a text-only golden path shipped in six vertical slices

- **Status:** Accepted
- **Date:** 2026-07-31
- **Phase:** 2
- **Deciders:** Project owner + engineering team

## Context

The project charter lists 18 user-facing capabilities. Phase 1 identified scope explosion
(R-01) as the highest-probability, highest-impact risk in the register, and limited team
capacity (R-11) as a compounding factor. Phase 2 catalogued 58 discrete features.

Two structural questions had to be settled:

1. **How much goes in the MVP?** Attempting all 58 produces an architecture sized for a
   product that does not exist yet and a timeline no part-time team can hold.
2. **How is the work sliced?** Horizontal layering (build all the backend, then all the
   frontend) means no user-visible value until the very end — which removes the feedback
   loop precisely when it is most valuable, and is demotivating for a small team.

A third question was whether voice interviews belong in the MVP. Voice is the most
impressive capability in the charter, and also XL effort requiring streaming transport,
ASR, TTS, and realtime session state.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Ship all 58 features | Complete vision | Certain schedule failure; complexity compounds; R-01 realised |
| Thin horizontal MVP (all features, shallow) | Demos broadly | 60% of every feature = 100% of no journey; nothing is good enough to retain |
| **Complete thin journey in 6 vertical slices (chosen)** | Every slice ships value; feedback from week 2; a coherent product exists at every point | Requires discipline to refuse adjacent features |
| Voice in MVP | Most impressive capability | XL effort on an unvalidated retention hypothesis |
| **Text interview in MVP, voice in H2 (chosen)** | Tests the interview loop at ~10% of the cost | Less impressive demo |

## Decision

**MVP = 41 features delivering one complete golden path, text-only, in six vertical slices.**

The golden path: sign up → upload → parse → **parse-fidelity report** → ATS score with
evidence → paste JD → match and gaps → grounded suggestions → gap-targeted questions → text
mock interview → session report → re-upload v2 → observe score movement.

Slices, each independently shippable, each with a hard exit gate:

| Slice | Content | Gate |
|---|---|---|
| S0 | Walking skeleton, **zero AI** | Deploys; queue and worker run end to end |
| S1 | Parsing | Section-detection F1 ≥ 0.90 on 50 labelled resumes |
| S2 | Parse-fidelity + ATS score | Reproducibility σ ≤ 2; 5 users say "I didn't know that" |
| S3 | JD match + grounded suggestions | Zero fabricated facts on the eval set (blocking) |
| S4 | Interview engine | A full session completes coherently |
| S5 | Loop, limits, privacy | Activation ≥ 70% in closed beta |

Exactly **one delighter** ships in MVP: the parse-fidelity report. Voice, payments, the
Chrome extension, the coding module, institution dashboards, and multilingual support are
deferred.

**Contingency:** if capacity runs short, S0–S2 ships alone as "the ATS diagnosis tool" — a
coherent, marketable product. The slice design exists specifically to make this retreat
available without discarding work.

## Consequences

### Positive
- Scope is bounded and the boundary is written down, so cuts are not re-litigated weekly.
- User feedback arrives from week 2, not week 12.
- S0 de-risks the async pipeline — historically the thing that surprises AI products — before
  any AI is involved.
- S1's F1 gate prevents four slices being built on unreliable parsing (R-06).
- Text-first treats the interview loop as a cheap experiment rather than an expensive bet.

### Negative / Costs
- Voice absence weakens the demo against competitors that advertise it.
- 41 features is still ambitious part-time (R-19); the contingency exists because of this.
- Vertical slicing means some components are revisited across slices rather than built once.

### Follow-up actions required
- **Phase 3:** write requirements only for MVP features; deferred features get no NFRs yet.
- **Phase 4:** architecture sized for the MVP, with slice boundaries as future service seams.
- **Phase 7:** AI design covers S1–S4 modules only.
- **Phase 19:** the S1 F1 gate and the S3 zero-fabrication gate become CI gates, not manual
  checks. A gate that is not automated is a wish.
