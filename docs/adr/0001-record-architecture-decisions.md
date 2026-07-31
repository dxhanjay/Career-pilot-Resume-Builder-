# ADR-0001: Record architecture decisions in ADRs

- **Status:** Accepted
- **Date:** 2026-07-31
- **Phase:** 1
- **Deciders:** Project owner + engineering team

## Context

This project spans 22 design and delivery phases, from problem definition to maintenance.
Decisions taken in early phases (scope, stack, data model) constrain every later phase.
Two failure modes are likely without a written record:

1. **Contradiction** — a later phase makes a choice incompatible with an earlier one,
   discovered only at integration or deployment time.
2. **Amnesia** — the *reason* for a constraint is lost, so it is either cargo-culted
   forever or discarded without understanding what it protected.

Operating Rule 10 of the project charter explicitly requires a running decision record.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| **ADRs in-repo (Nygard format)** | Versioned with code; diffable; reviewable in PRs; greppable; industry standard; zero cost | Requires discipline to write |
| Wiki / Notion page | Rich editing, easy linking | Drifts from code; no review gate; access dies with the tool |
| Chat history / meeting notes | Zero effort | Unsearchable, unstructured, effectively lost |
| Code comments only | Colocated with implementation | Cannot record *rejected* options or cross-cutting decisions |
| No record | Fastest today | Guarantees both failure modes above |

## Decision

We will record every significant decision as a numbered, immutable ADR in `docs/adr/`,
using the Nygard format defined in `docs/adr/README.md`.

A decision is significant if reversing it would cost more than a day of work, affects more
than one module, or changes the product's legal or ethical posture.

## Consequences

### Positive
- Any later phase can be traced back to the reasoning that produced it.
- Rejected options are preserved, so debates are not re-litigated.
- New contributors onboard by reading the log rather than interrogating the author.
- Satisfies the project's documentation deliverable requirement.

### Negative / Costs
- ~15 minutes of writing per significant decision.
- Requires discipline; an abandoned ADR log is worse than none, because it misleads.

### Follow-up actions required
- Every phase from 2 onward must produce at least one ADR for its headline decisions.
- The index table in `docs/adr/README.md` must be updated with each new record.
