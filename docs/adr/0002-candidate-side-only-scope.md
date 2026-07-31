# ADR-0002: Build candidate-side only; no employer screening

- **Status:** Accepted
- **Date:** 2026-07-31
- **Phase:** 1
- **Deciders:** Project owner + engineering team

## Context

The platform analyses resumes and evaluates interview answers. The same technology can be
pointed in two opposite directions:

- **Candidate-side:** the job seeker uses it on their own data to improve.
- **Employer-side:** a company uses it to screen, rank, or reject real applicants.

These are technically similar and legally worlds apart. Employer-side AI used in hiring
decisions falls into elevated regulatory categories in several jurisdictions — for example
the EU AI Act treats AI systems used for recruitment and candidate evaluation as high-risk,
and New York City Local Law 144 requires an independent annual bias audit and candidate
notification for automated employment decision tools. Those regimes bring conformity
assessments, documented risk management, bias auditing, human-oversight duties, and
meaningful liability for discriminatory outcomes.

The master prompt lists a "Recruiter Panel" as an optional feature, which makes this
boundary a decision that must be taken deliberately rather than drifted into.

> This is an engineering risk assessment, not legal advice. Any move toward employer-side
> functionality requires review by a qualified lawyer in each target jurisdiction.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| **Candidate-side only (chosen)** | Minimal regulatory burden; user is the data subject *and* the beneficiary; clear ethical story; simpler consent model | Forgoes the higher-ARPU employer market |
| Both from the start | Two revenue streams; recruiter-side pays far more | Compliance cost dwarfs a small team's capacity; conflicting incentives (we would advise candidates *and* filter them); a bias incident is existential |
| Employer-side only | Highest willingness to pay | Wrong for our stated problem; crowded by funded incumbents; maximum liability |
| Candidate-side now, employer-side later behind legal review | Keeps the option open | Requires holding architectural discipline; risk of scope creep |

## Decision

We will build **candidate-side only**. The user analysing a resume is always the person
that resume belongs to.

We will **not** build features that let a third party screen, rank, score, or reject real
job applicants. The "Recruiter Panel" is deferred to Horizon 4 and is gated on an explicit
legal review and a documented bias audit — it is not a normal backlog item.

Institution/cohort features (Horizon 3) are permitted only where students are the users and
the institution sees **aggregate readiness data**, never automated accept/reject decisions.

## Consequences

### Positive
- Stays outside the highest-risk regulatory categories for hiring AI.
- Product incentives align with the user: we succeed when the candidate succeeds.
- Simpler data protection model — one data subject, clear consent, clear erasure path.
- Marketing position: "we're on the candidate's side" is credible because it's structural.

### Negative / Costs
- Forgoes the larger B2B recruiting-tools market.
- Revenue depends on consumers with episodic, low willingness to pay (Risk R-08).
- Institution features must be carefully designed to stay on the right side of the line.

### Follow-up actions required
- **Phase 2:** recruiter panel marked H4-gated, not MVP.
- **Phase 3:** requirements assert candidate ownership of all analysed data.
- **Phase 11:** fairness and bias evaluation still required — we advise people, and bad
  advice can be discriminatory even without a hiring decision attached.
- **Phase 17:** terms of service must prohibit employer use of candidate-side outputs for
  screening.
