# ADR-0003: Do not build facial-expression / emotion analysis

- **Status:** Proposed — awaiting project owner's decision (Open Question 5, Phase 1)
- **Date:** 2026-07-31
- **Phase:** 1
- **Deciders:** Project owner + engineering team

## Context

The project charter lists "Analyze facial expressions (optional)" and "Analyze confidence
level" among the platform's capabilities. Implementing facial expression analysis means
capturing video of a user's face and inferring an emotional or confidence state from it.

Three independent problems make this the highest risk-to-value feature in the entire
product:

**1. Scientific validity.** The premise that discrete emotional states can be reliably read
from facial movements is contested in the psychology literature; expression–emotion mappings
vary substantially across individuals, cultures, and contexts. A "confidence score" derived
this way risks being confidently wrong — and it would be wrong in a way that
disproportionately penalises neurodivergent users and users from cultures with different
display norms (Risk R-12).

**2. Data category.** Face video processed to identify or characterise a person is
biometric or biometric-adjacent data. Under GDPR it can fall under Article 9 special
category data, requiring an explicit lawful basis and a Data Protection Impact Assessment.
Some US state laws (notably Illinois BIPA) attach statutory damages per violation for
mishandled biometric identifiers.

**3. Prohibited-practice exposure.** The EU AI Act prohibits the use of AI systems to infer
emotions of natural persons in the areas of workplace and education institutions, outside
narrow medical and safety exceptions. Our product sits squarely in the
employment-preparation and education space, and Horizon 3 explicitly targets universities
and bootcamps. Even if a candidate-side self-practice tool were argued to fall outside the
prohibition, arguing that position is a legal cost we cannot absorb, and the institutional
sales channel makes it materially harder to argue.

Against this: the user-facing value is a cosmetic score ("you looked 72% confident") that
users cannot straightforwardly act on. Nearly all of the actionable coaching value — pace,
filler words, pauses, hesitation, energy, answer structure — is obtainable from **audio**,
which carries none of the biometric or prohibited-practice exposure.

> This is an engineering risk assessment, not legal advice. Regulatory detail varies by
> jurisdiction and evolves; obtain qualified legal counsel before any biometric feature.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| **Drop permanently (recommended)** | Eliminates the largest legal risk in the product; removes a known bias vector; saves substantial engineering effort; enables clean institutional sales in the EU | Loses a demo-impressive feature; competitors may advertise it |
| Voice-only delivery analytics | ~80% of the coaching value; no biometric data; works on low-bandwidth connections; far cheaper | No eye-contact or posture feedback |
| Opt-in, on-device, nothing stored, disabled in EU | Preserves the feature with reduced exposure | Still processes biometric data; still likely prohibited in EU workplace/education contexts; on-device ML across browsers is a large engineering cost; geo-gating is imperfect |
| Build it fully server-side | Simplest to implement | Maximum exposure on every axis. Rejected outright. |

## Decision

We will **not** build facial-expression or emotion-inference analysis.

Delivery feedback in mock interviews will be derived from **audio and transcript signals
only**: speaking pace, filler-word rate, pause distribution, response latency, verbosity,
and answer structure (e.g. STAR compliance). Where we report a "confidence" style signal,
it will be presented as an explicitly-defined measure of *speech delivery*, with its inputs
disclosed to the user — never as an inference about the user's inner emotional state.

Optional video recording for **user self-review only** (the user watches their own playback,
no machine inference, opt-in, user-deletable) may be reconsidered in a later ADR, as it
raises storage questions but not inference questions.

## Consequences

### Positive
- Removes the single largest legal and reputational risk in the product.
- Removes a significant algorithmic bias vector against neurodivergent and
  culturally-diverse users.
- Saves substantial engineering effort in an already over-scoped project (Risk R-01, R-11).
- Makes EU and institutional markets accessible without a prohibited-practice question.
- Reduces bandwidth, cost, and device requirements — video is expensive to move and store.

### Negative / Costs
- Loses a visually impressive demo feature.
- Competitors offering it may appear more advanced on a feature-comparison table.
- No feedback on eye contact, posture, or on-camera presence.

### Follow-up actions required
- **Phase 2:** remove facial analysis from all feature lists and pricing tiers.
- **Phase 3:** NFRs specify audio-only capture for interview sessions.
- **Phase 7:** speech-analytics module design covers pace, fillers, pauses, latency,
  structure — with each metric's definition published to users.
- **Phase 11:** any delivery score must ship with its rubric and stated limitations.
- **Marketing:** do not use the words "emotion detection" or "AI reads your confidence".
- **If the project owner overrides this ADR:** a DPIA, explicit opt-in consent, EU
  geo-disablement, on-device-only inference, zero server-side retention, and qualified
  legal review become blocking prerequisites, and this ADR is superseded rather than edited.
