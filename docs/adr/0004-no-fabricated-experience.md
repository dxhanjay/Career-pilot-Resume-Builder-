# ADR-0004: The system never fabricates user experience

- **Status:** Accepted
- **Date:** 2026-07-31
- **Phase:** 1
- **Deciders:** Project owner + engineering team

## Context

The platform rewrites resume content and suggests improvements using an LLM. There is a
direct, commercially tempting path from "improve how this bullet is written" to "invent a
bullet that makes this candidate look better". Users will actively ask for the latter — a
resume with a missing skill scores lower, and the fastest way to raise the score is to add
the skill regardless of whether the user has it.

Three forces make this a decision rather than an obvious default:

1. **Model behaviour.** LLMs asked to "optimise this resume for this job description" will
   naturally drift toward inserting the JD's keywords, quantifying achievements with
   invented numbers ("improved performance by 40%"), and inflating scope. This happens
   without anyone intending it.
2. **Commercial pressure.** Competitors do offer generation that blurs this line, and
   metrics will look better in the short term if we do too.
3. **User harm.** A fabricated resume gets the user into an interview they cannot survive,
   and can constitute misrepresentation to an employer with real consequences for them —
   not for us.

This decision is the enforcement mechanism for USP pillar 3 ("Evidence, not vibes") stated
in Phase 1.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| **Grounded rewriting only (chosen)** | Ethically defensible; protects users; brand differentiator in a market drifting the other way; every claim traceable to source text | Lower headline scores; some users will churn to competitors that will lie for them |
| Unrestricted generation | Higher scores, happier short-term users, easier to build | Harms users; indefensible if reported on; invites misrepresentation claims |
| Generate freely, add a disclaimer | Cheap; shifts responsibility to the user | Disclaimers do not stop harm; the fabricated text is still ours |
| Grounded by default, "creative mode" toggle | Preserves choice | A toggle is a fig leaf — it is unrestricted generation with extra steps |

## Decision

We will **only transform information the user has actually provided**.

Concretely, the system may:
- rephrase, restructure, and strengthen the *expression* of existing content;
- surface a skill the user already demonstrated but did not name explicitly, and label it
  as an inference for the user to confirm;
- **prompt the user for missing facts** ("this bullet would be stronger with a number — what
  was the actual impact?") rather than inventing them.

The system may **not**:
- add employers, roles, dates, education, certifications, or credentials not present in the
  source;
- invent metrics, percentages, team sizes, or timeframes;
- assert proficiency in a skill absent from the user's material solely because a JD requires
  it — missing skills belong in the *gap report and learning path*, never in the rewrite.

Every rewrite suggestion must carry a reference to the source span it derives from, so the
grounding is inspectable rather than promised.

## Consequences

### Positive
- Users are protected from walking into interviews they cannot defend.
- The grounding requirement makes suggestions genuinely explainable (Phase 11).
- "We will not lie for you" is a credible, differentiated brand position.
- Reduces liability around content we generated appearing in employment applications.

### Negative / Costs
- Some users will prefer a competitor that fabricates. Accepted (Risk R-13).
- Requires real engineering: grounding checks and hallucination evals are not free.
- Suggested resumes may score lower than a fabricated one, which some users will read as
  the product being worse.

### Follow-up actions required
- **Phase 7:** prompt design enforces grounding and citation of source spans; rewrite output
  schema carries a `source_span` field per suggestion.
- **Phase 9/19:** a hallucinated-fact evaluation set with a **zero-tolerance gate in CI** —
  a build that invents an employer does not ship. This is the load-bearing control; the
  policy without the eval is decoration.
- **Phase 11:** each suggestion displays what it was derived from.
- **Phase 13:** UI clearly separates "rewritten from your content" from "gap you need to
  close", so the two can never be confused.
- **Marketing/ToS:** state the policy publicly; it is a feature, not fine print.
