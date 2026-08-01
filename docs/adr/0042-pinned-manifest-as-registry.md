# ADR-0042: A pinned YAML manifest in Git is the model registry

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 9
- **Deciders:** Project owner + engineering team

## Context

The charter asks for a model registry and experiment tracking, and the standard answers are MLflow
or Weights & Biases. Both are designed around a specific workflow: many training runs, each
producing model weights, hyperparameters, and metrics, with a need to compare runs and promote a
chosen artefact to production.

**We produce no model weights.** ADR-0039 rules out fine-tuning at MVP. What we actually have to
version is a different set of things:

- which provider model IDs are in use, per task class
- which prompt version is active for each of six tasks
- which rubric version and locale variants are published
- which embedding model and dimension the vector index was built with
- which calibrated threshold set is in force
- which corpus version metrics were measured against (ADR-0038)

These are configuration, not artefacts. They are small, textual, and change together — a prompt
change often accompanies a threshold change and a rubric bump, and they must be consistent or the
system is in a state nobody intended.

The requirement underneath all of this is reproducibility: given a score a user disputes, or a
metric in a release report, we must be able to say exactly what configuration produced it.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| MLflow | Purpose-built; run comparison; model staging UI | A service to run, back up, and secure; designed around weight artefacts we do not have; adds infrastructure ADR-0018's discipline resists |
| Weights & Biases | Excellent experiment UI; hosted | Ongoing cost; a vendor in the development loop; again optimised for training runs |
| Database table for versions | Queryable; integrated with the app | Not diff-reviewable; a model change would ship without appearing in code review |
| Environment variables per deployment | Simple; no new files | Configuration drifts between environments; no history; nothing to review |
| **Single pinned YAML manifest in Git (chosen)** | Diff-reviewable; atomic with the code; zero infrastructure; `git checkout` reproduces any state | No comparison UI; scales poorly to many concurrent variants |

## Decision

**A single `ai-manifest.yaml`, version-controlled, is the registry.** It pins every model ID, prompt
version, rubric version, embedding model and dimension, threshold set, and corpus version in use.

**Every production artefact and every evaluation result records the manifest version.** A disputed
score, a release report, or a regression investigation resolves to an exact, checkoutable
configuration.

Experiment tracking is an append-only JSONL log in the repository, recording each attempt's
hypothesis, what changed, development metrics, baseline metrics, and verdict — **for adopted and
rejected experiments alike**, since the rejected ones are what stop an idea being retried in three
months.

**Why a file beats a service here, concretely:**

- A model change appears in a pull-request diff. Someone reviewing the change **sees** that the
  inference model was swapped. In a registry service, that change happens in a UI and is invisible
  to code review — which, for a change that alters every score in the product, is the wrong default.
- The manifest is atomic with the code that consumes it. There is no possibility of the deployed
  code and the registered configuration disagreeing.
- Reproducing any historical state is `git checkout`, with no dependency on a service still being
  reachable.
- Zero infrastructure to run, secure, back up, or pay for.

**Revisit triggers** (following ADR-0018's discipline): multiple concurrent model variants running
in production, a genuine A/B testing requirement, more than roughly 20 experiments per week, or
fine-tuning adoption producing actual weight artefacts to store. Phase 15 re-examines this against
those conditions.

## Consequences

### Positive
- **A model or prompt change is visible in code review** — the single most valuable property, given
  that such a change silently alters every score the product produces.
- Any state is fully reproducible with `git checkout`, with no external service dependency.
- No infrastructure to operate, which matters for a team with no on-call rotation (ADR-0009).
- The manifest doubles as documentation: one file answers "what is the AI, right now?"
- The experiment log is greppable, diffable, and travels with the repository.
- Consistent with the versioning discipline already applied to rubrics (FR-ATS-005), prompts
  (Phase 7 §23), and datasets (ADR-0038) — one pattern, applied everywhere.

### Negative / Costs
- **No comparison UI.** Comparing twenty experiments means reading JSONL rather than sorting a
  table — acceptable at a few experiments per week, painful at fifty.
- No built-in charting of metric trends over time; Phase 20's dashboard covers production metrics
  but not experiment history.
- Concurrent variants (A/B testing two prompts simultaneously) do not fit a single-manifest model
  and would force the revisit.
- Discipline is required: an engineer who changes a model ID without bumping the manifest version
  breaks reproducibility, and only review catches it.

### Follow-up actions required
- **Phase 9 harness:** every eval result record includes the manifest version; a result lacking it
  is a harness bug that fails the run, not a warning.
- **Phase 12:** the application loads the manifest at startup and **fails fast** if it is missing or
  malformed — configuration errors surface at deploy, not at first inference.
- **Phase 14:** CI checks that a changed prompt, rubric, or threshold file is accompanied by a
  manifest version bump.
- **Phase 15:** re-evaluate against the revisit triggers; if fine-tuning is ever adopted (ADR-0039),
  weight artefacts genuinely need a registry and this decision is superseded.
- **Phase 20:** production metrics are tagged with the manifest version, so a step change in a
  metric is attributable to a specific configuration change.
