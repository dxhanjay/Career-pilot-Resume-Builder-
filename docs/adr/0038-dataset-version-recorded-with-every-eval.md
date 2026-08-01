# ADR-0038: Every evaluation result records the dataset version it was measured against

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 8
- **Deciders:** Project owner + engineering team

## Context

Six blocking quality gates are expressed as numeric thresholds: section F1 ≥ 0.90, entity accuracy
≥ 0.95, score standard deviation ≤ 2, zero fabricated facts, bias variance ≤ 1 point, zero
successful injections.

A number like "F1 = 0.91" is meaningless on its own. It is only interpretable relative to the
corpus it was measured on. Two specific failures follow from forgetting this, and both are quiet:

1. **False regression.** Someone adds 30 harder documents to the corpus. F1 drops from 0.93 to
   0.89 and the build fails. A day is spent hunting a parser regression that does not exist — the
   parser is unchanged, the measuring stick moved.
2. **False progress — the more dangerous one.** A label correction or a corpus rebalance nudges F1
   from 0.89 to 0.91, the gate passes, and a genuine parsing deficiency ships. Nobody investigates
   an improvement.

This project has already established exactly this discipline for a structurally identical problem.
FR-ATS-005 requires that every stored score record its `rubric_version`, because otherwise the first
rubric change silently makes all historical scores incomparable and the progress feature begins to
lie. Phase 7 extended the same rule to `prompt_version` on every AI artefact, and Phase 4 made cache
keys carry both.

**Corpus version is the third member of that family, and omitting it would leave the evaluation
layer as the one place where the discipline does not apply.**

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Record nothing; trust the latest corpus | Zero effort | False regressions and, worse, false progress; historical results incomparable |
| Freeze the corpus permanently | Comparability guaranteed | The corpus must grow — new rules need new coverage (R-54), and R-55 may require expansion |
| Record the Git commit SHA | Precise; free | Opaque — a SHA does not communicate whether a change was a label fix or a schema break |
| **Semantic corpus version recorded with every result (chosen)** | Comparability is explicit; the version communicates whether comparison is valid | Requires versioning discipline and a bump decision per change |

## Decision

**Every evaluation run records the dataset version, and every reported metric carries it.**

Corpus versions follow semantic versioning, where the semantics are specifically about
**comparability of results**:

| Bump | Meaning | Are prior results comparable? |
|---|---|---|
| **MAJOR** | Labels or label schema changed | ❌ **No** — prior metrics are void for comparison |
| **MINOR** | Examples added, existing labels unchanged | ◐ Comparable **with a note** — the population changed |
| **PATCH** | Label corrections, corrupt file fixes | ✅ Yes |

Every eval result is stored as a record carrying the corpus version, the generator version and
seed, the rubric version, the prompt version, the model identifier, and the commit SHA — so a
result is fully reproducible from its own metadata.

**CI enforces two rules:**

1. A gate result may only be compared against a baseline with a compatible corpus version. A MAJOR
   bump **resets the baseline** and requires explicit re-approval rather than silently comparing
   against void history.
2. A MINOR or MAJOR bump in the same pull request as a code change is flagged for review, because
   it makes attribution ambiguous: the metric moved, and it is unclear which change moved it.

Rule 2 is the practically important one. Changing the code and the measuring stick together is how
both false regressions and false progress enter unnoticed.

## Consequences

### Positive
- Metrics become interpretable over time — a trend line means something, because the population
  behind it is known.
- Eliminates the false-regression debugging session, and more importantly the false-progress ship.
- Corpus changes become deliberate, versioned acts rather than incidental commits.
- Full reproducibility: any historical result can be regenerated from its recorded metadata plus
  the seeded generator (ADR-0035).
- Applies the same versioning discipline already established for rubrics and prompts, so the system
  is consistent rather than having one soft spot.
- A MAJOR bump forcing baseline re-approval means a change to what "good" means is a decision
  someone makes, not something that happens.

### Negative / Costs
- Someone must decide the bump level for each corpus change, and misjudging it (calling a label
  change PATCH) defeats the mechanism.
- MAJOR bumps discard historical comparability, which is correct but occasionally frustrating when
  a genuinely small label fix has wide reach.
- Separating corpus changes from code changes into different pull requests adds process friction.
- Eval result records are additional metadata to store and maintain.

### Follow-up actions required
- **Phase 9:** the eval harness emits a result record containing all six version fields; a result
  without them is a harness bug, not a warning.
- **Phase 14:** CI implements the baseline-compatibility check and the same-PR-change flag.
- **Phase 19:** gate reports display the corpus version prominently alongside the metric, so a
  reader cannot see the number without seeing what it was measured on.
- **Phase 20:** the quality dashboard plots metrics as a series annotated with corpus version
  changes, so a step change in the line is visibly attributable.
- Corpus changes ship in their own pull requests wherever practical.
