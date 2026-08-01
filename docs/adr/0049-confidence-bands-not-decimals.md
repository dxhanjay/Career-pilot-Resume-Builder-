# ADR-0049: Report confidence as bands with reasons, not as decimals

- **Status:** Accepted
- **Date:** 2026-08-02
- **Phase:** 11
- **Deciders:** Project owner + engineering team

## Context

FR-ATS-010 requires the system to display a confidence indicator alongside every score. Two
questions follow: where does the value come from, and how is it presented?

**On the source.** The easy implementation is to ask the model for a confidence value alongside its
judgement. That number is **not calibrated** — a self-reported 0.8 does not mean the judgement is
correct 80% of the time, and there is no reason to expect it would be. Displaying it as though it
were is a false claim wearing the costume of rigour.

**On the presentation.** Even with a properly derived value, a displayed `0.73` makes an implicit
promise: that findings at 0.73 confidence are right roughly 73% of the time. Establishing that
requires calibration data — a labelled set where predicted confidence is compared against actual
correctness. We do not have it. NFR-AI-005's expert panel (dataset D11) is the mechanism that could
produce it, and Phase 9 §21 records that the panel may not be available at all.

This matters more here than in most products. Phase 1's third USP pillar is *"evidence, not vibes"*,
and ADR-0004 commits us to never presenting generated content as fact. A confidence number we cannot
justify is the same category of error applied to our own output: manufactured precision presented as
measurement.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Model self-reported confidence | Trivial to implement | **Uncalibrated**; varies run to run, working against σ ≤ 2; no basis for the number |
| Derived numeric confidence (0.00–1.00) | Looks rigorous; sortable; familiar | Implies a calibration we have not demonstrated — the precision is fictional |
| **Derived confidence, reported as bands + reason (chosen)** | Claims only what we can defend; the reason is more actionable than any number | Less precise-looking; some users prefer a number |
| No confidence indicator | Nothing to defend | Fails FR-ATS-010; and users deserve to know when a parse went badly |

## Decision

**Confidence is computed deterministically from measurable signals and reported as High / Medium /
Low, always accompanied by the reason.**

Input signals, all of which we actually measure:

| Signal | Direction | Rationale |
|---|---|---|
| Mean per-field parse confidence (FR-PARSE-006) | ↑ | Poor extraction undermines everything downstream |
| Unclassified content ratio | ↓ | Content we could not place may contain what we missed |
| Share of score contributed by AI-judged rules | ↓ | Deterministic findings are more reliable than judged ones |
| OCR used | ↓ | OCR output is materially noisier than a native text layer |
| Locale mismatch (document vs rubric) | ↓ | A rubric applied outside its locale scope is less applicable |
| Document length outside the corpus range | ↓ | Outside the distribution we validated against |

The reason is displayed with the band, because the reason is the actionable part:

> **Confidence: Medium** — we couldn't place 14% of your resume text, so some findings may be
> incomplete. This is usually caused by a layout our parser struggled with.

**Promotion to a numeric confidence requires calibration** against the D11 expert panel —
demonstrating that findings in each band are correct at approximately the implied rate. Until that
exists, bands stay. If D11 never materialises, bands stay permanently, and that is an acceptable
outcome rather than a deficiency to work around.

## Consequences

### Positive
- We make only claims we can defend, consistent with the honesty position the product's
  differentiation rests on.
- The **reason** is more useful to the user than a number: "we couldn't place 14% of your text" tells
  them something actionable; "0.73" does not.
- Confidence is deterministic and reproducible, so it does not undermine NFR-AI-003's σ ≤ 2 gate the
  way a model-generated value would.
- Confidence composition is auditable — each contributing signal is stored and inspectable.
- Sets the correct precedent for the product: no manufactured precision anywhere.

### Negative / Costs
- Three bands are coarse; two findings at the edges of a band are presented identically.
- Some users — and some competitors' marketing — prefer a number, and bands can read as less
  sophisticated.
- Bands cannot be sorted or thresholded as finely, which slightly limits internal analytics.
- The composition weights are themselves judgement calls, and until calibration exists we cannot
  demonstrate they are the right ones. This is a real limitation, mitigated only by the weights being
  visible and revisable.

### Follow-up actions required
- **Phase 12:** confidence composition is a pure function in the domain layer, unit-tested, with each
  input signal persisted alongside the analysis.
- **Phase 13:** the band and its reason are displayed together — the band alone is not permitted, since
  the reason carries the value.
- **Phase 19:** a test asserts confidence is deterministic for identical input, and that a degraded
  parse produces a lower band.
- **If D11 becomes available:** run calibration; a numeric confidence may then be introduced via a
  superseding ADR, with the calibration evidence recorded.
- **Phase 20:** band distribution is monitored — a sudden shift toward Low indicates parser or input
  drift before users report it.
