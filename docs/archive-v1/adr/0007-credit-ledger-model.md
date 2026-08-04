# ADR-0007: Meter AI usage with an append-only credit ledger

- **Status:** Accepted
- **Date:** 2026-07-31
- **Phase:** 2
- **Deciders:** Project owner + engineering team

## Context

Phase 1 Risk R-03 identified inverted unit economics as a critical risk: our marginal cost
scales with *usage*, not with users. A single power user can consume many users' worth of
revenue, and a runaway loop or an abuser can drain an entire month's budget overnight.

Phase 1 §18 additionally classified **cost-based denial of service as a security concern**,
not merely a billing concern — an attacker who can trigger expensive AI calls without limit
can impose real financial damage without ever breaching anything.

Both problems have the same shape: **we need a hard, server-side bound on how much AI work
any principal can cause.**

A secondary concern is that credit systems are a well-known conversion killer when the UX is
opaque, which introduces R-15.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Flat unlimited per tier | Simplest to sell; no UX complexity | Unbounded cost liability; no throttle during spikes; one abuser breaks margin |
| Hard feature gates only | Simple; predictable | Bounds *which* features, not *how much* — the cost problem remains inside a tier |
| Pay-per-analysis | Perfectly aligned cost and revenue | Terrible UX; every action becomes a purchase decision; kills exploration |
| Mutable balance column | Trivial to implement | Cannot audit; disputes unresolvable; concurrent updates race; refunds corrupt history |
| **Append-only double-entry ledger (chosen)** | Auditable; correct under concurrency; refunds are entries not corrections; doubles as a security control | More schema and query complexity than a counter |

## Decision

We will meter AI-consuming actions with credits, recorded in an **append-only, double-entry
ledger**.

**Ledger rules**
1. Every grant, reservation, commitment, refund, and expiry is an immutable row. Rows are
   never updated or deleted.
2. **Balance is derived, never stored as a mutable column.** A materialised balance may exist
   as a cache, but the ledger is the source of truth.
3. Credits follow **reserve → commit → settle**: reserved when a job is enqueued, committed
   on success, **automatically refunded on failure or timeout.** A user never pays for our
   error.
4. Reservation and commitment are transactional with respect to job state, so a crash cannot
   leave credits consumed for work never performed.

**UX rules** (these are part of the decision, not decoration — they are the mitigation for
R-15)
1. The cost of an action is shown **before** the action. Never surprise-block mid-flow.
2. The interface speaks in **outcomes, not units**: "3 full analyses left this month", never
   "9 credits".
3. Re-running identical content (same resume hash + same JD hash) is **free**, because it is
   a cache hit and costs us nothing.

**Cost controls**
Per-account daily spend cap · per-IP and per-account rate limits · content-hash deduplication
· **global daily spend circuit breaker** that degrades to cheaper models and alerts when
platform-wide spend crosses a threshold.

## Consequences

### Positive
- Marginal cost per principal is hard-bounded, protecting gross margin (R-03).
- Cost-based DoS is structurally mitigated rather than monitored after the fact.
- Credits act as natural backpressure and load-shedding during seasonal spikes.
- A free tier becomes safe to offer generously, which matters because the free tier is our
  distribution channel (R-07).
- Full audit trail makes billing disputes resolvable.

### Negative / Costs
- More schema and query complexity than an integer counter.
- Credit UX must be designed carefully or it suppresses usage (R-15).
- Derived balances need a caching strategy to avoid summing the ledger on every request.

### Follow-up actions required
- **Phase 6:** ledger tables — `credit_grants`, `credit_entries` (append-only), plus a
  materialised balance with a documented invalidation strategy. Reservation rows carry the
  originating `job_id`.
- **Phase 12:** reserve/commit/refund is a transactional service-layer concern; entitlement
  checks are **server-side only** and never derived from client claims.
- **Phase 13:** cost-before-action UI, outcome-based language, balance always visible.
- **Phase 17:** the circuit breaker and daily caps are documented as security controls in the
  threat model.
- **Phase 18:** content-hash caching is a first-class cost lever, measured and reported.
- **Phase 20:** cost-per-feature and credits-refunded-due-to-failure are monitored SLIs; a
  rising refund rate is a reliability signal.
