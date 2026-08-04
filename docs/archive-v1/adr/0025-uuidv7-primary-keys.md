# ADR-0025: UUIDv7 primary keys, generated in the application

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 6
- **Deciders:** Project owner + engineering team

## Context

Phase 4 §18 requires identifiers that are "sortable, non-sequential" — sequential integers leak
business volume (a competitor can read `/analyses/1042` and know how many analyses we have ever
run) and invite enumeration attacks against any endpoint whose authorisation check is imperfect.

Three further constraints apply:

- **ADR-0016's outbox** requires that a row's ID exist *before* the transaction commits, because
  the outbox payload references it. A database-generated key forces a round trip mid-transaction
  or a `RETURNING` dance.
- **ADR-0024** forbids cross-schema foreign keys, so cross-module references are plain UUID
  columns. Those columns are indexed and joined on frequently.
- Index locality matters. Random UUIDs (v4) scatter inserts across the whole B-tree, causing page
  splits, poor cache hit rates, and index bloat — a well-documented pain point at scale.

UUIDv7 encodes a Unix millisecond timestamp in its high bits, so values generated over time are
approximately monotonic. That gives the insert locality of a sequential key while keeping the
unguessability of a UUID.

PostgreSQL 16 has no native `uuidv7()` function (it arrives in later versions), so generation must
happen somewhere we control.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| `BIGSERIAL` | Compact (8 bytes); perfect locality; simplest | **Leaks volume and ordering**; enables enumeration; requires a DB round trip before the ID exists |
| `UUIDv4` | Unguessable; client-generatable | **Random insert distribution** → page splits, index bloat, poor cache locality |
| **`UUIDv7` generated in Python (chosen)** | Time-sortable → good locality; unguessable; available **before** the transaction, which the outbox needs | 16 bytes vs 8; no native PG16 support; a library dependency |
| ULID | Same properties as UUIDv7; shorter text form | Not a native `UUID` type in PostgreSQL — loses type checking and UUID-specific indexing |
| Composite (tenant + sequence) | Meaningful keys | Complexity; still leaks ordering within a tenant |

## Decision

**All primary keys are `UUID` columns holding UUIDv7 values, generated in application code before
the row is written.**

Consequences accepted deliberately:
- IDs are available before `INSERT`, which is what lets the outbox row reference the aggregate ID
  inside the same transaction (ADR-0016).
- Time-ordering is *approximate* and must never be relied on for correctness. Where strict ordering
  matters — `admin.audit_log` — an explicit `BIGSERIAL seq` column is added alongside the UUID.
- The `admin.audit_log` and any other append-only sequence-critical table keep both: the UUID as
  the public identifier, the sequence as the ordering guarantee.

## Consequences

### Positive
- No volume or ordering leakage through public identifiers.
- Enumeration attacks are infeasible even where an authorisation check is imperfect — defence in
  depth for NFR-SEC-007.
- Insert locality close to a sequential key, avoiding the index bloat that UUIDv4 causes.
- IDs exist client-side and application-side before persistence, simplifying the outbox, idempotency
  keys, and any optimistic UI.
- Merging data across environments or future services cannot collide.

### Negative / Costs
- 16 bytes per key versus 8 for a bigint, multiplied across every foreign-key column and index. At
  our data volumes this is immaterial; at hundreds of millions of rows it would be worth revisiting.
- A library dependency for UUIDv7 generation until PostgreSQL provides it natively.
- UUIDs are unpleasant to type in a psql session during debugging.
- Approximate time ordering can mislead someone who assumes it is strict — hence the explicit
  `seq` column where ordering is load-bearing.

### Follow-up actions required
- **Phase 12:** a single ID-generation utility in `packages/platform`; no module generates IDs
  independently, so the version and encoding stay uniform.
- **Phase 12:** `admin.audit_log` carries `BIGSERIAL seq` in addition to its UUID, since the hash
  chain in ADR-0028 depends on strict ordering.
- **Phase 6/21:** revisit if any single table approaches hundreds of millions of rows, where the
  8-byte difference starts to matter.
- Migrate to a native `uuidv7()` function when the PostgreSQL version in use provides one, keeping
  application generation as the default for the pre-insert-availability property.
