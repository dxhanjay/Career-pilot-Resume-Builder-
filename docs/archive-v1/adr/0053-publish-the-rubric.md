# ADR-0053: Publish the scoring rubric

- **Status:** Accepted
- **Date:** 2026-08-02
- **Phase:** 11
- **Deciders:** Project owner + engineering team

## Context

FR-ATS-004 requires the ATS score to derive from a **published** rubric. Phase 11 must decide what
that means concretely: the full rule set with weights, point values, and locale scopes, or a
sanitised summary.

The rubric is already the right shape to publish. ADR-0013 made it versioned, locale-scoped YAML;
Phase 11 §7 established it *is* the explanation, not an approximation of one. Publishing is
therefore a decision about disclosure, not about producing new artefacts.

Two objections deserve serious treatment rather than dismissal.

**Competitors can copy it.** A rival could lift the rule set wholesale and ship it next week.

**Users can optimise against it.** If people know exactly what is scored, they will target the score
rather than the underlying quality — the classic Goodhart problem, where a measure ceases to be a
good measure once it becomes a target.

There is also a countervailing consideration that runs deeper than either. Phase 1's business
problem is that **ATS screening is an opaque filter people are judged by without being told the
rules.** A product built to expose that opacity, whose own scoring rules are secret, is doing the
same thing to its users at a smaller scale. That is not a compliance argument — it is a coherence
argument about whether the product means what it says.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Publish nothing | Nothing to copy; no gaming | Contradicts FR-ATS-004; reproduces the opacity we criticise; every explainability claim becomes unverifiable |
| Publish category names and weights only | Some transparency; rules stay private | Users can see *that* Parseability is 35% but not *why* they lost 18 points — the actionable part stays hidden |
| **Publish the full rubric, versioned (chosen)** | Complete explanation; verifiable by anyone; coherent with the product's premise | Copyable; invites optimisation against the measure |
| Publish after a delay | Some competitive lag | Complexity for little benefit; the current rubric is what users need to understand today's score |

## Decision

**The full ATS rubric is published: every rule with its identifier, category, weight, point value,
locale scope, and plain-language rationale — versioned, and matching the version stamped on scores
(FR-ATS-005).**

### Why the competitor objection is weak

Phase 1 §9's moat analysis already concluded that prompts and rubrics are **not a moat** — copyable
in a weekend, and not what makes the product hard to replicate. The real moats are the accumulating
parse-failure corpus, longitudinal user data, the eval harness, and institutional relationships. A
competitor with our rubric still lacks the geometric layout analysis, the guard, the corpus, and the
gates. Publishing costs us little that was ever defensible.

### Why the gaming objection dissolves on inspection

Ask concretely what "gaming" our rubric means: removing a second column, adding an Experience
heading, starting bullets with action verbs, making date formats consistent.

**Every one of those genuinely improves machine-readability.** Our rubric is not a proxy for an
underlying quality that can be satisfied deceptively — it is a *description of machine-readability*,
and the only way to satisfy it is to actually become machine-readable. Goodhart's law applies to
proxies; it does not apply where the measure and the target are the same thing.

The Content category is the partial exception: "optimising" there could mean writing hollow bullets
that pattern-match an action-verb rule. That is exactly why ADR-0032's guard exists, why suggestions
must be grounded in the user's own material (ADR-0004), and why the rubric weights Parseability and
Structure — the un-gameable categories — at 60% of the score.

### Scope of publication

| Published | Not published | Reason |
|---|---|---|
| Rule IDs, categories, weights, points, locale scope | Prompt text | Injection-attack surface |
| Plain-language rationale per rule | Internal confidence thresholds | Would allow the guard to be reverse-engineered |
| Rubric version history | Cascade confidence floors | Same |
| Fairness-review outcome per version | Calibration parameters | Same |

## Consequences

### Positive
- **Every explainability claim becomes checkable.** A user can read the rule that cost them 18
  points and judge whether it is reasonable — which is what distinguishes evidence from assertion.
- Coherent with the product's premise: we cannot credibly criticise opaque scoring while scoring
  opaquely.
- Publishing the fairness-review outcome alongside each version makes ADR-0052's process visible
  rather than internal.
- The rubric doubles as content: a public, versioned explanation of what makes a resume
  machine-readable is genuinely useful and serves the SEO channel R-07 depends on.
- Creates healthy pressure on rubric quality — a rule we would be embarrassed to publish is a rule
  we should not be applying.
- Makes the locale dimension (ADR-0013) visible, so users in a market can see which rules apply to
  them and why.

### Negative / Costs
- **Irreversible.** Once published, it is public; a later decision to withdraw it would be more
  damaging than never publishing.
- Competitors get the rule set for free.
- Content-category rules are partially gameable, mitigated but not eliminated by the guard.
- Every rubric change becomes a public change, adding a communication obligation to what would
  otherwise be an internal release.
- Users may argue with specific rules — which is legitimate, and a support cost.

### Follow-up actions required
- **Phase 12:** a public endpoint serves the current and historical rubric versions, so a score's
  stamped version always resolves to the rules that produced it.
- **Phase 13:** each finding links to its published rule; the rubric page is indexable.
- **Phase 11 §9.3:** the fairness review runs before each rubric version is published, and its
  outcome is published with it.
- **Phase 22:** rubric changes follow a documented release process with a changelog — a silent change
  to a published rubric would undermine the point of publishing it.
- Marketing copy may reference the published rubric as a differentiator; it must not overstate it as
  a moat (Phase 1 §9).
