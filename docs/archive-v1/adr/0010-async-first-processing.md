# ADR-0010: All AI work is asynchronous; the API never blocks on a model call

- **Status:** Accepted
- **Date:** 2026-07-31
- **Phase:** 3
- **Deciders:** Project owner + engineering team

## Context

NFR-PERF-004 sets a p95 of 60 seconds for a full resume analysis. The latency budget
decomposition shows where that time goes:

```
Full analysis p95: 60 000 ms
├─ our own work (validate, extract, sections, score, persist) ... 19 000 ms
├─ queue wait ..................................................   5 000 ms
├─ LLM analysis call ........................................... 25 000 ms  ← 42%, not ours
└─ retry headroom ..............................................  11 000 ms
```

**Forty-two percent of the budget is a third-party network call we do not control**, whose
latency distribution has a long and unpredictable tail, and which can fail or rate-limit us at
any time.

Attempting this synchronously inside an HTTP request handler fails in several ways at once:
request timeouts at the load balancer and CDN (commonly 30–60 s), a worker thread or
connection pinned for the entire duration, no way to retry without the user resubmitting, work
lost entirely on deploy or crash, and a spike in concurrent uploads exhausting the web tier
while it sits idle waiting on the network.

NFR-REL-001 additionally requires that enqueued work survive worker restart, deploy, and
crash — which a request-scoped operation cannot satisfy by construction.

Phase 2 already assumed this shape (slice S0 exists specifically to prove the async pipeline
before any AI is involved). This ADR records it as a binding architectural constraint rather
than an implementation detail, because several later phases will be tempted to violate it for
"just this one quick call".

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Synchronous request/response | Simplest code; no job state to model | Times out at the edge; pins connections; no retry; work lost on deploy; cannot meet NFR-REL-001 |
| Synchronous with streaming response | Feels fast; progress visible | Connection still held for the full duration; retry and durability problems remain; complicates the CDN and proxy layer |
| **Async job + polling / push notification (chosen)** | Survives crashes and deploys; retryable; independently scalable workers; natural backpressure | Job state must be modelled; the client needs a progress UX; more moving parts |
| Async for analysis, sync for "small" AI calls | Less machinery for cheap operations | The exception becomes the rule; "small" calls are the ones that surprise you under load |

## Decision

**Every operation that invokes a model is asynchronous. No HTTP request handler ever waits on
an inference call.**

The contract:

1. Work-initiating endpoints return **`202 Accepted`** with a job identifier and a status URL.
   They never return the result inline.
2. Job state is **durably persisted before the response is returned**, so an immediate crash
   loses nothing.
3. Workers consume from a queue and are scaled independently of the API tier.
4. Delivery is **at-least-once**, so every job carries an idempotency key; reprocessing a job
   must not double-charge credits or duplicate results (NFR-REL-002).
5. Failures retry with **exponential backoff and jitter**, three attempts, then a dead-letter
   queue that is alerted on (NFR-REL-003/004).
6. Provider calls carry a timeout **below** their stage budget (≤ 20 s) with a circuit breaker
   on sustained failure (NFR-REL-006/007).
7. Credits follow reserve → commit → refund tied to job lifecycle (ADR-0007), so a failed job
   never costs the user.
8. The client shows **real progress and a plain-language failure cause**, never an indefinite
   spinner (FR-UPL-010).

This applies to *all* model-invoking work — analysis, matching, rewriting, question
generation, and answer scoring — with no "small call" exemption.

## Consequences

### Positive
- Meets the latency, reliability, and durability NFRs by construction rather than by luck.
- Workers scale on queue depth independently of web traffic (NFR-CAP-002).
- The queue is natural backpressure and load-shedding during seasonal spikes.
- Priority queues become possible, so free-tier load cannot starve Pro (NFR-CAP-004).
- Provider outages degrade gracefully: jobs queue rather than erroring (NFR-AVL-004).
- Slice S0 can prove the entire pipeline with a fake analyser, de-risking the hardest
  infrastructure before any AI exists.

### Negative / Costs
- Job state, status endpoints, and progress UX must be built — this is most of S0's work.
- The client is more complex than a simple request/response call.
- Debugging spans process boundaries, which is why distributed tracing is NFR-OBS-002.
- Local development needs a queue and worker running, raising setup cost (mitigated by
  NFR-MNT-003's one-command environment).

### Follow-up actions required
- **Phase 4:** API tier, queue, and worker tier as distinct components; job lifecycle state
  machine defined.
- **Phase 6:** `jobs` table with status, attempts, idempotency key, timestamps, and a link to
  the credit reservation.
- **Phase 12:** the `202` + status-URL contract is documented in OpenAPI; no endpoint may
  return an inference result inline.
- **Phase 13:** progress component with live-region announcements (NFR-A11Y-007) and plain
  failure messaging.
- **Phase 19:** chaos test — kill a worker mid-job and assert no loss, no double-charge, and a
  refunded reservation.
- **Phase 20:** queue depth, job age, retry rate, and DLQ depth are dashboarded and alerted.
