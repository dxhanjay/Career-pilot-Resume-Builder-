# ADR-0041: No RAG in the core loop; retrieval and RAG are different things

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 9
- **Deciders:** Project owner + engineering team

## Context

Retrieval-Augmented Generation is listed in the charter and is close to a default assumption for
any LLM product in 2026. Before adopting it, the term needs disambiguating, because two distinct
things are routinely conflated:

- **Retrieval** — finding relevant items from a collection. We do this extensively: ADR-0031's
  hybrid retrieval matches job-description requirements against resume evidence units using
  lexical search, vector similarity, and taxonomy aliases. It is core to the product.
- **RAG** — retrieving *external documents* to ground a *generation*, so the model produces
  output supported by knowledge it did not have in context.

We do the first. The question is whether we need the second.

Examining our generation tasks — rewrite suggestions, question generation, answer evaluation — the
grounding source in every case is **the user's own resume and the pasted job description**. Both are
already in context. Both are small: a resume is one to three pages, a JD is at most 20,000
characters (FR-MATCH-001). There is no external knowledge the model is missing.

More importantly, ADR-0032's guard already solves the grounding problem, and solves it *better than
RAG would*. RAG improves grounding probabilistically by putting relevant text in context and hoping
the model uses it. Our guard verifies grounding deterministically after generation: every claimed
source span must resolve to real source text, and any novel entity is rejected. A verification is
strictly stronger than a hint.

Adding RAG to the core loop would therefore introduce a corpus to build and maintain, a new failure
mode (retrieving the wrong context, which degrades output while looking like a model problem), and
latency inside a budget where inference already consumes 42% — to solve a problem that is already
solved more directly.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| RAG over a resume-writing knowledge base | Might improve rewrite quality with best-practice guidance | A corpus to curate and maintain; retrieval failures are hard to diagnose; the rubric already encodes best practice as inspectable rules |
| RAG over an ATS-behaviour knowledge base | Genuinely external knowledge (vendor-specific parser quirks) | That knowledge does not exist yet in usable form — it is the moat we are still accumulating |
| RAG over prior user analyses | Personalised context | **Cross-user retrieval risks leaking one user's content into another's output** — unacceptable |
| **No RAG in the core loop; extension points designed (chosen)** | No corpus to maintain; no new failure mode; latency preserved; guard already verifies grounding | Forgoes potential quality gains; may read as a missing capability |

The third row deserves emphasis. Retrieval across users is the natural way to make RAG valuable
here, and it is exactly the design that could surface one person's employer or project detail in
another person's suggestions. That is a data-leakage class we refuse to introduce.

## Decision

**No RAG in the core loop.** Grounding for every generation comes from the user's own documents,
already in context, and is *verified* by the guard rather than *encouraged* by retrieval.

Retrieval remains central to matching (ADR-0031) — that is a separate mechanism and is unaffected.

**RAG is designed as an extension point, not built:**

| Use case | Horizon | Why RAG genuinely fits |
|---|---|---|
| **Learning-path recommendations** (F-34) | H2 | Requires a maintained corpus of courses and resources — genuinely external knowledge the model cannot have |
| ATS-behaviour knowledge base | H2/H3 | Vendor-specific parser quirks; grows over time; naturally retrievable |
| Role-specific interview grounding | H3 | A curated bank of question patterns per role and level |
| Company-specific interview prep | H4 | Retrieval over public company research |

ADR-0015's `ai_port` already provides the seam: a RAG-backed adapter composes into the existing
decorator chain without touching any domain module. Adding it later is an adapter, not a
refactor.

**A constraint that applies whenever RAG is added:** retrieval corpora must be **shared or
public knowledge**, never other users' content. Per-user retrieval over that user's own history is
permissible; cross-user retrieval is not.

## Consequences

### Positive
- No corpus to build, curate, version, or keep current — meaningful ongoing work avoided.
- No retrieval-failure mode, which is among the harder classes of bug to diagnose because it
  manifests as degraded output quality rather than as an error.
- Latency budget preserved, where inference already dominates (Phase 3 §6.1).
- Grounding is *verified* deterministically by the guard, which is stronger than RAG's
  probabilistic improvement — and testable at zero tolerance in CI.
- The cross-user leakage class is structurally excluded rather than mitigated.
- The extension points are documented, so this reads as a decision rather than an oversight.

### Negative / Costs
- Rewrite suggestions get no external best-practice grounding beyond what the rubric and prompt
  encode. Acceptable, because that knowledge is better expressed as inspectable rules than as
  retrieved prose.
- Learning-path recommendations (F-34) genuinely need RAG and are therefore deferred to H2 rather
  than shipped shallow — a scope consequence, stated rather than hidden.
- A reviewer scanning for modern LLM techniques will not find RAG here, which this ADR exists to
  answer.

### Follow-up actions required
- **Phase 12:** the `ai_port` decorator chain accommodates a retrieval-augmenting adapter without
  domain-module changes, so the extension point is real rather than aspirational.
- **Horizon 2:** if learning paths are built, the resource corpus needs provenance, licensing, and
  freshness policies — designed at that point, applying ADR-0034's discipline to a new data type.
- **Any future RAG work:** cross-user retrieval is prohibited; per-user retrieval over that user's
  own history requires a privacy review against ADR-0012.
- Revisit if a generation task appears whose grounding genuinely lies outside the user's documents.
