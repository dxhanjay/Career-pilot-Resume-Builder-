# ADR-0018: Patterns deliberately not adopted, and the conditions that would change that

- **Status:** Accepted
- **Date:** 2026-07-31
- **Phase:** 4
- **Deciders:** Project owner + engineering team

## Context

Most architecture decision records document what a team *chose*. The decisions that cause more
trouble in practice are the ones a team *didn't* make and never wrote down — because six months
later someone proposes microservices, nobody remembers whether it was considered, and the
discussion restarts from zero.

This project is particularly exposed to that failure. It is an AI SaaS platform, a category where
the surrounding discourse strongly favours distributed architectures, Kubernetes, event sourcing,
and GraphQL. Every one of those will be proposed at some point, either by a collaborator, a
reviewer, or by the project owner reading an article.

Meanwhile the actual constraints are: 1–2 part-time developers (ADR-0005), a 99.5% SLO with no
on-call rotation (ADR-0009), a ~$150/month infrastructure budget, and a top risk register led by
scope explosion (R-01) and bandwidth (R-11).

**For this team, operational surface area is the scarcest resource** — scarcer than compute,
scarcer than money. Every additional system to run, monitor, patch, and debug is drawn from the
same budget that ships features.

## Options Considered

The alternative to writing this ADR is deciding each of these ad hoc when proposed. That
predictably produces inconsistent answers, repeated debates, and occasional adoption during a
moment of enthusiasm rather than on evidence.

## Decision

The following are **deliberately not adopted for MVP**, each with the condition that would
reopen it.

| Rejected | Why not now | Reopen when |
|---|---|---|
| **Microservices** | Distributed transactions, network failure modes, and N deployment pipelines for a 1–2 person team. ADR-0014 already provides the seams without the cost | At least **two** of: scaling conflict between modules, team > ~6 engineers with real merge contention, compliance-driven independent release cadence, one module repeatedly taking down others |
| **Event sourcing** | We need an audit *log*, not a rebuildable event history. Large conceptual overhead, difficult schema evolution, and the credit ledger already provides immutability exactly where it matters | A regulatory requirement to reconstruct arbitrary historical state, or a genuine temporal-query product need |
| **CQRS** | Reads are simple, low-volume, and served well by indexes. Two models to maintain for zero present benefit | Read load demonstrably outgrows the write model, and indexes plus caching have already been exhausted |
| **Kubernetes** | A cluster to operate, secure, upgrade, and debug — for roughly two services. Managed container hosting meets 99.5% at a fraction of the effort | Multi-service topology (i.e. after microservice extraction), or a hard requirement it uniquely satisfies |
| **GraphQL** | One first-party client with well-known access patterns. Adds N+1 risk, caching complexity, and query-cost attack surface | Third-party API consumers with genuinely divergent data needs (plausibly H4 partner API) |
| **Service mesh** | Nothing to mesh | After microservice extraction, if inter-service mTLS and traffic policy become real needs |
| **Multi-region active-active** | Not promised at a 99.5% SLO; would dominate the infrastructure budget | SLO raised to 99.95%+, or data-residency requirements from institutional customers |
| **Self-hosted model training infrastructure** | Explicit Phase 1 non-goal. We rent inference and own the rubric, the evals, and the parse-failure corpus | A fine-tuned model demonstrably outperforms prompting on our own evals **and** unit economics justify it (H3 at the earliest) |
| **Saga orchestration** | There are no distributed transactions to coordinate; the outbox plus idempotent consumers covers our consistency needs | After service extraction, if a genuine multi-service workflow with compensations appears |
| **Separate vector database** | Corpus is small; one more system to operate | Index size or query latency demonstrably exceeds what the primary datastore handles |

**Standing rule:** adopting any item above requires a new ADR that supersedes the relevant row,
states which reopening condition has been met, and states the evidence. "It seems better" is not
evidence.

## Consequences

### Positive
- Prevents repeated re-litigation of settled questions, with a written answer to point at.
- Keeps operational surface proportional to team size, protecting the scarcest resource.
- Makes adoption criteria **objective and falsifiable**, so future decisions rest on measurements
  rather than fashion.
- Documents that these were considered and rejected on reasoning — not overlooked — which matters
  for the project's credibility to any reviewer.

### Negative / Costs
- Some rejected patterns would genuinely help *later*; the reopening conditions must actually be
  monitored, or the rejections silently harden into dogma.
- A team accustomed to these tools may perceive the architecture as unfashionable or
  under-engineered.
- Deferred adoption means paying a migration cost later that early adoption would have avoided —
  accepted deliberately, because most of these migrations never become necessary, and paying for
  all of them upfront is certain waste against uncertain benefit.

### Follow-up actions required
- Review the reopening conditions at each horizon gate (H1 → H2 → H3), not continuously.
- **Phase 21 (Scaling)** must check these conditions explicitly against real measurements rather
  than re-deriving the arguments.
- Any proposal to adopt one of these arrives as a superseding ADR citing the met condition and
  the supporting evidence.
