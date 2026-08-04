# ADR-0009: Target 99.5% availability with an explicit error budget

- **Status:** Accepted
- **Date:** 2026-07-31
- **Phase:** 3
- **Deciders:** Project owner + engineering team

## Context

An availability target must be set before Phase 4, because it is one of the two or three
inputs that most strongly determine architecture and cost. Every additional "nine" costs
roughly an order of magnitude more engineering and infrastructure:

| SLO | Downtime/month | What it typically requires |
|---|---|---|
| 99.0% | 7.2 h | A single server and hope |
| 99.5% | 3.6 h | Managed services, health checks, quick manual recovery |
| 99.9% | 43 min | Multi-AZ, automated failover, alerting with response |
| 99.95% | 22 min | On-call rotation, tested runbooks, zero-downtime deploys |
| 99.99% | 4.4 min | Multi-region, follow-the-sun on-call, extensive automation |

The team is 1–2 developers working part-time (ADR-0005), with a budget ceiling of roughly
$150/month at MVP. There is no on-call rotation and there will not be one during H1.

The failure mode to avoid is publishing a number we cannot honour. A stated 99.99% that is
actually 99.2% is worse than an honest 99.5%: it produces support obligations we cannot meet,
and it drives architectural decisions (multi-region, active-active) whose cost the project
cannot absorb.

There is a second, less obvious consideration. An error budget is not only a *limit* — it is
a **permission**. Explicitly allowing 3.6 hours of unavailability per month means the MVP does
not need blue-green deployments, zero-downtime migrations, or connection draining. That is a
substantial amount of engineering the project can legitimately defer.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| No stated target ("best effort") | No commitment to breach | No design input; reliability work never gets prioritised; can't tell good from bad |
| 99.9% at MVP | Credible-sounding; industry-familiar | Requires multi-AZ + automated failover + alert response the team cannot staff; consumes budget better spent on the product |
| **99.5% at MVP, 99.9% at H2 (chosen)** | Honest and achievable; leaves budget for deploys; escalates when payments make it matter | Less impressive on a marketing page |
| 99.99% | Enterprise-grade | Fantasy for a part-time team; would distort the entire architecture |

## Decision

**MVP availability SLO: 99.5% monthly for the core API** (≈3.6 h/month of allowed downtime),
measured by external synthetic probes from three regions at one-minute intervals.

Supporting targets: analysis-pipeline success ≥ 99.0%, RTO ≤ 4 h, RPO ≤ 1 h.

Two policies accompany the number:

1. **Degraded mode is a requirement, not a nicety.** If the AI provider is unavailable, upload,
   parsing, history, and dashboard MUST remain functional (NFR-AVL-004). Availability is
   measured per capability, not as a single binary.
2. **Error budget policy:** when the monthly budget is exhausted, feature work pauses and
   reliability work takes priority until the following period. This makes reliability
   self-correcting rather than dependent on someone remembering to care.

**The target rises to 99.9% at Horizon 2**, gated on payments going live — taking money
changes the obligation.

## Consequences

### Positive
- The architecture in Phase 4 can be sized honestly: managed services and a single region are
  sufficient for MVP, and the money saved goes into the product.
- Zero-downtime deployment machinery is legitimately deferred, because the budget covers it.
- The error budget gives an objective trigger for when to stop building and start hardening.
- We publish a number we can actually meet.

### Negative / Costs
- 3.6 h/month is visible to users during a bad month.
- Enterprise or institutional buyers in H3 may require better; the H2 escalation exists partly
  for this reason.
- Requires genuine external measurement — an SLO measured from inside the system measures
  nothing.

### Follow-up actions required
- **Phase 4:** single-region, multi-AZ-optional architecture; degraded-mode paths designed so
  the read path does not depend on the inference path.
- **Phase 14:** deployment strategy may take brief downtime; blue-green deferred to H2.
- **Phase 16:** cloud sizing reflects 99.5%, not higher.
- **Phase 20:** external uptime probes, SLO dashboard, and error-budget burn alerting.
- **Phase 22:** DR drill quarterly against the RTO/RPO targets; monthly restore test.
- Revisit at H2 when payments launch; supersede rather than edit.
