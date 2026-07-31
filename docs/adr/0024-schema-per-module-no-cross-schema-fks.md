# ADR-0024: One PostgreSQL schema per module, with no foreign keys across schemas

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 6
- **Deciders:** Project owner + engineering team

## Context

ADR-0014 established a modular monolith whose defining rule is that **modules own their data** —
no module reads or writes another module's tables. That rule is what makes future extraction to
services mechanical rather than a rewrite, and it is enforced in application code by
`import-linter`.

But import linting only constrains Python. If all tables live in one flat namespace with foreign
keys crossing freely, the database itself becomes the place where boundaries dissolve: a query in
the `analysis` module can join straight into `ingestion` tables, a foreign key creates a hard
coupling that any future extraction must unpick, and a cascade delete in one module silently
removes another module's rows.

The tension is genuine. Foreign keys are one of the most valuable features a relational database
offers — they make entire classes of orphaned-data bugs impossible. Giving them up across module
boundaries is a real loss, not a free win.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Single schema, FKs everywhere | Maximum referential integrity; simplest queries | Boundaries exist only in Python; extraction requires unpicking every cross-module FK; cascades cross ownership |
| Table-name prefixes in one schema (`analysis_*`) | Some visual separation; FKs still possible | Convention only — nothing prevents a cross-boundary join or FK |
| **Schema per module, no cross-schema FKs (chosen)** | Ownership is physical; extraction is mechanical; per-schema `GRANT`s become possible later | **Loses DB-enforced integrity across modules**; orphans become possible |
| Separate databases per module | Strongest isolation | No cross-module transactions at all — would break the ADR-0016 outbox, which requires the state change and the event to commit together |

## Decision

**Each module owns a PostgreSQL schema** (`identity`, `ingestion`, `parsing`, `analysis`,
`matching`, `improvement`, `interview`, `credits`, `notification`, `admin`), plus two shared
schemas (`platform` for job/outbox/AI-call infrastructure, `reference` for read-only shared data
such as the skills taxonomy).

**Foreign keys may not cross a schema boundary.**

| | Within a module | Across modules |
|---|---|---|
| Reference style | `REFERENCES` with `ON DELETE CASCADE` | Plain indexed `UUID` column |
| Integrity enforced by | PostgreSQL | Application code + nightly reconciliation |

The separate-databases option is rejected specifically because it would break ADR-0016: the
transactional outbox requires that a domain state change and its event row commit in **one**
transaction. A single database with multiple schemas preserves that while still making ownership
physical.

**Mitigation for the integrity we gave up** — this is the part that makes the trade-off
acceptable rather than reckless:

1. A nightly reconciliation job checks every declared cross-module reference for orphans and
   alerts on drift (NFR-REL-008).
2. Cross-module reference columns are declared in a registry, so the reconciliation job is
   generated from it rather than hand-maintained.
3. Cross-module deletion is driven by **domain events**, not database cascades — which is the
   same mechanism ADR-0016 already uses, and which is what makes `UserDeleted` fan-out work.
4. Per-schema database roles arrive in Horizon 2, at which point the database enforces the
   boundary directly.

## Consequences

### Positive
- Module ownership is visible and physical, not a naming convention.
- Extracting a module to a service becomes: point its connection at a new database, move its
  schema, replace in-process calls with network calls. No foreign keys to unpick.
- Cascades cannot cross ownership, so one module cannot silently delete another's data.
- Per-schema `GRANT`s in H2 turn the boundary into a database-enforced permission, closing the
  gap that lint alone leaves.
- The `platform` and `reference` schemas make shared infrastructure and shared read-only data
  explicit rather than ambiguous.

### Negative / Costs
- **Orphaned rows become possible** (Risk R-38). A resume deleted without its analyses being
  deleted leaves dangling references that the database will not catch.
- Some queries need application-level joins or two round trips where a single SQL join would have
  worked.
- Developers must know which references are "real" FKs and which are not — an inconsistency that
  needs documenting in the data dictionary.
- The reconciliation job is ongoing operational surface that a fully-FK'd schema would not need.

### Follow-up actions required
- **Phase 12:** repositories return domain objects, never ORM rows; cross-module data is fetched
  through the owning module's published interface, never by joining into its schema.
- **Phase 12:** a registry of cross-module reference columns, from which the reconciliation job is
  generated.
- **Phase 14:** a CI check that fails the build on any foreign key crossing a schema boundary — the
  rule must be machine-enforced, exactly as ADR-0014's import rule is.
- **Phase 19:** reconciliation job has its own test asserting it detects a deliberately-created
  orphan.
- **Phase 20:** orphan count per reference is a monitored metric; a non-zero count is an alert, not
  a chart nobody reads.
- **Horizon 2:** introduce per-schema database roles.
