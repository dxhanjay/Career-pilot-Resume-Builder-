# ADR-0034: Synthetic-first evaluation corpus; no scraped or unverified resume datasets

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 8
- **Deciders:** Project owner + engineering team

## Context

Six blocking quality gates depend on an evaluation corpus that does not yet exist. It has been the
project's longest-standing blocker, holding up slice S1's section-detection gate (NFR-AI-001) and
several risks (R-21, R-32, R-44).

The fastest available path is to download one of the public "resume datasets" that circulate on
data-sharing sites. Several contain thousands of resumes. Examining that option properly reveals
five problems, and the last one is the most surprising.

1. **Consent.** Many such corpora were scraped from job boards and CV-hosting sites. The people in
   them did not consent to research use, still less to a commercial product's evaluation pipeline.
2. **Live personal data.** These are real names, phone numbers, addresses, and employment
   histories — frequently still current. Committing them to a repository creates a PII estate with
   none of the controls Phase 3 §7 requires of us for our own users' data.
3. **Positioning.** ADR-0002 and ADR-0012 built this product's credibility on candidate-side
   ethics and explicit consent. "We evaluate on scraped resumes" cannot coexist with "we never use
   your data without asking."
4. **Licence.** Provenance is usually unstated. ADR-0023 requires known licences for code we ship;
   applying a weaker standard to data would be inconsistent.
5. **Fitness for purpose.** ⭐ Most such datasets are plain text or already-parsed JSON. **The
   layout is gone.** Our differentiator is geometric layout analysis (Phase 7 §8). A text-only
   corpus cannot test the thing that makes this product different from a chatbot.

Point 5 means the ethical objection and the engineering objection point the same way: even with
perfect provenance, these corpora could not measure what we need to measure.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Public scraped corpus | Immediate; large | Consent and licence problems; live PII in the repo; contradicts our positioning; **contains no layout, so it cannot test the wedge** |
| Commercial licensed corpus | Clean provenance | Cost; still typically parsed text rather than original documents |
| Collect real resumes at scale ourselves | Realistic; ours | Slow; requires consent infrastructure; creates a real PII estate; still cannot guarantee adversarial layout coverage |
| **Synthetic-first + small consented validation set (chosen)** | Free exact labels; systematic adversarial coverage; no PII; reproducible | Synthetic data is cleaner than reality — a genuine weakness requiring an explicit control |
| Synthetic only, no real data at all | Simplest; zero PII | No control for distribution shift — the risk would be unmeasured rather than absent |

## Decision

**The evaluation corpus is generated synthetically. No scraped or unverified-provenance resume
corpus is used, at any point, for any purpose.**

Resume data may come from exactly three sources:

1. **Synthetic generation** (ADR-0035) — the primary corpus for all six blocking gates.
2. **Explicitly volunteered resumes** with written, purpose-limited, revocable consent — 15–20
   documents used **only** to verify that synthetic performance transfers (dataset D12), stored
   under ADR-0036's constraints.
3. **Opted-in production user content** under ADR-0012 — not used at MVP, and only after
   de-identification if ever used.

**Aggregate, non-identifying statistics derived from production remain permitted without opt-in**
("23% of uploaded resumes use a two-column layout"). That is a statistic, not content, and it is
what actually builds the parse-failure corpus Phase 1 identified as a moat.

Every corpus item carries a provenance record, and a CI rule rejects any item marked as containing
real personal data from entering the repository.

## Consequences

### Positive
- No consent, licence, or PII-liability exposure from evaluation data.
- Consistent with the ethical positioning the product's differentiation depends on — we are not
  doing privately what we tell users we do not do.
- **Synthetic generation is actively better for our gates**: ground-truth labels are free,
  adversarial layouts can be produced systematically rather than hoped for, and bias perturbation
  requires holding content byte-identical, which only generated data allows.
- The corpus is reproducible from a generator plus a seed, so it is versioned as code.
- Expansion — more documents, new locales, new layouts — is a configuration change rather than a
  collection project.

### Negative / Costs
- **Synthetic resumes are cleaner than real ones** (Risk R-50). This is the strategy's real
  weakness, and it is controlled by deliberate noise injection plus the D12 transfer check, not
  waved away.
- Building the generator is upfront work (~3 days) before any gate can run.
- Generated content risks homogeneity if the persona matrix is too narrow (R-52).
- We forgo the "free" option, so the corpus arrives days later than a download would have.

### Follow-up actions required
- **Phase 8 build:** generator first (steps 1–5 of §20), because they unblock the earliest hard
  gate.
- **D12 transfer check:** if a metric on real resumes diverges from synthetic by more than 5
  points, **fix the generator, never the threshold.**
- **Phase 9:** report confidence intervals rather than point estimates, so corpus-size limitations
  are visible.
- **Phase 19:** CI enforces the provenance rule — no item marked as containing real PII may exist
  in the repository.
- If a future need for real data at scale arises, it requires a superseding ADR with a consent
  mechanism designed first, not a dataset acquired first.
