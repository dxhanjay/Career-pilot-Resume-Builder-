# ADR-0011: WCAG 2.2 AA enforced in CI, and text mode is permanent

- **Status:** Accepted
- **Date:** 2026-07-31
- **Phase:** 3
- **Deciders:** Project owner + engineering team

## Context

Two accessibility questions had to be settled before Phase 4, because both change the
architecture rather than the styling.

**First: what standard, and when is it checked?** Accessibility deferred to a "polish pass"
is accessibility that never happens (Risk R-25). Retrofitting keyboard navigation, focus
management, and semantic structure into a built interface is substantially more expensive
than building it correctly, because it touches every component.

**Second: what happens to text mode when voice interviews arrive in Horizon 2?** The obvious
trajectory is that voice becomes the "real" product and text degrades into a legacy fallback
that quietly stops receiving features. That trajectory excludes a large set of users:

- deaf and hard-of-hearing users
- non-speaking users and users with speech differences
- users whose accented speech is poorly served by ASR — which, for an India-first product, is
  not an edge case but a substantial fraction of the target market
- anyone without a private space to speak aloud, which describes a great many people
  practising interviews from a shared home

There is also a specific consideration for *this* product's users. They are people under
stress, frequently on low-bandwidth connections and inexpensive devices, and often non-native
English speakers. Accessibility work here is usability work for the median user, not
accommodation for a minority.

Finally, three hard cases are specific to what we are building: a turn-based interview screen
with timing is among the most accessibility-hostile patterns available; score visualisations
default to colour-coded gauges that convey information by colour alone; and generated PDF
reports — the artefact users most want to share — are, without a tag tree, a flat image to a
screen reader.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| No formal target | Fastest short-term | Excludes users; legal exposure; retrofit cost compounds |
| WCAG 2.1 AA | Familiar; mature tooling | Superseded; 2.2 adds criteria directly relevant to us (focus appearance, dragging alternatives) |
| **WCAG 2.2 AA, CI-enforced (chosen)** | Current standard; AA is the legal reference point; automation makes it unskippable | Some 2.2 criteria are newer and tooling coverage lags |
| WCAG 2.2 AAA | Maximum inclusion | Impractical for a product with rich data visualisation; not the legal reference point |
| Manual audit before launch only | Cheap in the short term | Findings arrive when they are most expensive to fix |
| Voice-primary at H2, text as fallback | Simpler product story | Excludes the user groups above; discards an asset we already built |

## Decision

**Target WCAG 2.2 Level AA**, with automated checks (axe-core or equivalent) running as a **CI
gate from slice S0** — not as a pre-launch audit. Zero violations merge.

Automation is supplemented by manual screen-reader testing of the golden path on NVDA and
VoiceOver before each release, because automated tooling detects roughly a third of real
issues.

Three product-specific requirements are adopted as consequences:

1. **No un-extendable time limits** anywhere, including the interview screen (WCAG 2.2.1;
   FR-INT-010). Combined with resumable sessions (FR-INT-006), this makes the most hostile
   screen in the product usable.
2. **No information conveyed by colour alone.** Scores carry a numeric value and a text label,
   never only a red/amber/green arc (WCAG 1.4.1).
3. **Exported PDF reports must be tagged and accessible** (PDF/UA; NFR-A11Y-012). This is
   cheap at generation time and painful to retrofit.

**Text mode is a permanently supported, fully-featured path.** When voice interviews ship in
Horizon 2, text does not become a fallback: any capability added to voice interviews must have
a text equivalent, and text mode continues to receive feature work. The MVP being text-first
(ADR-0006) is treated as an accessibility asset to preserve, not a limitation to outgrow.

## Consequences

### Positive
- Accessibility cannot be silently skipped, because the gate is automated and blocking.
- Building it correctly from S0 is materially cheaper than retrofitting.
- The requirements that serve disabled users — resumable sessions, no forced timers, plain
  language, text alternatives — improve the product for every stressed, low-bandwidth,
  non-native-English user, which is most of our market.
- Text parity keeps the product usable for users ASR serves poorly, which in an India-first
  market is a competitive advantage rather than a concession.
- Reduces legal exposure in jurisdictions with accessibility statutes.

### Negative / Costs
- Roughly 10–15% additional frontend effort, concentrated in the interview screen and the
  data visualisations.
- CI gate will occasionally block a merge on a genuine but inconvenient violation.
- Maintaining voice/text parity in H2 is ongoing work, not a one-time cost.
- Accessible PDF generation constrains the choice of PDF library in Phase 13.

### Follow-up actions required
- **Phase 13:** component library is accessible by default — focus management, semantic
  structure, ARIA live regions for async job progress (NFR-A11Y-007); PDF library selected for
  tagged output.
- **Phase 14:** axe-core CI gate configured in the pipeline from the first frontend commit.
- **Phase 19:** manual NVDA and VoiceOver passes on the golden path are release checklist items.
- **H2 voice work:** every voice capability ships with a text equivalent in the same release,
  or it does not ship.
