# ADR-0040: Frozen holdout opened only at release gates; calibration by k-fold on development data

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 9
- **Deciders:** Project owner + engineering team

## Context

Twelve parameters require calibration (Phase 9 §8): layout thresholds, cascade confidence floors,
entity confidence floors, skill fuzzy-match thresholds, match similarity floors, retrieval fusion
weights, guard fuzziness, and rubric weights and point values. The corpus available for tuning them
is 50 documents, expanding to 150.

That ratio is the problem. Twelve tunable parameters against 50 documents is a configuration where
overfitting is not a risk to watch for — it is the default outcome unless the process prevents it.

Two specific mechanisms cause it, and both are quiet:

**Direct overfitting.** Tuning parameters to maximise a metric on a set, then reporting that metric,
produces an optimistic number by construction.

**Holdout erosion.** Even with a nominal held-out set, every peek costs something. A developer
adjusts a threshold, runs the holdout, sees a failure, adjusts again, runs again. After twenty
iterations the "held-out" score reflects twenty decisions informed by it. It has silently become a
second development set, and nobody can quantify by how much the number is inflated.

The consequence is concrete and specific to this project: **shipping a parser that reports F1 0.92
and performs at 0.84 on real documents.** Slice S1's gate exists to prevent building four slices on
unreliable parsing. A gate that can be tuned until it passes prevents nothing.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Single train/test split | Standard; simple | 10–15 test documents is too noisy; erodes with repeated access |
| Tune on everything, report on everything | Maximum data use | Reported metrics meaningless; the gate is decorative |
| k-fold cross-validation over the whole corpus | Efficient use of small data | No clean final estimate; the whole corpus is consumed by tuning |
| **Frozen holdout + k-fold on development (chosen)** | Clean final estimate; efficient calibration; erosion made visible | Fewer documents for tuning; holdout metrics are noisy at n=15 |
| Nested cross-validation | Statistically strongest | Complex; expensive with LLM calls in the loop; disproportionate at this scale |

## Decision

**The corpus splits 70% development / 30% frozen holdout. All calibration uses k-fold
cross-validation on the development set. The holdout is opened only at release gates.**

**Four rules:**

1. **Calibration never sees the holdout.** Every parameter is tuned against out-of-fold predictions
   within the development set.
2. **The holdout is opened at T4 release gates only** — not during iteration, not "just to check."
3. **Holdout access is logged**, recording the manifest version, the date, and the result. The log
   makes erosion *visible*: if the holdout has been evaluated forty times this month, its score is
   no longer trustworthy, and we know that rather than assuming otherwise.
4. **The tuned-parameter budget is bounded**: ≤ 12 parameters against ≥ 50 development documents.
   Adding a thirteenth requires expanding the corpus first — which, because generation is a config
   change (ADR-0035), is a cheap constraint to honour rather than a theoretical one.

**Corpus growth uses a deterministic hash of the document ID** to assign items to development or
holdout — never random assignment. Random assignment would let an item migrate between sets across
corpus versions, silently contaminating the holdout with something previously used for tuning.

**Leakage controls specific to this corpus:**

| Hazard | Control |
|---|---|
| One persona rendered into 16 templates, split across sets | Split by **content ID** — all renderings of a persona stay together |
| Bias-perturbation variants split across sets | All 12 variants of a base document are one unit |
| LLM response cache built on holdout items during development | Cache partitioned by set; the dev cache cannot contain holdout responses |
| D12 real resumes used for tuning | ADR-0036 makes them manual and release-only — **validation only, never calibration** |

**When a holdout gate fails, the response is to return to the development set.** Adjusting a
parameter in response to a holdout result converts the holdout into a development set — permanently,
after a single occurrence.

## Consequences

### Positive
- Reported quality metrics mean what they claim, which is the entire point of having gates.
- Erosion becomes measurable rather than invisible — the access log is the honest signal.
- The parameter budget makes the overfitting constraint structural rather than a matter of
  discipline under deadline pressure.
- Deterministic splitting means corpus growth is safe; expansion cannot contaminate history.
- The content-ID split closes the leakage path that synthetic generation specifically creates —
  a hazard that would not exist with a real corpus and is easy to miss.
- Forces corpus expansion (cheap) rather than parameter proliferation (expensive) as the response
  to needing more tuning capacity.

### Negative / Costs
- Only ~35 documents available for calibration at the initial corpus size, making some parameters
  noisy to tune.
- **Holdout metrics at n=15 have wide confidence intervals** — a genuine limitation, addressed by
  reporting intervals rather than point estimates and by expanding the corpus before the S2 gate.
- Iteration is slower: a failed holdout gate means returning to development rather than a quick
  adjustment.
- Requires real discipline. The rule is trivially breakable by one impatient tweak, and nothing in
  the tooling can fully prevent it — only the access log makes it visible afterwards.

### Follow-up actions required
- **Phase 8 build:** deterministic hash-based split implemented in the corpus manifest, applied at
  generation time.
- **Phase 9 harness:** holdout evaluation writes an access-log entry automatically; the entry cannot
  be suppressed.
- **Phase 14:** CI runs T1–T3 against development only; T4 holdout gates run in the release
  pipeline, not on branches.
- **Phase 19:** the release checklist includes reviewing the holdout access count — a rising count
  is a process problem to discuss, not a number to ignore.
- Expand the corpus to 150 before the S2 gate, so holdout confidence intervals narrow where the
  score-quality question becomes binding.
