# ADR-0008: Sell a 90-day Job Search Pass, not a monthly subscription

- **Status:** Accepted (pricing levels provisional until validated)
- **Date:** 2026-07-31
- **Phase:** 2
- **Deciders:** Project owner + engineering team

## Context

Phase 1 §6 established a structural fact about this market: **job searching is episodic.**
Users have intense need for one to three months, then essentially zero need until their next
search — possibly years later. Phase 1 recorded this as Risk R-08, structural churn built
into the business model.

Standard SaaS practice is a monthly subscription with automatic renewal. Applied to episodic
demand, that model has three specific problems:

1. We pay acquisition cost to win a customer who **intends** to leave in eight weeks, so
   lifetime value is compressed by design.
2. The purchase decision carries cancel-anxiety — "will I remember to cancel?" — which
   suppresses conversion at exactly the moment of highest intent.
3. Revenue from users who forgot to cancel is revenue from a user who feels cheated. It
   produces refund requests, chargebacks, and reviews that say so.

Phase 1 also defaulted to India-first pricing (ADR-0005), where consumer SaaS ARPU is
materially lower than US benchmarks: Jobscan's ~$50/month is roughly ₹4,200/month, which
converts near zero for persona P1 and poorly for P2.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Monthly subscription only | Industry default; predictable MRR | Fights the demand shape; cancel-anxiety at purchase; forgot-to-cancel revenue is toxic |
| One-time lifetime purchase | No churn concept; simple | No recurring revenue; underprices heavy users; mismatched to ongoing AI cost |
| Pure pay-per-analysis | Perfect cost alignment | Every click is a purchase decision; kills exploration and the habit loop |
| **90-day pass as headline, monthly available (chosen)** | Matches the real cycle; higher effective ARPU per customer; removes cancel-anxiety; honest | Lumpier revenue recognition; less clean MRR reporting |

## Decision

The **90-day "Job Search Pass" is the headline, default, best-value product.** Monthly
remains available for users who prefer it, but is not the default and is not the most
prominent option.

Provisional levels (`[TO VERIFY]` — to be validated against observed consumption in beta,
not set in stone here):

| Plan | India | Global |
|---|---|---|
| Free | ₹0 | $0 |
| Pro — Monthly | ₹599 | $12 |
| ⭐ Pro — 90-day Job Search Pass | **₹1,299** | **$25** |
| Institution (H3) | Custom per seat | Custom per seat |

Regional pricing is implemented from the first paid release rather than retrofitted.

Two constraints accompany this decision:
- **The free tier gives the complete diagnosis.** We do not paywall the ATS score after
  making a user upload their resume — that is a dark pattern that destroys the trust the
  entire USP rests on, and it kills word of mouth (R-07).
- **Prices are provisional.** They are validated by instrumenting `credit_wall_hit` and
  observed consumption during beta, not by conviction.

## Consequences

### Positive
- Pricing matches how the product is actually used, so the offer is honest.
- Removes the largest objection at the point of purchase.
- Converts the business model's structural weakness (R-08) into a coherent product framing.
- Higher effective ARPU per acquired customer than a subscription they cancel at month two.
- Regional pricing from day one makes global expansion a configuration change.

### Negative / Costs
- Revenue is lumpier and MRR-style reporting is less clean.
- Renewal is an explicit repurchase decision rather than passive continuation.
- Billing implementation must handle fixed-term entitlements, not just recurring
  subscriptions — a slightly less common path in payment providers.

### Follow-up actions required
- **Phase 6:** subscription schema models **fixed-term entitlements with an explicit expiry**,
  not only recurring plans. Currency and region are first-class fields.
- **Phase 12:** entitlement checks read term validity, not merely "is subscribed".
- **Phase 13:** pricing page presents the 90-day pass as the default choice.
- **Phase 17:** hosted checkout only — we never handle card data, keeping us outside PCI-DSS
  scope.
- **Beta:** instrument `credit_wall_hit` and consumption distribution before fixing prices.
- Revisit this ADR once real consumption data exists; supersede rather than edit.
