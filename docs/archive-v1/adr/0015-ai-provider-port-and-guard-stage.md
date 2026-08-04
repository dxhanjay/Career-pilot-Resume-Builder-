# ADR-0015: All model access goes through one port, and all model output passes a guard

- **Status:** Accepted
- **Date:** 2026-07-31
- **Phase:** 4
- **Deciders:** Project owner + engineering team

## Context

Every core feature depends on a third-party model provider (Phase 1 §13). That single fact
creates four distinct problems, and they are usually solved in four different places — badly.

1. **Vendor lock-in (Risk R-05).** Provider pricing, availability, terms, and quality all change.
   Direct SDK calls scattered across modules make switching a repository-wide edit.
2. **Cost attribution (NFR-COST-006).** Cost must be attributable per feature and per user, or
   R-03 (inverted unit economics) is unmanageable. Attribution added at each call site is
   attribution that will be missing at half of them.
3. **Caching (NFR-COST-004, ≥30% hit rate).** The largest cost lever available is not re-running
   identical work. A cache implemented per feature will be implemented inconsistently.
4. **Untrusted output.** Model responses are not trustworthy by virtue of us having asked for
   them. They can violate the response schema (NFR-AI-007), fabricate facts (FR-IMP-004,
   ADR-0004), or echo instructions injected via a job description or interview answer
   (FR-MATCH-006, FR-INT-011).

Point 4 is the one most often mishandled. A model response is *input from an untrusted source* —
architecturally equivalent to a user-submitted form, not to a return value from our own code.

There is also a delivery consideration: slice S0 must build the entire async pipeline **before
any provider is selected** (Phase 10 selects it). That is only possible if the pipeline depends
on an abstraction rather than a vendor SDK.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Direct SDK calls in each module | Least code; obvious | Lock-in; inconsistent caching; missing cost tags; no uniform validation; S0 blocked on provider choice |
| Thin wrapper function | Some centralisation | Nothing enforces its use; cross-cutting concerns still scattered |
| **Single port + decorator chain (chosen)** | One place for caching, metering, timeouts, breaking, validation, fallback; testable with a fake | Indirection; the port must be designed from our needs, not the vendor's |
| Third-party LLM gateway product | Batteries included | Another dependency and cost; still need our own guard; less control over the grounding checks that matter most to us |
| Validate output only where it matters | Less work | "Where it matters" is every place; exceptions become the rule |

## Decision

**All model and embedding access goes through a single port. No module ever calls a provider SDK
directly.**

The port is defined in terms of *our* domain's needs (`complete(request) -> ValidatedResponse`,
`embed(texts) -> Vectors`), not a vendor's API shape. Implementations are composed as a decorator
chain:

```
cache lookup → prompt assembly (instruction ‖ untrusted data, strictly separated)
             → token + cost metering
             → timeout + retry + circuit breaker
             → structured-output enforcement
             → fallback to secondary provider
             → provider adapter
```

**Every response then passes a mandatory, non-bypassable GUARD stage** before it can be persisted
or shown to a user. The guard:

- validates against the declared response schema; on violation it retries and then fails the job
  — raw model output is never surfaced (FR-IMP-007);
- verifies **grounding**: every rewrite's `source_span` must resolve to text actually present in
  the source document;
- runs **fabrication checks**: no employer, job title, date, credential, or metric that is absent
  from the source (FR-IMP-004);
- strips echoed instruction text arising from injection attempts.

A `FakeAdapter` implementing the same port ships from slice S0, so the full pipeline is built,
tested, and deployed before Phase 10 chooses a provider.

## Consequences

### Positive
- Provider swap becomes a configuration change, directly mitigating R-05.
- Cost attribution is complete by construction, because there is exactly one call site
  (NFR-COST-006).
- Caching is uniform and correct — keyed on `(content_hash, prompt_version, rubric_version)` —
  delivering NFR-COST-004 in one place.
- Timeouts, retries, and circuit breaking are consistent, so provider failure is contained
  rather than cascading (NFR-REL-006/007).
- ADR-0004 becomes a *mechanism* rather than a promise: fabrication is blocked by code, and the
  Phase 19 zero-tolerance CI gate has something concrete to test.
- Slice S0 is unblocked from provider selection.
- Prompt injection defence is structural, not per-prompt.

### Negative / Costs
- Indirection: reading the code requires understanding the decorator chain.
- The port risks leaking provider-specific concepts and quietly losing its swappability
  (Risk R-31).
- The guard adds latency and complexity to every inference; grounding verification in particular
  is real work.
- A poorly-designed port is worse than none, because it gives false confidence about portability.

### Follow-up actions required
- **Phase 7:** prompt assembly maintains strict instruction/data channel separation; grounding
  and fabrication checks are specified in detail; every prompt is versioned.
- **Phase 10:** provider selection is constrained by ADR-0012 (no training on our data) as a hard
  elimination criterion, and the chosen provider must fit the existing port — the port does not
  bend to the provider.
- **Phase 12:** a lint or review rule forbids importing a provider SDK outside `packages/ai-port`.
- **Phase 19:** fabrication, injection, and schema-validity eval suites run against the guard as
  blocking CI gates.
- **To keep R-31 honest:** at least two adapters exist at all times (the fake counts), so the
  abstraction is continuously exercised rather than theoretical.
