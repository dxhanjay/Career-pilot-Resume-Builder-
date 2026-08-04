# ADR-0014: Modular monolith with machine-enforced boundaries, plus a separate worker tier

- **Status:** Accepted
- **Date:** 2026-07-31
- **Phase:** 4
- **Deciders:** Project owner + engineering team

## Context

The system must satisfy several requirements that pull in different directions:

- NFR-PERF-004 (60 s p95 analysis, 42% of it a third-party call) and NFR-REL-001 (jobs survive
  crash and deploy) rule out doing AI work inside a request handler.
- NFR-CAP-002 requires compute to scale on queue depth, independently of web traffic.
- ADR-0005 fixes the team at 1–2 part-time developers, and ADR-0009 sets a 99.5% SLO with no
  on-call rotation — both of which make operational surface area expensive.
- The project charter requires a modular monolith with microservice-ready boundaries.

The genuine risk in a monolith is not performance — it is **entropy**. A monolith without
enforced internal structure becomes a system where every change touches everything, typically
within six months. The usual promise ("we'll split it later") almost never materialises, because
by the time splitting is desirable, modules read each other's tables and share transactions, and
the split is a rewrite.

The opposite failure is equally real and more fashionable: adopting microservices at MVP,
producing distributed transactions, network failure modes, and N deployment pipelines for a team
that cannot operate them.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Single-process monolith, AI inline | Simplest possible | Cannot meet NFR-PERF-004 or NFR-REL-001; web tier pinned on network waits |
| Monolith + workers, no internal boundaries | Meets async requirements; simple | Degrades to a ball of mud; "split it later" never happens |
| **Modular monolith + worker tier, boundaries enforced in CI (chosen)** | One deployable; independent worker scaling; genuine extraction seams | Boundary discipline must be automated, not merely agreed |
| Microservices | Independent scale and deploy | Operational surface a part-time team cannot carry; distributed transactions; N pipelines |
| Serverless functions | Scales to zero | Cold starts on the interactive path; 60 s+ jobs strain limits; local development degrades |

## Decision

**A modular monolith serving requests, plus a separately-deployable worker tier consuming a
durable queue.** Both deploy from the same image with different entrypoints, so the code that
enqueues and the code that consumes can never drift.

Internal structure is organised into ten domain modules (`identity`, `ingestion`, `parsing`,
`analysis`, `matching`, `improvement`, `interview`, `credits`, `notification`, `admin`) plus a
shared kernel (`platform`, `ai-port`) containing no business logic.

**Four boundary rules, enforced by a CI lint rule — not by convention:**

1. A module may only be imported through its published `api` surface; internals are private.
2. **A module must not read or write another module's tables.** Data ownership is the property
   that makes future extraction possible; without it, boundaries are cosmetic.
3. Cross-module reads go through the published interface; cross-module writes go through
   asynchronous domain events (ADR-0016), so no distributed transaction is ever created.
4. The shared kernel may be imported by anyone and imports no domain module, keeping the
   dependency graph acyclic.

Within each module, Clean/Hexagonal layering applies: dependencies point inward, the domain
defines ports, infrastructure provides adapters.

**Enforcement is the decision.** A boundary that depends on developer memory is not a boundary;
`tools/boundary-lint` fails the build on violation, from slice S0.

## Consequences

### Positive
- Meets the latency, durability, and independent-scaling requirements without distributed-system
  complexity.
- One deployment pipeline, one runtime to operate, one place to debug — appropriate to team size.
- Extraction to services later is a mechanical change (in-process call → network call, move
  tables) rather than a rewrite, because modules already own their data and communicate
  asynchronously across boundaries.
- Clean layering keeps the scoring rubric free of infrastructure, which is what makes NFR-AI-003
  (σ ≤ 2) testable in CI without a provider key.
- A local environment remains a single compose file (NFR-MNT-003).

### Negative / Costs
- More upfront structure than a flat application; contributors must learn the module layout.
- The `api` surface of each module is extra indirection that occasionally feels like ceremony.
- Boundary lint will block merges on genuine but inconvenient violations (this is the point, and
  it will still be annoying).
- A single deployable means one module's memory leak can affect the whole API tier — accepted at
  this scale, and partly mitigated by the workers being separate.

### Follow-up actions required
- **Phase 5:** technology chosen must support a credible import-boundary lint and a shared image
  with multiple entrypoints.
- **Phase 6:** schema is organised per module; no foreign keys cross module boundaries — cross-module
  references are by ID with the relationship enforced in application code.
- **Phase 12/13:** route handlers stay thin — validate, call a use case, serialise.
- **Phase 14:** `boundary-lint` runs as a blocking CI gate from the first commit of S0.
- Revisit extraction only when at least two of the §17 triggers hold (see ADR-0018).
