# ADR-0027: JSONB is permitted only for versioned, whole-read structures

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 6
- **Deciders:** Project owner + engineering team

## Context

ADR-0020 chose PostgreSQL partly *because* `JSONB` provides document flexibility without a second
datastore. Several structures in this system genuinely warrant it: extracted page layout, rubric
definitions, guard-stage reports, model payloads, and evaluation dimension scores are all irregular,
nested, and consumed as a whole.

But JSONB has a well-known failure mode. Because it accepts anything, it becomes the path of least
resistance for any field someone does not want to write a migration for. Six months later a column
named `metadata` holds forty different shapes written by twelve code paths, none documented, none
validated, and none safely queryable — and by then it cannot be normalised without a data
archaeology project.

The risk is higher than usual here because our parsed-resume structures genuinely *are* irregular
and evolving. The legitimate use sits right next to the illegitimate one, which is exactly the
condition under which discipline erodes.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Ban JSONB entirely | Maximum rigour | Forces absurd normalisation of genuinely irregular structures like page layout; loses the reason we chose PostgreSQL over MongoDB |
| Unrestricted JSONB | Fast to write; no migrations | Becomes a landfill (Risk R-40); shapes undocumented; queries unreliable; normalising later is a data migration nightmare |
| **Permitted under an explicit policy (chosen)** | Flexibility where warranted, rigour where it matters | Requires judgement and enforcement |
| JSONB with a database-level JSON Schema check | Strongest validation | PostgreSQL has no native JSON Schema validation; a trigger-based implementation is slow and awkward |

## Decision

**JSONB is permitted only where all four conditions hold:**

1. The structure is **irregular or deeply nested** — normalising it would produce tables nobody
   queries independently.
2. It is **read as a whole**, not filtered, sorted, or joined on.
3. It has an **owning Pydantic model** in the codebase that defines and validates its shape.
4. The column has a **sibling `*_schema_version` column** recording which shape version the row
   holds.

**The decision rule, applied in order:**

| Question | If yes |
|---|---|
| Will we `WHERE`, `ORDER BY`, or `JOIN` on it? | **Column** |
| Does it have a stable, known shape? | **Column** |
| Is it a repeating group we will count or aggregate? | **Child table** |
| Irregular, nested, versioned, consumed whole? | **JSONB** |
| Unsure? | **Column** — promoting JSONB to a column later is easy; the reverse is not |

**Approved JSONB columns** (the complete list; additions require review):

| Column | Why |
|---|---|
| `parsing.parsed_documents.layout` | Page geometry — genuinely irregular, read whole |
| `parsing.unclassified_blocks.bbox` | Coordinates, read whole |
| `parsing.document_entities.normalized_value` | Shape varies by entity type |
| `analysis.rubrics.definition` | The rubric as data (ADR-0013); versioned by design |
| `analysis.findings.source_span` | Span coordinates |
| `improvement.rewrites.guard_report` | Guard-stage output, read whole |
| `interview.evaluations.dimension_scores` | Rubric-dependent shape |
| `platform.jobs.payload` / `.error` | Type-dependent |
| `platform.outbox.payload` | Event-dependent |
| `credits.payments.provider_payload` | Provider-defined, retained verbatim for reconciliation |

**Explicitly forbidden:** a general-purpose `metadata` or `extra` JSONB column on any table. That
pattern is how the landfill starts, and it has no legitimate use we can name.

## Consequences

### Positive
- Keeps the flexibility that justified choosing PostgreSQL over a document store, without
  inheriting the document store's weaknesses.
- Every JSONB shape has an owning Pydantic model, so it is validated on write and typed on read —
  the same type system already used for HTTP boundaries and LLM output (ADR-0019).
- `*_schema_version` makes shape evolution explicit: old rows remain readable, and migration is a
  deliberate act rather than a discovery.
- The approved list makes an unapproved JSONB column visible in code review.
- The "unsure → column" default biases toward the reversible choice.

### Negative / Costs
- Judgement is required at design time, and judgement erodes under deadline pressure.
- `*_schema_version` on every JSONB column is mild overhead and one more thing to set correctly.
- Reading old schema versions requires the application to handle multiple shapes for a while.
- GIN indexes on JSONB are larger and slower to update than B-trees on scalar columns — a reason
  not to reach for JSONB when a column would do.

### Follow-up actions required
- **Phase 12:** every approved JSONB column has a corresponding Pydantic model in its module's
  domain layer; writes go through it, so unvalidated JSON cannot reach the database.
- **Phase 12:** readers dispatch on `*_schema_version`; an unknown version is an error, not a
  silent partial parse.
- **Phase 14:** a CI check flags any new JSONB column not present in the approved list above, and
  any column named `metadata` or `extra`.
- **Phase 19:** round-trip tests for each JSONB shape, including reading a prior schema version.
- Additions to the approved list supersede this ADR rather than editing it.
