# ADR-0047: Reject LayoutLM; layout understanding stays geometric

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 10
- **Deciders:** Project owner + engineering team

## Context

LayoutLM is the obvious candidate for the most important component in this product. It is a family
of multimodal transformers that jointly model text, spatial layout, and page image — designed
precisely for form and document understanding, which is what our ⭐ parse-fidelity wedge depends on.
Any survey of document-AI models puts it near the top.

Two independent findings rule it out.

**Licensing.** LayoutLMv2 and LayoutLMv3 are released under **CC BY-NC-SA 4.0** `[VERIFY BEFORE ANY
USE]` — a **non-commercial** licence. This product is a commercial SaaS platform (ADR-0005), which
puts those weights outside permitted use. LayoutLM v1 carries a more permissive licence but is
materially weaker, and adopting a weaker model to escape a licence is a poor trade. ADR-0023
established permissive licensing as a hard requirement with CI enforcement; that policy was written
for linked code, and the same reasoning applies to model weights we would ship or serve.

This is the **second such finding in the project**. ADR-0023 rejected PyMuPDF — the fastest and most
accurate PDF layout library available — on AGPL grounds. In both cases the technically strongest
tool for our most critical component carried a licence we cannot use, and in both cases the finding
was cheap to make at selection time and would have been expensive after a module was built around
the API.

**Architectural fit.** The more interesting finding is that we were never going to need it. Phase 7
§8 established that our layout signals are **pure geometry over word bounding boxes**: column count
by vertical projection profile, reading order by column-aware sorting, tables by rect density,
text-in-images by region overlap, headers and footers by y-band repetition across pages. Those seven
signals *are* the wedge, and they are deterministic, instant, free, perfectly reproducible, and
testable in CI without a model.

Introducing LayoutLM would replace a deterministic method with a probabilistic one **in the one part
of the system where determinism is the product**. It would add a model dependency, GPU or heavier
CPU inference, latency inside a budget where inference already consumes 42%, and variance against
NFR-AI-003's σ ≤ 2 gate — in exchange for capability the geometric approach already provides.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| LayoutLMv3 | State-of-the-art document understanding | **Non-commercial licence** — ineligible; probabilistic where we need determinism; inference cost and latency |
| LayoutLM v1 (permissive) | Licence-compatible | Materially weaker; still probabilistic; still adds a model dependency for capability geometry already provides |
| Commercial document-AI API | Turnkey; no licence issue | Per-page cost on our highest-volume operation; another sub-processor in the PII path; still probabilistic |
| **Geometry only (chosen)** | Deterministic, free, instant, CI-testable, already designed and specified | Heuristics can fail silently on unusual templates (R-44) |

## Decision

**LayoutLM is not used, in any version. Layout understanding remains purely geometric**, per Phase 7
§8.

The geometric approach stays the answer even if a permissively-licensed equivalent appears, because
the licensing objection is the lesser of the two — determinism in the ATS-hostility signals is what
makes the ⭐ wedge reproducible, explainable, and free, and what lets Phase 7 §26's degraded mode
deliver the wedge during a total AI outage.

**Escalation ladder if geometry proves insufficient** (Risk R-44), in order:

1. **Improve the geometry** — better gutter detection, better rect clustering, more layout truth
   cases in dataset D2. Cheap, deterministic, and it improves the CI gate at the same time.
2. **Evaluate permissively-licensed document-layout models**, measured on D2's `layout_truth`
   against the geometric baseline — a model must beat geometry on our own data, not on a paper's
   benchmark.
3. **License a commercial model or API** as a costed, deliberate decision — the same third rung as
   ADR-0023's Artifex option.

At no point does a non-commercially-licensed model enter the ladder.

## Consequences

### Positive
- No licence exposure on the component the product's differentiation rests on.
- The wedge stays **deterministic**: reproducible signals, no variance contributed to the σ ≤ 2
  gate, and testable in CI with no API key or network access.
- Zero marginal cost and millisecond latency on every analysis, in a budget where inference already
  dominates.
- Phase 7 §26's degraded mode survives — the parse-fidelity report and 60% of the rubric keep
  working during a complete AI provider outage, which would be impossible if layout depended on a
  model.
- Establishes the pattern: **check the licence before evaluating the capability.** Two findings in
  two phases suggests this will not be the last.

### Negative / Costs
- Geometric heuristics **fail silently and confidently** on templates they were not designed for
  (R-44) — a worse failure mode than a model's hedging, and the reason dataset D2 exists with 16
  adversarial templates.
- We forgo genuine capability on genuinely hard documents: heavily-designed CVs, infographic
  resumes, unusual multi-column arrangements.
- Heuristics need ongoing maintenance as resume design conventions change; a model would generalise
  more gracefully.

### Follow-up actions required
- **Phase 8 / slice S1:** dataset D2's 16 adversarial layout templates are the mechanical defence
  against R-44 — without them, a heuristic gap is undetectable.
- **Phase 12:** every geometric signal reports a confidence value; low confidence surfaces to the
  user as "we're unsure about this" rather than as a false negative.
- **Phase 14:** the licence-scanning CI gate (ADR-0023) is extended to cover model weights and
  model-serving dependencies, not only Python and JavaScript packages.
- **Phase 20:** low-confidence layout signal rate is monitored; a rising rate means real-world
  templates have drifted from the corpus and D2 needs new cases.
- Revisit only via the escalation ladder above, and never past rung 2 without a costed decision.
