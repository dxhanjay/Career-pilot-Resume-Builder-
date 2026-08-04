# ADR-0037: Version the corpus with Git LFS, not DVC

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 8
- **Deciders:** Project owner + engineering team

## Context

The evaluation corpus (datasets D1–D4, D7, D8) consists of roughly 200 generated PDF and DOCX
files, each well under 200 KB, plus JSONL label sidecars — approximately **40 MB in total**.

It must be versioned, because ADR-0038 requires every evaluation result to record the corpus
version it was measured against. It must also be available in CI, because six blocking quality
gates run there.

The conventional answer in an ML context is DVC (Data Version Control) or a similar tool: a
remote data cache, content-addressed storage, pipeline definitions, and lightweight pointers in
Git. DVC exists because ML datasets are frequently gigabytes or terabytes, which Git handles
badly.

Our situation differs in the ways that matter. The corpus is small, it is text and small binaries,
it must sit alongside the code that consumes it, and — because ADR-0035 makes generation seeded and
reproducible — **the corpus is substantially regenerable from the generator plus its config and
seed.** The files are an artefact of code we already version, not an irreplaceable asset.

This is the same right-sizing question ADR-0018 addressed for architecture: adopting infrastructure
whose operating cost exceeds the problem is a cost, not a precaution.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Plain Git (no LFS) | Zero configuration | Binary diffs bloat history; repository grows with every corpus revision |
| **Git LFS (chosen)** | One line of configuration; files sit with the code; works in CI out of the box; supported by every Git host | LFS bandwidth quotas on some hosts; another `git lfs install` step for contributors |
| DVC | Purpose-built for data versioning; remote caches; pipeline orchestration | A tool to learn, operate, and debug; remote storage to configure; **solves problems we do not have at 40 MB** |
| Object storage + manifest of hashes | Full control; no size limit | Custom tooling to build and maintain; CI fetch logic to write |
| Regenerate from seed in CI, store nothing | Zero storage | Every CI run pays generation cost including LLM calls; **and generation is not free or offline**, so CI would depend on a provider |

That last row is worth noting. Reproducibility from a seed is a *verification* property, not a
substitute for storing the corpus — CI must be able to run the gates offline, deterministically, and
without spending money.

## Decision

**Git LFS**, tracking `evals/**/*.pdf` and `evals/**/*.docx`. Label sidecars, generator code,
configs, and seeds stay in plain Git as ordinary text.

Corpus versions are Git tags (`corpus-v1.2.0`) following the semantic scheme in Phase 8 §17:
MAJOR when labels or schema change, MINOR when examples are added, PATCH for corrections.

**Revisit trigger:** the corpus exceeding roughly **1 GB**, or a genuine need for data-pipeline
orchestration (multi-stage derived datasets with caching). Neither is foreseeable at MVP, and both
would be recorded as a superseding ADR citing the met condition — the same discipline ADR-0018
established.

## Consequences

### Positive
- One line of configuration instead of a tool to learn and operate.
- The corpus is versioned atomically **with the code and the rubric that interpret it** — a single
  `git checkout` reproduces a complete evaluation state, which is exactly what ADR-0038 needs.
- CI works with no additional setup beyond `git lfs pull`.
- No remote storage to configure, secure, pay for, or debug.
- Contributors need no new mental model: it is Git.

### Negative / Costs
- LFS bandwidth and storage quotas exist on hosted Git providers; at 40 MB and infrequent revisions
  this is comfortably within free tiers, but it is a ceiling.
- Contributors must run `git lfs install` — a documented setup step and an occasional source of
  confusion when someone clones and gets pointer files.
- Rewriting corpus history (should it ever be necessary) is more awkward than deleting objects from
  a DVC remote.
- If the corpus grows unexpectedly — say, an audio corpus for H2 voice interviews — this decision
  will need revisiting sooner than the 1 GB trigger suggests.

### Follow-up actions required
- **Phase 8 build:** `.gitattributes` tracking rules committed before the first corpus file, so no
  binary ever lands in plain Git history.
- **Phase 14:** CI runs `git lfs pull` before the eval stage; developer setup documentation includes
  `git lfs install` (NFR-MNT-003's 30-minute onboarding).
- **Phase 19:** a CI check verifies the corpus tag matches the version recorded in eval results
  (ADR-0038).
- **Horizon 2:** re-evaluate before adding an audio corpus for voice interviews — that is the change
  most likely to breach the size assumption behind this decision.
