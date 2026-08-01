# ADR-0046: Design prompts as a stable cacheable prefix plus volatile suffix

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 10
- **Deciders:** Project owner + engineering team

## Context

Prompt caching lets a provider reuse a previously-processed prompt prefix at roughly one tenth the
input price. It is a prefix match: the cache key derives from the exact bytes up to a declared
breakpoint, and **any byte change anywhere in the prefix invalidates everything after it**. Cache
writes cost about 1.25× normal input, so break-even is two requests.

Our prompts are unusually well suited to this, largely by accident of decisions made for other
reasons. Every inference call carries roughly 3,000 tokens of content that is **identical across
every user and every document**: system instructions, the rubric rules for that task, the output
schema, and grounded few-shot examples. The rubric is versioned data (ADR-0013), the prompt is a
versioned artefact (Phase 9 §11.1), and the schema is fixed — so the prefix is stable by
construction rather than by discipline.

Phase 10 §12's cost model shows caching cuts the analysis path by roughly 35%. Against
NFR-COST-001's ₹8 ceiling that is meaningful, and it compounds: the prefix is hot across concurrent
users, so the benefit grows with scale rather than shrinking.

The risk is that caching fails **silently**. There is no error when a prefix changes — the request
simply costs full price, and nothing surfaces unless someone is watching the right metric. A single
interpolated timestamp, an unsorted JSON serialisation, or a per-user locale substitution converts a
35% saving into zero with no signal.

A second, subtler trap emerged while verifying model data: **the minimum cacheable prefix differs
per model and is not monotonic across generations** — 512 tokens on one current model, 1024 on
another, and 4096 on the cheapest small-tier candidate. Our ~3,000-token prefix would cache on two
of those and silently not cache on the third.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| No caching | Nothing to design or maintain | Forgoes ~35% of analysis cost against a tight ceiling |
| Cache opportunistically, no structural rule | Some benefit with little effort | Fails silently and unpredictably as prompts evolve; nobody notices |
| **Structural stable-prefix design with a monitored SLI (chosen)** | Reliable benefit; violations are detectable | Requires discipline in prompt construction and a monitored metric |
| Cache aggressively including per-user content | Larger cached span | Per-user prefixes cache nothing across users — the opposite of the intended effect |

## Decision

**Every prompt is constructed as a stable prefix followed by volatile content, with the cache
breakpoint at the boundary.**

```
STABLE PREFIX (cached)                    VOLATILE (after the breakpoint)
├─ System instructions                    ├─ <<<SOURCE_RESUME>>> …
├─ Rubric rules for this task             ├─ <<<JOB_DESCRIPTION>>> …
├─ Output schema                          └─ Task-specific parameters
└─ Grounded few-shot examples
```

This aligns exactly with Phase 7 §23's channel separation: instructions in the prefix, untrusted
content after it. **The security boundary and the cache boundary are the same line**, which is a
convenient property worth preserving deliberately rather than by luck.

**Five rules:**

1. **Nothing volatile in the prefix.** No timestamp, user ID, request ID, or session identifier.
   Locale-specific rubrics are *separate prefixes*, not one templated prefix — a locale variable
   interpolated into a shared prefix gives every locale its own cache entry and no cross-user reuse.
2. **Deterministic serialisation.** Sorted JSON keys, stable tool ordering. Non-deterministic
   serialisation produces a different prefix on every request while looking identical to a human
   reader.
3. **Breakpoint at the last stable block**, immediately before untrusted content.
4. **Verify, never assume.** Cache-read tokens are a monitored SLI. Zero across repeated requests
   means a silent invalidator, and it alerts.
5. **Respect the per-model minimum.** The prefix must clear the cacheable minimum of every model it
   is used with. Where it does not, either grow the prefix with genuinely useful content — more
   grounded few-shot examples, which improve output quality anyway — or accept no caching on that
   tier and re-run the cost model without it.

Rule 5 is currently live: our ~3,000-token prefix sits below the 4096-token minimum of the leading
small-tier candidate, so the bake-off must verify that caching actually engages rather than assuming
the §12 figures.

## Consequences

### Positive
- Roughly 35% reduction on the analysis path's inference cost, improving as concurrency rises.
- The discipline is nearly free: Phase 7 and ADR-0013 already made the prefix stable, so this
  formalises an existing property rather than imposing a new constraint.
- The cache boundary coinciding with the instruction/data boundary means one rule serves both cost
  and prompt-injection defence.
- A monitored SLI turns a silent failure mode into a detectable one.
- Rule 5's "grow the prefix with more examples" resolution improves grounding quality as a side
  effect — the cheapest kind of fix.

### Negative / Costs
- Prompt authors must understand the boundary; an innocuous-looking interpolation can silently
  cost 35%.
- Per-model cache minimums are a moving, provider-specific detail that must be re-checked whenever
  the manifest changes models.
- Cache writes cost 1.25×, so a genuinely one-shot prompt is slightly *more* expensive with a
  breakpoint than without — caching should not be applied indiscriminately.
- Padding a prefix to reach a minimum is only legitimate when the added content earns its place;
  padding with filler would be a cost increase disguised as an optimisation.

### Follow-up actions required
- **Phase 10 bake-off, stage 6:** verify cache-read tokens are non-zero for each candidate model
  and each prompt; a model whose minimum our prefix cannot clear has its cost re-modelled without
  caching.
- **Phase 12:** prompt assembly enforces the boundary structurally — untrusted content cannot be
  placed before the breakpoint by construction, not by review.
- **Phase 14:** a CI check flags any prompt template containing a timestamp, UUID, or per-user
  interpolation before the breakpoint.
- **Phase 20:** cache hit rate is a dashboarded SLI with an alert on collapse; a drop is a cost
  regression and often the first sign of an unintended prompt change.
- **On any manifest model change:** re-verify the cache minimum. It is not stable across models or
  generations.
