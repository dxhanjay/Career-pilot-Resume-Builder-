# ADR-0044: Eliminate models on contractual criteria before evaluating quality

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 10
- **Deciders:** Project owner + engineering team

## Context

Model selection is normally framed as an optimisation: compare candidates on quality, latency, and
price, and pick the best trade-off. That framing is wrong for this product, because several
candidates are not eligible at all — and evaluating an ineligible model wastes effort and, worse,
creates pressure to reconsider a commitment under cost pressure once a cheap option scores well.

Three constraints established in earlier phases are non-negotiable:

- **ADR-0012** commits us to never using customer content for model training without explicit
  opt-in. A provider that trains on API inputs makes that promise false. This is the project's
  clearest ethical commitment and the one its positioning depends on.
- **Phase 3 §7** requires a DPA, a documented transfer safeguard for India→US processing, and
  provider retention no longer than our own. Without those, our privacy notice cannot be accurate.
- **FR-IMP-007 and ADR-0030** depend on schema-constrained structured output. A provider without it
  cannot implement Phase 7's design at all — the guard's first check has nothing to validate
  against, and categorical judgement with span citation is unreliable.

There is also a weighting problem specific to how Phase 7 uses models. The design deliberately
narrows each call to a decidable question, so raw reasoning capability matters far less than
returning valid, stable, cited JSON. A model that tops a public reasoning benchmark and returns
malformed JSON 3% of the time is strictly worse for us than a weaker model that never does.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Score everything on one weighted matrix | Single comparable number | Lets a disqualifying legal failure be outweighed by price; invites re-litigating ADR-0012 when a cheap option scores well |
| Evaluate quality first, check compliance later | Fast start | Discovers blockers after building; sunk-cost pressure to accept a weaker guarantee |
| Choose on public benchmarks | Cheap; widely available | Measures general reasoning, not schema reliability or stability — the properties we actually depend on |
| **Hard eliminations, then weighted criteria (chosen)** | Legal and design constraints are absolute; evaluation effort spent only on eligible candidates | Rules out some otherwise-attractive options outright |

## Decision

**Model selection is two-stage. Stage one is elimination on six criteria, evaluated from written
contractual terms. A candidate failing any one is removed before any quality measurement.**

| # | Elimination criterion | Origin |
|---|---|---|
| E1 | Contractual guarantee of **no training on our data** | ADR-0012 |
| E2 | DPA available; documented international-transfer safeguard | NFR-CMPL-001/003 |
| E3 | Schema-constrained structured output | FR-IMP-007, ADR-0030 |
| E4 | Documented retention ≤ 30 days, deletable | Phase 3 §7.4 |
| E5 | Commercially licensed for SaaS use (self-hosted weights) | ADR-0023 |
| E6 | Published rate limits and a support path | NFR-CAP-001 |

Stage two scores surviving candidates on weighted criteria, deliberately ordered so that the
properties Phase 7's design depends on outrank general capability:

**Structured-output reliability 25% · output stability 20% · cost 20% · latency 15% ·
prompt-caching support 10% · quality on our own evals 10%.**

Quality is measured on datasets D1–D11 (Phase 8), **never on public benchmarks** — our text is dense
resume and job-description jargon that general benchmarks do not represent.

**E1 is not tradeable.** A provider that is cheaper, faster, and more accurate but trains on API
inputs is ineligible. This ADR exists so that decision is made once, in the calm, rather than
re-argued under budget pressure.

## Consequences

### Positive
- The ADR-0012 privacy commitment cannot be quietly eroded by a compelling price.
- Evaluation effort goes only to candidates that could actually be used.
- Weighting reflects what the architecture depends on — schema reliability and stability over raw
  capability — rather than what is easiest to compare.
- Compliance blockers surface before a module is built on a provider's API.
- Eliminations are checkable from contracts, so the decision is auditable rather than a judgement
  call.

### Negative / Costs
- Some strong candidates are excluded on terms rather than merit, and that will occasionally be
  frustrating.
- Verifying E1, E2, and E4 requires reading commercial terms rather than marketing pages — slower,
  and the terms change.
- Low weighting on general quality could be wrong if a future feature genuinely needs frontier
  reasoning; that would warrant revisiting the weights, not the eliminations.

### Follow-up actions required
- **Phase 10 bake-off:** eliminations are stage zero; nothing proceeds to measurement without them.
- **Before any provider selection:** E1, E2, and E4 confirmed from current commercial terms and
  recorded with a date — including for the incumbent, since terms change.
- **Phase 12:** the sub-processor list (FR-PRIV-007) records each provider with its verified terms.
- **On any provider change:** re-run the eliminations. A model swap is not only a quality decision.
- **Re-verify annually**, or whenever a provider announces terms changes — an expired verification
  is not a verification.
