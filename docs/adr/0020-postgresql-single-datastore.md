# ADR-0020: PostgreSQL as the single datastore, including vectors and documents

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 5
- **Deciders:** Project owner + engineering team

## Context

Three storage needs appear across the design, and the naive answer is three different systems:

1. **Transactional relational data** — users, resumes, jobs, and the credit ledger. ADR-0007 and
   ADR-0016 require that a credit reservation, a job row, and an outbox row commit **atomically or
   not at all**. This single transaction is what prevents both stranded credits and vanished jobs;
   it is the least forgiving requirement in the system.
2. **Semi-structured documents** — parsed resume structures are irregular, deeply nested, and will
   evolve as the parser improves. This is the textbook argument for a document store.
3. **Vector similarity** — resume and JD embeddings for semantic matching (FR-MATCH-002). The
   default industry answer is a dedicated vector database.

Phase 4 left the vector placement as an open question. Phase 5's selection criteria weight
operational burden at 20% (C3), reflecting a 1–2 person part-time team with no on-call rotation
(ADR-0005, ADR-0009), where **every additional system must be operated, monitored, backed up,
patched, and debugged**.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| **PostgreSQL alone (JSONB + pgvector) (chosen)** | Full ACID for the ledger; JSONB gives document flexibility; pgvector gives similarity search; one system to operate, back up, and restore | Vertical scaling ceiling (far beyond MVP); pgvector is less specialised than a purpose-built vector engine |
| PostgreSQL + MongoDB | Document store optimised for parsed structures | Two systems; **cross-store consistency has no transaction**; JSONB already covers the need |
| PostgreSQL + dedicated vector DB (Qdrant/Pinecone/Weaviate) | Best-in-class vector performance and features | A third system, or a paid service; unnecessary at our corpus size; another failure domain and backup story |
| MongoDB as primary | Flexible schema; horizontal scaling | **Weaker transactional fit for the ledger**, the part that least tolerates weakness; no relational integrity; still needs a vector solution |
| MySQL/MariaDB | Mature, widely available | Weaker JSON support; no first-class vector extension; less rich indexing |

## Decision

**PostgreSQL 16+ is the only datastore**, using:

- **relational tables** for users, sessions, resumes, jobs, analyses, sessions, and the credit
  ledger;
- **`JSONB` with GIN indexing** for parsed resume structures and other irregular payloads;
- **`pgvector`** for resume and job-description embeddings and similarity search.

Redis remains for cache, sessions, rate limits, and the message broker (ADR-0021), but holds no
system-of-record data — it is safely losable by design.

This decision **closes Phase 4's open question 1** in favour of keeping vectors in the primary
database.

**Documented upgrade paths, per ADR-0018's reopening-condition discipline:**

| Trigger (measured, not assumed) | Response |
|---|---|
| pgvector query latency exceeds the matching stage's budget, or index size strains the instance | Extract to a dedicated vector service |
| Write throughput saturates a single primary | Read replicas, then time-partition `analyses` |
| A genuine document-store access pattern emerges that JSONB serves badly | Reassess — but the burden of proof is on the new system |

## Consequences

### Positive
- The atomic transaction that ADR-0016 depends on is trivially available:
  `resume + job + credit reservation + outbox` in one `COMMIT`.
- **One system to operate**: one backup, one restore drill (NFR-REL-009), one connection story,
  one monitoring surface, one thing to patch.
- Vector search adds an extension, not a service — no new failure domain, no new billing line, no
  new data to keep in sync.
- JSONB delivers the flexibility that motivates document stores, without giving up transactions
  where they matter.
- Managed PostgreSQL with pgvector is available from every hosting option under consideration
  (Phase 5 §17), so this choice does not constrain hosting.
- The vector index remains *derived and rebuildable* from the primary data (Phase 4 §13), so
  changing embedding models in Phase 10 is a re-index rather than a migration.

### Negative / Costs
- Migrations require discipline that a schemaless store would not (mitigated by Alembic and
  NFR-MNT-004's reversible-migration requirement).
- pgvector is less feature-rich than a specialised vector engine — no advanced filtering DSL, less
  tuning surface. Acceptable at our corpus size; revisit on the trigger above.
- A single database is a single failure domain. Mitigated by managed automated backups, PITR, and
  the RTO/RPO targets in NFR-AVL-006/007.
- Vertical scaling has a ceiling — far beyond MVP, but real.

### Follow-up actions required
- **Phase 6:** schema organised per module (ADR-0014); **no foreign keys cross module boundaries**
  — cross-module references are by ID with integrity enforced in application code, so extraction
  stays mechanical. JSONB columns get explicit, versioned shapes rather than being a dumping
  ground.
- **Phase 6:** ledger tables designed append-only with derived balances (ADR-0007); the outbox
  table indexed on unpublished rows (ADR-0016).
- **Phase 10:** embedding dimensionality is a schema decision — changing models means re-indexing,
  so the choice is recorded with the migration path.
- **Phase 14/16:** managed PostgreSQL with the `pgvector` extension available is a hard hosting
  requirement.
- **Phase 22:** monthly restore drill covers the single-failure-domain risk.
