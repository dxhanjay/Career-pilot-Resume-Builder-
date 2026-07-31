# ADR-0016: Publish domain events via a transactional outbox

- **Status:** Accepted
- **Date:** 2026-07-31
- **Phase:** 4
- **Deciders:** Project owner + engineering team

## Context

Enqueuing work requires two effects that must happen together: **persist state** (the resume
row, the job row, the credit reservation) and **publish a message** so a worker picks it up.
These live in two different systems with no shared transaction.

Both naive orderings are broken:

```
# Order A
tx.commit()            # resume, job, credit reservation saved
queue.publish(msg)     # ← process dies here
# Result: job exists, no worker will ever run it. Credits stay reserved.
#         The user watches a spinner forever.

# Order B
queue.publish(msg)     # worker may start immediately
tx.commit()            # ← fails
# Result: a worker processes a job whose state was never persisted.
```

This is the dual-write problem. It is not a rare edge case: it fires on every deploy that
restarts a process mid-request, every OOM kill, and every spot-instance reclaim. The resulting
bugs are intermittent, unreproducible locally, and extremely time-consuming to diagnose — the
symptom is "the analysis just never finished", with nothing in the logs.

It matters more here than in a typical CRUD application because credits are reserved in the same
transaction (ADR-0007). A lost message means a user's balance is held against work that will
never run.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Publish after commit, accept the gap | Trivial | Silent job loss on crash; violates NFR-REL-001; reserved credits stranded |
| Publish before commit | Trivial | Workers process phantom jobs; worse than option 1 |
| Two-phase commit across DB and broker | Formally correct | Rarely supported by managed brokers; heavy; poor failure behaviour |
| **Transactional outbox (chosen)** | Atomic with the state change; one table and one relay loop; broker-agnostic | Requires a relay process; delivery is at-least-once, so consumers must be idempotent |
| Database-as-queue (poll the jobs table) | No broker at all; simplest | Loses priority queues, DLQ semantics, and managed scaling; polling load grows with table size |
| Change-data-capture from the DB log | No application changes | Operationally heavier; more moving parts than the problem warrants at this scale |

## Decision

**Domain events are written to an `outbox` table inside the same database transaction as the
state change they describe. A relay process publishes unpublished rows to the message queue and
marks them sent.**

```
BEGIN
  insert resume
  insert job (state = created)
  insert credit reservation
  insert outbox row (event = job.created)
COMMIT                       ← all four, or none

[outbox relay]  poll unpublished → publish to queue → mark published
```

Consequences that follow directly and are therefore part of this decision:

1. **Delivery is at-least-once.** A crash between publishing and marking-published republishes
   the message. Therefore **every consumer must be idempotent**, using the job's idempotency key
   (NFR-REL-002). At-least-once delivery plus idempotent consumers gives effectively-once
   behaviour without distributed transactions.
2. **Relay lag is a monitored SLI.** If the relay stalls, jobs silently stop starting. Lag and
   unpublished-row age are alerted on (Risk R-27).
3. **The scheduler sweeps** outbox rows older than a threshold that are still unpublished, as a
   backstop against a wedged relay.
4. The same mechanism carries **cross-module writes** (ADR-0014 rule 3), so no distributed
   transaction is ever created inside the monolith — which is precisely what keeps future service
   extraction mechanical.

## Consequences

### Positive
- Eliminates an entire class of "the job vanished" bugs, which are among the most expensive to
  diagnose in production.
- Credits can never be stranded by a lost message, because the reservation and the event commit
  together.
- Cross-module communication is asynchronous from day one, so extracting a module later does not
  require unpicking shared transactions (ADR-0014, §17).
- Broker-agnostic: the queue technology chosen in Phase 5 can change without touching business
  logic.
- The outbox table doubles as a debuggable, queryable record of what the system intended to
  publish.

### Negative / Costs
- One extra table, one background relay process to run and monitor.
- Consumers must be written idempotently — a discipline, and a source of subtle bugs if forgotten.
- Adds a small, bounded latency between commit and message availability (relay poll interval).
- The outbox table needs pruning, or it grows without limit.

### Follow-up actions required
- **Phase 5:** the chosen datastore must support the transactional guarantees this relies on;
  the queue must support at-least-once delivery with a DLQ.
- **Phase 6:** `outbox` table with `id, aggregate, event_type, payload, created_at,
  published_at`, indexed on unpublished rows; retention/pruning policy defined.
- **Phase 12:** every job consumer is idempotent by key; a duplicate delivery returns the stored
  result and performs no work and no charge.
- **Phase 19:** a chaos test kills the process between `COMMIT` and publish, and asserts the job
  still runs exactly once with no double charge.
- **Phase 20:** relay lag, unpublished-row age, and publish failure rate are dashboarded and
  alerted.
