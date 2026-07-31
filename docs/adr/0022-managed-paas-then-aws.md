# ADR-0022: Deploy on managed PaaS (Render) for MVP, with AWS ap-south-1 as a triggered migration

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 5
- **Deciders:** Project owner + engineering team

## Context

The Phase 4 deployment topology needs: a web service, a background worker tier that autoscales on
queue depth, a scheduled task runner, managed PostgreSQL with the `pgvector` extension, managed
Redis, object storage on a separate origin, and a CDN.

The constraints that shape where this runs are not technical capability — every major provider can
host it — but **operational cost measured in a part-time team's hours**:

- 1–2 developers, part-time (ADR-0005)
- 99.5% SLO with no on-call rotation (ADR-0009)
- ≤ $150/month infrastructure and AI budget (NFR-COST-005)
- Phase 5 criterion C3 weights operational burden at 20%

On a hyperscaler, the topology above is a VPC, subnets, security groups, an ALB, ECS task
definitions, RDS, ElastiCache, EventBridge schedules, and a substantial IAM policy set. On a
managed PaaS it is a web service, a worker, a cron job, a database, and a Redis instance —
primitives that map one-to-one onto our components.

**That difference is measured in weeks of a very small team's time.** Weeks spent on
infrastructure at MVP are weeks not spent on parsing quality — which, per R-06 and the Phase 4
dependency graph, is what actually determines whether this product works at all.

There is a countervailing consideration that must not be glossed over. We are India-first
(ADR-0005). Render's nearest region is Singapore. That is acceptable for latency and, on current
reading, acceptable legally — but it would stop being acceptable if an institutional customer
(Horizon 3) requires India-resident data, or if DPDP obligations tighten for our category. AWS
`ap-south-1` (Mumbai) is the answer in that case.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| **Render (chosen for MVP)** | Primitives map 1:1 onto our components (web + worker + cron + Postgres/pgvector + Redis); private networking; predictable pricing; minimal operations | Fewer regions; nearest is Singapore, not India; less control; scaling ceiling above MVP but below enterprise |
| Railway | Excellent DX; similar component model | Less mature networking and compliance posture |
| Fly.io | Strong global distribution and latency control | More DIY; Postgres is closer to self-managed |
| DigitalOcean App Platform | Inexpensive; managed DB and Redis | Weaker worker and cron ergonomics |
| **AWS ap-south-1** | Everything at any scale; **India region** for latency and residency; mature compliance artefacts | Materially higher operational burden — the multi-week setup described above |
| GCP Cloud Run | Excellent for stateless containers; strong data tooling | Request-scoped model fits long-running workers less naturally; smaller India footprint than AWS |
| Azure | Strong enterprise and compliance story | Weakest developer experience for this workload; no offsetting advantage for us |
| Vercel | Best-in-class for Next.js | Frontend only — we still need somewhere for Python, PostgreSQL, Redis, and workers |

## Decision

**Deploy on Render for MVP.** Web service, worker, and scheduler run from the shared Docker image
(ADR-0014); PostgreSQL with `pgvector` and Redis are managed. Cloudflare provides CDN, WAF, DNS,
and R2 object storage on a separate origin (NFR-SEC-010).

**AWS `ap-south-1` is the documented migration destination**, triggered — not scheduled — by any
of:

| Trigger | Why it forces the move |
|---|---|
| **An institutional customer requires India-resident data** | Contractual; no workaround on Render |
| **DPDP or sector obligations mandate local residency** | Legal |
| Render's service tiers cannot meet measured load or the SLO | Capability |
| The 99.9% SLO at Horizon 2 requires multi-AZ guarantees Render cannot provide | ADR-0009 escalation |
| Costs at scale exceed AWS equivalents by a sustained, measured margin | Economics |

**Portability discipline — this is what makes the decision low-regret.** We use **no
Render-proprietary API**. Every component is a Docker container configured entirely through
environment variables (12-factor). PostgreSQL, Redis, and S3-compatible storage are standard
interfaces. The migration is bounded, real work — not a rewrite.

## Consequences

### Positive
- Setup is hours rather than weeks, and that time goes into the parsing quality that determines
  product viability.
- Fewer moving parts to secure, patch, and debug — appropriate to a team with no on-call rotation
  and a 99.5% SLO.
- Managed backups, PITR, and TLS come as defaults rather than as configuration we must get right.
- Cost is predictable and fits the MVP budget (Phase 5 §22), with no surprise egress or NAT
  gateway charges.
- Cloudflare R2's zero egress fees materially reduce the cost of serving resume files and PDFs.
- The migration trigger is written down, so residency arrives as a planned project rather than a
  crisis during a sales conversation.

### Negative / Costs
- **Data sits in Singapore, not India.** Acceptable now; a known liability for the H3
  institutional channel, and the primary reason the trigger list exists.
- A future migration is real work — IaC to write, a database to move, a cutover to plan. Bounded
  by the portability discipline, but not free.
- Less control over networking, scaling policy, and instance tuning than a hyperscaler offers.
- Dependence on a smaller provider's own reliability, which is part of our error budget whether we
  like it or not.

### Follow-up actions required
- **Phase 14:** infrastructure defined in a single `infra/render.yaml` manifest, kept in version
  control. **No configuration lives only in the provider's dashboard.**
- **Phase 14:** CI builds one image and deploys all three entrypoints from it.
- **Phase 16:** an AWS `ap-south-1` target architecture is sketched *before* it is needed, so the
  trigger has a plan attached rather than a blank page.
- **Phase 16:** confirm the managed PostgreSQL offering includes `pgvector` — this is a hard
  requirement from ADR-0020.
- **Phase 17:** confirm the sub-processor list (FR-PRIV-007) names the hosting region accurately,
  and that the privacy notice states where data is processed.
- **Horizon 3:** re-evaluate before any institutional contract is signed, not after.
