# ADR-0031: Match at requirement level with hybrid retrieval, not document-level cosine

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 7
- **Deciders:** Project owner + engineering team

## Context

FR-MATCH-002 requires a semantic match score between a resume and a job description.
FR-MATCH-003 requires listing matched and missing keywords with semantic equivalents recognised.
FR-MATCH-005 requires a ranked skill-gap list, which then feeds gap-targeted interview questions
(FR-INT-004) — the closed loop that Phase 1 identified as our structural differentiator.

The standard implementation is to embed the whole resume and the whole job description and report
their cosine similarity. It is one line of code and it is wrong for this product in three specific
ways:

1. **It is unexplainable.** A single number cannot say *which* requirement is unmet, so the gap
   report — the thing that actually helps the user — has nothing to draw on.
2. **It is insensitive.** Long documents average out into a narrow similarity band. Two candidates
   with very different suitability for the same role score within a few points of each other,
   because most of both documents is generic resume language.
3. **It cannot feed the loop.** Gap-targeted question generation needs *ranked individual gaps*. A
   document-level score produces none.

There is a fourth problem specific to hiring text. Job descriptions contain exact-match
requirements — *"AWS Certified Solutions Architect"*, *"3+ years"*, a named framework — where
paraphrase is not acceptable. Pure semantic similarity treats "familiar with cloud platforms" as a
near match for a specific certification, which is confidently wrong. Conversely, pure lexical
matching misses that "containerised deployments" covers a Kubernetes requirement.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Document-level cosine | One line of code; fast | Unexplainable; insensitive; produces no gaps; cannot feed the interview loop |
| Keyword overlap only (classic ATS-style) | Transparent; cheap | Misses all paraphrase; encourages keyword stuffing, which is advice we refuse to give |
| LLM reads both documents and reports a match | Flexible; handles nuance | Expensive on long text; non-reproducible; hard to cite evidence; a score from a model (against ADR-0030) |
| **Requirement-level units + hybrid retrieval + deterministic coverage scoring (chosen)** | Explainable per requirement; produces ranked gaps directly; reproducible; catches both exact and paraphrased matches | More components; thresholds need calibration; more embeddings to store |

## Decision

**Both documents are decomposed into units, matched unit-to-unit, and scored deterministically.**

```
Job description → requirement units   (text, kind: hard|nice, importance, skills, source_span)
Resume          → evidence units      (individual bullets, skill entries, project descriptions)

For each requirement: retrieve the best-matching evidence units.
```

**Retrieval is hybrid**, because no single channel is sufficient:

| Channel | Catches | Would miss alone |
|---|---|---|
| Lexical (PostgreSQL full-text) | Exact terms, credentials, named tools | Paraphrase |
| Semantic (pgvector / HNSW) | "containerised deployments" ≈ "Docker, Kubernetes" | Exact credential strings |
| Taxonomy alias | `K8s` ≡ `Kubernetes` | Anything outside the taxonomy |

Results are fused by Reciprocal Rank Fusion, then a calibrated similarity floor classifies each
requirement as `matched` / `partial` / `missing`.

**Scoring is deterministic arithmetic** (consistent with ADR-0030):

```
coverage(req) = 1.0 matched | 0.5 partial | 0.0 missing
weight(req)   = importance × (1.0 if hard else 0.4)
match_score   = round(100 × Σ(coverage × weight) / Σ(weight))
gap_rank      = weight × (1 − coverage)          → feeds interview slot binding
```

**Thresholds are calibrated against labelled pairs in Phase 9**, never shipped as guesses — a
mis-set floor produces confident nonsense, which is the failure mode this product least tolerates
(Risk R-47).

`match_keywords.matched_via` records whether a match was `exact`, `alias`, or `semantic`, so the
report can explain itself.

## Consequences

### Positive
- **Every requirement links to the specific resume line that covers it, or to nothing.** The report
  becomes advice — *"'container orchestration' is covered by your Kubernetes line"* — rather than a
  number.
- The ranked gap list falls out of the same computation, feeding the interview blueprint directly
  (ADR-0033) and closing the loop that differentiates the product.
- Scores are reproducible: embeddings are cached by content hash and the arithmetic is pure.
- Hard-versus-nice weighting means missing a genuine requirement costs more than missing a
  preference, which matches how hiring actually works.
- Sensitivity is real — two resumes for the same role produce meaningfully different scores.
- Hybrid retrieval means we never advise keyword stuffing, because semantic coverage counts.

### Negative / Costs
- More moving parts: unit segmentation, three retrieval channels, fusion, calibration.
- More embeddings stored (per unit rather than per document), increasing vector index size —
  bounded by resume length, and pgvector handles it comfortably at our scale.
- Unit segmentation quality now matters: a badly-split bullet degrades matching, adding a dependency
  on parsing quality that document-level embedding would not have.
- Threshold calibration is required work before launch and needs revisiting whenever the embedding
  model changes.

### Follow-up actions required
- **Phase 8:** a labelled dataset of resume–JD pairs with per-requirement coverage judgements, for
  threshold calibration.
- **Phase 9:** calibrate the `matched`/`partial`/`missing` floors; report precision and recall per
  channel so the fusion weights are evidence-based.
- **Phase 10:** embedding model selection evaluated on *this* retrieval task, not on general
  benchmarks; a model change requires re-calibration, not just re-indexing.
- **Phase 12:** unit segmentation is a shared, tested component — matching and improvement both
  depend on it.
- **Phase 20:** distribution of match scores is monitored; a sudden shift indicates model or
  calibration drift.
