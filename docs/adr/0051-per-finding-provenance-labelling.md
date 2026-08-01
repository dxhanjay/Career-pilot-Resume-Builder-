# ADR-0051: Label every finding with how it was produced

- **Status:** Accepted
- **Date:** 2026-08-02
- **Phase:** 11
- **Deciders:** Project owner + engineering team

## Context

Phase 7 established that this system is a mixture: roughly 70% of the analysis pipeline's value
comes from deterministic geometry and rules, and about 30% from contained model judgements. A single
report therefore contains findings of two very different epistemic kinds:

- *"A two-column layout was detected"* — measured from word bounding-box geometry. Reproducible,
  verifiable, and not a judgement at all.
- *"4 of 14 bullets lack an action verb"* — a model judgement, guard-verified and span-cited, but
  still an interpretation.

Presenting these identically implies they warrant equal confidence. They do not, and the difference
is not marginal. A user acting on a measured layout finding is acting on fact; a user acting on a
content judgement is acting on an interpretation that could reasonably differ.

Two further considerations push the same way.

**Transparency obligations.** Phase 3 §7.1 noted that even under ADR-0002's candidate-side scope,
the EU AI Act's transparency requirement — users must know they are interacting with AI — applies.
The usual implementation is a blanket "AI-powered" banner, which tells the user nothing useful about
*which* parts involved AI.

**The wedge is measured, not judged.** Phase 1 established that our differentiator is showing what
the machine actually saw. The highest-impact findings — column detection, text-in-images,
header/footer content — are all deterministic. Presenting them undifferentiated from model output
buries exactly the property that distinguishes us from a chatbot.

The information required already exists: Phase 7's rubric schema carries a `detector: deterministic
| llm` field on every rule, for reasons of cost and test tiering.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| No labelling | Simplest report | Implies equal confidence across unequal findings; blanket AI disclosure is uninformative; buries the wedge |
| Blanket "AI-powered" banner | Satisfies disclosure minimally | Tells the user nothing about which findings involved AI; arguably misleading in the other direction, implying the geometry is model output |
| **Per-finding provenance label (chosen)** | Accurate per finding; free (data already exists); makes the wedge legible | Three concepts for users to absorb; some risk of reading the labels as a quality hierarchy |
| Confidence per finding instead | More granular | Confidence is a different axis (ADR-0049) and would need per-rule calibration we do not have |

## Decision

**Every finding carries a provenance label describing how it was produced.**

| Label | Meaning | Example |
|---|---|---|
| 📐 **Measured** | Deterministic geometry over the document. No model involved | "Two-column layout detected" |
| 📋 **Rule** | Deterministic check against the rubric | "No Experience section found" |
| 🤖 **AI judgement** | A model returned a categorical judgement, guard-verified, span-cited | "3 of 12 bullets lack an action verb" |

The label is derived from the rubric rule's existing `detector` field and stored on the finding, so
it is fixed at analysis time rather than recomputed — a rubric change cannot retroactively relabel
historical findings.

Accompanying plain-language explanation, shown once per report:

> Findings marked 🤖 come from an AI judgement. We show you the exact text it was looking at and
> what it concluded, and we verify that text exists in your resume — but we can't show you *why* it
> reached that conclusion. Findings marked 📐 and 📋 are measured or rule-based: for those, the
> reason is exactly the rule shown.

This is the transparency disclosure — precise rather than blanket.

## Consequences

### Positive
- Users calibrate trust **per finding** rather than globally, which is the accurate way to read a
  mixed-provenance report.
- Satisfies the AI-transparency obligation with more precision than a banner, and in a form that is
  genuinely useful rather than merely compliant.
- ⭐ **Makes the wedge legible.** A report whose highest-impact findings are labelled 📐 Measured
  communicates something a competitor's opaque score cannot — this is not a chatbot's opinion.
- Costs essentially nothing: one enum column, populated from data the rubric already carries.
- Supports the honest position in Phase 11 §8.2 — labelling *which* findings we cannot fully explain
  is what makes that admission concrete rather than a disclaimer.
- Gives us a product metric worth watching: the share of score contributed by 🤖 findings is both a
  confidence input (ADR-0049) and a signal of how much of the product is genuinely deterministic.

### Negative / Costs
- Three concepts to communicate in a report already carrying scores, categories, and spans.
- Users may read the labels as a quality ranking rather than a provenance description — "AI
  judgement" could be heard as "less trustworthy" in a way that undersells guard-verified findings.
- The 📐/📋 distinction is subtle; some users will not perceive a difference between geometry and a
  rule check, which is acceptable since both are deterministic.
- Fixing the label at analysis time means a rule that later changes detector type produces
  inconsistent labels across a user's history — correct, but potentially confusing.

### Follow-up actions required
- **Phase 6 addition:** `analysis.findings` carries a `provenance` column with a CHECK constraint on
  the three values, populated at analysis time from the rubric rule.
- **Phase 12:** provenance is derived from the rubric's `detector` field by the scoring function —
  never set by hand at a call site, so it cannot drift from the rule.
- **Phase 13:** the label appears on every finding with a legend; the plain-language explanation
  appears once per report, not per finding.
- **Phase 13:** user testing should check the labels are read as provenance rather than as a quality
  ranking; adjust wording if not.
- **Phase 20:** share of score from 🤖 findings is monitored — a rise means the deterministic layers
  are covering less than they did, which is an early signal of input drift.
