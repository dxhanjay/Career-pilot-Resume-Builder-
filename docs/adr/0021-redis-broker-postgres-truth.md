# ADR-0021: Redis as the message broker, because PostgreSQL already holds job truth

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 5
- **Deciders:** Project owner + engineering team

## Context

The architecture needs a way to deliver work from the API to the worker tier. The instinctive
approach is to choose the broker with the strongest durability guarantees, since losing a job
would strand credits and leave a user watching a spinner forever.

But two earlier decisions have already absorbed that requirement:

- **Phase 4 §9.1** puts the job state machine in the database. State, attempt count, lease owner,
  lease expiry, and idempotency key are PostgreSQL rows. A worker that dies has its lease expire,
  and the job returns to `queued` automatically.
- **ADR-0016** adds the transactional outbox. The event is written in the same transaction as the
  state change, and a relay publishes it. A message lost between relay and broker leaves the
  outbox row unpublished, and the scheduler's sweep republishes it.

**Therefore the broker is a delivery transport, not the durability guarantee.** Durability is
already solved, twice, in PostgreSQL. A heavyweight broker would be paying a second time for a
property we already have.

This reframing is what makes the decision, and it is worth recording because the reasoning is not
obvious from the broker comparison alone.

Separately, Phase 4 §13 already requires Redis for three other purposes: session caching,
rate-limit counters, and the AI response cache. Redis is therefore not a new dependency — it is a
service we are already running.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| **Redis (chosen)** | **Zero new services** — already required for cache, sessions, rate limits; fast; simple; supported by every Python queue library | Weaker delivery durability than a real broker — acceptable, since PostgreSQL holds the truth |
| RabbitMQ | Proper AMQP: priority queues, dead-letter exchanges, publisher confirms, durable queues | One more service to run, monitor, patch, and back up (criterion C3); its durability duplicates what PostgreSQL already provides |
| Kafka | Very high throughput; replayable log; partitioning | **Wrong tool for the job.** We need task distribution, not an event log. Brokers, KRaft/ZooKeeper, and topic management are heavy operations for ~500 jobs/day at peak. Adopting it would be architecture-by-résumé |
| Managed SQS / Cloud Tasks | Durable, DLQ built in, no operations | Couples us to one cloud early, against the portability discipline in ADR-0022; extra latency; another billing dimension |
| Database-as-queue (poll the jobs table) | No broker at all | Loses priority-queue semantics needed by NFR-CAP-004; polling cost grows with table size |

**Worker library**, evaluated separately:

| Option | Pros | Cons |
|---|---|---|
| **Dramatiq (chosen)** | Sane defaults; **supports Redis *and* RabbitMQ behind the same API**, making the upgrade path a config change; built-in retries with backoff, DLQ, middleware hooks | Smaller community than Celery |
| Celery | Largest ecosystem; battle-tested | Configuration sprawl; well-known reliability footguns (visibility timeout, late-ack); weak async support |
| arq | Async-native, lightweight | Less mature; fewer hooks; our heaviest work is CPU-bound parsing, where async buys little |
| RQ | Simplest | **No priority queues — fails NFR-CAP-004** (free must not starve Pro) |

## Decision

**Dramatiq on Redis**, with separate priority queues per plan tier to satisfy NFR-CAP-004.

PostgreSQL remains the system of record for all job state. Redis carries messages and holds only
losable data — cache entries, rate-limit counters, session copies, and in-flight queue messages.

**Recovery layers, in order:** the job's lease expires and it re-queues; the outbox sweep
republishes anything unpublished beyond a threshold; the reconciliation job refunds any
reservation whose job reached a terminal state (NFR-REL-005).

**RabbitMQ is the documented upgrade path.** Because Dramatiq abstracts the broker, migrating is a
configuration change rather than a rewrite. The trigger is a measured need for AMQP routing
semantics, per-message TTLs, or delivery guarantees that the recovery layers above prove
insufficient to cover.

## Consequences

### Positive
- **No new service.** Redis was already required, so the broker costs nothing additional to run,
  monitor, or back up — directly serving criterion C3 and the ≤$150/month ceiling (NFR-COST-005).
- Priority queues satisfy NFR-CAP-004 without RabbitMQ's operational weight.
- Broker choice is reversible by configuration, so the decision is low-regret.
- Redis's simplicity means fewer failure modes to understand for a team of two.
- Kafka's rejection is recorded with reasoning, so it does not get re-proposed on general
  enthusiasm.

### Negative / Costs
- Redis persistence is weaker than a durable broker's; an unclean Redis failure can drop in-flight
  messages. **This is only tolerable because of the recovery layers** — if any of them were
  removed, this decision would become wrong.
- Redis is now carrying four responsibilities; a Redis outage degrades cache, sessions, rate
  limiting, and job delivery simultaneously. Blast radius is wider than with separate systems.
- Memory pressure from the AI response cache could interact with queue depth under load; both
  need monitoring.
- Redis's licence changed to RSALv2/SSPL in recent years. Unaffected here (unmodified managed
  service, not redistributed), with **Valkey** as a drop-in BSD alternative if it ever matters.

### Follow-up actions required
- **Phase 6:** `jobs` table carries state, attempts, `idempotency_key`, `lease_owner`,
  `lease_expires_at`; `outbox` indexed on unpublished rows.
- **Phase 12:** every job handler is idempotent by key — at-least-once delivery makes this
  mandatory, not optional (NFR-REL-002).
- **Phase 14:** Redis configured with appropriate persistence; separate logical databases (or key
  prefixes) for cache, sessions, rate limits, and broker so eviction policy for one cannot evict
  another.
- **Phase 19:** chaos test kills Redis mid-flight and asserts the outbox sweep and lease expiry
  recover every job exactly once, with no double charge.
- **Phase 20:** monitor queue depth, job age, retry rate, DLQ depth, **outbox relay lag**, and
  Redis memory — the last two are the early-warning signals for this decision going wrong.
