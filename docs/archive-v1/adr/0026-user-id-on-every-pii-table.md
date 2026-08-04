# ADR-0026: Every table holding personal data carries `user_id`, even when derivable

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 6
- **Deciders:** Project owner + engineering team

## Context

FR-PRIV-002 and FR-PRIV-003 require that a user can delete their account and **all** associated
personal data, completed within 30 days, with confirmation. AC-7 tightens this: primary storage
and object storage cleared within 24 hours, backups rotated out within 30 days.

The normalised design would place `user_id` only on top-level entities and derive ownership
through joins. Under that design, deleting a user's parsed document sections requires:

```sql
DELETE FROM parsing.document_sections ds
USING parsing.parsed_documents pd, ingestion.resumes r
WHERE ds.parsed_document_id = pd.id
  AND pd.resume_id = r.id
  AND r.user_id = $1;
```

Three problems follow, and they compound:

1. **Deletion order becomes load-bearing.** If `resumes` is deleted first, the join chain breaks
   and the sections become permanently unreachable orphans — undeletable personal data, which is
   the precise failure GDPR erasure exists to prevent.
2. **ADR-0024 forbids cross-schema foreign keys**, so those joins cross module boundaries that the
   architecture deliberately severed. The erasure routine would have to know every module's
   internal structure, defeating the boundary.
3. **It depends on developer memory.** Every new child table adds a join path that someone must
   remember. The failure is silent: nothing errors, the data simply survives.

This is the single most common way erasure implementations fail in practice — not through
negligence, but through a schema that made correctness depend on knowledge held outside the schema.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Normalised — derive ownership via joins | Textbook 3NF; no redundancy | Deletion order matters; joins cross module boundaries; silent failure when a table is forgotten; orphans become permanently undeletable |
| Cascade deletes from `users` | Automatic | **Impossible** — ADR-0024 forbids the cross-schema FKs that cascades require |
| Soft delete (`deleted_at`) everywhere | Reversible; simple | **Does not satisfy erasure at all** — the data is still there. Soft delete and GDPR erasure are contradictory |
| **`user_id` on every PII table (chosen)** | Deletion is one predicate per table; order-independent; no join-path knowledge; verifiable | Denormalised; column must be populated correctly on write |
| Separate database per user | Perfect isolation and deletion | Absurd at this scale |

## Decision

**Every table containing personal data carries a `user_id` column, indexed, even where ownership is
derivable through joins.**

Erasure therefore becomes, for every table:

```sql
DELETE FROM <schema>.<table> WHERE user_id = $1;
```

Order-independent, join-free, and requiring no knowledge of any module's internal structure. Each
module handles its own `UserDeleted` event (ADR-0016), deleting from its own schema only — which
keeps the boundary intact while making erasure trivially correct.

**Two supporting mechanisms make this a system rather than a convention:**

1. **An erasure registry** lists every `(schema, table)` holding user data. The scheduler's
   verification sweep iterates it and asserts zero remaining rows after deletion.
2. **A CI test fails the build** if any table with a `user_id` column is absent from both the
   erasure registry and the retention policy table (Risk R-39). New personal data cannot be added
   silently.

**What survives deletion:** `credits.billing_records` — an anonymised financial aggregate carrying
amount, date, currency, and plan code with **no user reference**. Retained for the 7-year financial
record-keeping obligation. It is non-reidentifiable, which is what makes retaining it lawful.

## Consequences

### Positive
- Erasure is **provably complete** rather than hopefully complete — the registry plus sweep gives a
  verifiable assertion, which is exactly what a regulator or auditor asks for.
- Deletion order does not matter, eliminating the orphan class entirely.
- Each module deletes only from its own schema, so ADR-0024's boundary survives contact with
  compliance.
- The same column powers efficient per-user queries throughout the application — every list view
  filters by `user_id` anyway, so the index earns its keep beyond erasure.
- IDOR defence benefits: repositories scope by `user_id` directly at every level, rather than
  relying on a join to establish ownership (NFR-SEC-007).
- Retention purges use the same predicate shape, so retention and erasure share one mechanism.

### Negative / Costs
- **Denormalised.** `user_id` is redundant with the join path in most child tables — a deliberate
  violation of 3NF, justified by compliance and query patterns.
- The column must be populated correctly on insert; a missing or wrong `user_id` creates a row that
  erasure will not find. Mitigated by `NOT NULL` and by repositories setting it centrally rather
  than each call site.
- Storage overhead of 16 bytes per row plus an index per table. Immaterial at our volumes.
- Reparenting data between users (not a current use case) would require updating many rows.

### Follow-up actions required
- **Phase 12:** `user_id` is set by the repository layer, not by individual call sites, so it
  cannot be forgotten; `NOT NULL` on every such column.
- **Phase 12:** the erasure registry is a real table, and the `UserDeleted` handler in each module
  is generated from or checked against it.
- **Phase 14:** the CI test described above runs from slice S0, before there are many tables to
  retrofit.
- **Phase 19:** an erasure test creates a user with data in every module, deletes them, and asserts
  zero rows remain across the full registry — plus object storage.
- **Phase 22:** the erasure runbook documents the backup-rotation window and how a DSR is confirmed
  to the user.
