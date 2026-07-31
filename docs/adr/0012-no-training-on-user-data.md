# ADR-0012: No training on user content without explicit opt-in

- **Status:** Accepted
- **Date:** 2026-07-31
- **Phase:** 3
- **Deciders:** Project owner + engineering team

## Context

Phase 1 identified an accumulating corpus of real resumes — and specifically which layouts
break which parsers — as one of the few genuine moats available to this project. That creates
a standing incentive to use user content to improve our models.

The data in question is unusually sensitive. A resume is dense PII: full name, contact
details, address, complete employment history, education, and in Indian resumes frequently a
photograph, date of birth, and family details. Interview answers are more sensitive still —
users discuss failures, conflicts, salary, and reasons for leaving jobs. This is material
people share with us precisely because they expect it to stay private.

Three forces push against a permissive default:

1. **Legal.** GDPR requires a lawful basis for each purpose. Using content for model
   improvement is a *different purpose* from providing the service, so contract does not cover
   it; it needs consent or a defensible legitimate-interest assessment. India's DPDP Act is
   consent-centric and requires purpose limitation. A buried clause in a terms-of-service
   document is not the consent either regime contemplates.
2. **Trust.** Our entire USP rests on being the honest, candidate-side, evidence-based product
   (ADR-0002, ADR-0004). "We trained on your resume without telling you" is the single fastest
   way to destroy that positioning, and it is the kind of story that spreads.
3. **Leakage.** Content memorised during training can resurface in another user's output.
   Training on resumes risks one user's employer, project, or contact details appearing in
   someone else's suggestions — a breach that is very hard to detect and impossible to undo.

Against this: an opt-in corpus is smaller than an opt-out one, and the moat accumulates more
slowly.

There is a second decision embedded here: what our **third-party AI providers** are permitted
to do with content we send them. Sending resumes to a provider that trains on API inputs would
make the entire policy meaningless.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Train by default, opt-out available | Largest corpus fastest | Weakest lawful basis; most users never see the setting; catastrophic trust story if reported; leakage risk |
| Train on everything, disclose in ToS | Simple | Not meaningful consent under GDPR or DPDP; reputationally indefensible |
| **Explicit, separate, revocable opt-in (chosen)** | Defensible lawful basis; consistent with the brand; users who consent genuinely consented | Smaller corpus; moat accumulates slower |
| Never train on user content at all | Simplest privacy story | Forgoes a real moat; unnecessary — informed consent is a legitimate mechanism |

## Decision

**User content is never used for model training, fine-tuning, or evaluation-set construction
without explicit, separate, revocable opt-in consent** (FR-PRIV-004).

Specifics:

1. Opt-in is a **distinct choice**, not bundled into the privacy policy or terms acceptance,
   and not pre-ticked.
2. It is **revocable at any time, self-serve**, with revocation stopping future use
   immediately.
3. Declining costs the user **nothing** — no feature, quality, or quota difference. A consent
   that carries a penalty is not freely given.
4. Any consented corpus is **de-identified before use**: names, contact details, and
   identifiable employer/institution combinations stripped.
5. **Third-party providers must contractually guarantee no training on our data**
   (NFR-CMPL-002). A provider that will not commit to this is not eligible for selection in
   Phase 10, regardless of quality or price.
6. Direct identifiers are minimised or redacted before inference where doing so does not
   degrade output quality (FR-PRIV-006).
7. **Aggregate, non-identifying statistics are permitted without opt-in** — e.g. "23% of
   uploaded resumes use a two-column layout". This is derived analytics, not content use, and
   it is what actually powers the parse-failure moat.

## Consequences

### Positive
- Clean lawful basis under GDPR and DPDP; the consent is real, so it survives scrutiny.
- Consistent with the honesty positioning the product's differentiation rests on.
- Eliminates cross-user content-leakage risk in generated output.
- Point 7 preserves most of the intended moat: the valuable signal is *which layouts break
  parsers*, which is aggregate structural data, not resume content.
- Constrains provider selection in Phase 10 in a way that is easier to enforce now than to
  renegotiate later.

### Negative / Costs
- A consented fine-tuning corpus grows slowly and may never reach useful size.
- Consent state must be modelled, versioned, and enforced at every point content is used.
- Provider choice is narrowed; some cheaper options may be excluded.
- De-identification of any consented corpus is real engineering work.

### Follow-up actions required
- **Phase 6:** consent is a versioned, timestamped, per-purpose record — not a boolean on the
  user row. Revocation is an event, not an update.
- **Phase 8 (dataset strategy):** training data comes from public, synthetic, and licensed
  sources by default; user content only where consent exists and only after de-identification.
- **Phase 10:** "no training on customer data" is a **hard elimination criterion** for provider
  selection, evaluated before accuracy or cost.
- **Phase 12:** consent is checked at the point of use, not assumed from a cached flag.
- **Phase 13:** the opt-in appears in settings as a standalone, unchecked, plainly-worded
  choice with a clear statement of what it does.
- **Privacy notice:** state the policy plainly and publish the sub-processor list
  (FR-PRIV-007).
