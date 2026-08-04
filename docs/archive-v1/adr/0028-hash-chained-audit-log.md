# ADR-0028: The audit log is hash-chained and verified nightly

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 6
- **Deciders:** Project owner + engineering team

## Context

NFR-SEC-009 requires an audit log covering authentication, administrative actions, credit
movements, and content access — and specifies that it be **tamper-evident**. FR-ADM-002 requires
that credit grants and revocations carry a mandatory reason and be audited. FR-ADM-006 requires
that break-glass access to resume content be logged and capable of user notification.

The word "tamper-evident" is doing real work in that requirement, and it is usually ignored. A
typical audit table is an ordinary table: an administrator with database access, or an attacker who
has obtained such access, can delete or alter rows and leave no trace. An audit log that can be
silently edited provides no evidence — it provides *false confidence*, which is worse than having
no log, because decisions get made on the assumption that it is reliable.

The threat is concrete for us. Break-glass content access (FR-ADM-006) exists precisely so that an
administrator can, under justification, read a user's resume. The audit entry for that access is the
only thing standing between "a controlled exception" and "unaccountable access to everyone's
personal data". If that entry can be deleted by the same person who created it, the control is
theatre.

Append-only permissions (`REVOKE UPDATE, DELETE`) stop honest mistakes and application bugs. They do
not stop someone with superuser access, and they leave no evidence of what was removed.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Ordinary table | Simple | Silently editable; the requirement is not met |
| Append-only permissions only | Stops application bugs and honest error | A superuser can still alter history, undetectably |
| **Append-only + hash chain + nightly verification (chosen)** | Any alteration or deletion is **detectable**; cheap to compute; no external dependency | Requires strict ordering; verification job to run; a broken chain must be investigated, not ignored |
| Ship logs to an external append-only store | Off-host copy resists local tampering | Ongoing cost; another dependency; latency; still want local integrity |
| Blockchain / external notary | Strongest guarantee | Absurdly disproportionate at this scale and cost |

## Decision

**`admin.audit_log` is append-only and hash-chained.**

Each row carries:

```
seq        BIGSERIAL     -- strict ordering (UUIDv7 ordering is approximate, ADR-0025)
prev_hash  TEXT          -- row_hash of the immediately preceding row
row_hash   TEXT          -- SHA256(prev_hash ‖ canonical_serialisation(row fields))
```

Writes are serialised through a single append function so that `prev_hash` is read and the new
`row_hash` computed atomically. `UPDATE` and `DELETE` are revoked, and a trigger raises on either.

**A nightly verifier** recomputes the chain from the last verified checkpoint and alerts on any
mismatch. A broken chain is a security incident, not a data-quality warning — the runbook treats it
as such.

**`admin.content_access_log` is a separate table**, also chained. Break-glass access to user content
is qualitatively different from ordinary administrative activity: it is the category a user has a
right to be told about, it carries a mandatory `justification`, and it is the first thing anyone
reviews after a suspected incident. Keeping it separate means it is small, dense, and reviewable
rather than buried in routine noise.

Horizon 2 adds periodic export of chain checkpoints to write-once external storage, which extends
detectability to an attacker who controls the database entirely.

## Consequences

### Positive
- Alteration or deletion of any historical entry breaks every subsequent hash and is detected —
  which is what makes "tamper-evident" true rather than aspirational.
- Break-glass content access becomes genuinely accountable, which is the control FR-ADM-006 depends
  on.
- Provides real evidence during incident response and for any future audit, rather than a log whose
  integrity has to be assumed.
- Cheap: one SHA-256 per audit row, and audit writes are low-volume by nature.
- No external dependency, no additional cost, no new service to operate.

### Negative / Costs
- Writes must be serialised to compute `prev_hash` correctly, making the audit log a serialisation
  point. Acceptable because audit volume is low; it would not be acceptable for a high-throughput
  table.
- A legitimate schema change to the audit table changes the canonical serialisation and therefore
  breaks the chain — this requires a deliberate re-anchoring procedure, documented rather than
  improvised.
- Verification failures must be investigated. A team that learns to ignore the alert has undone the
  control entirely.
- Until H2's external checkpoint export, an attacker with full database control could recompute the
  entire chain. Detectability is strong against tampering, weaker against total compromise — stated
  plainly rather than overclaimed.

### Follow-up actions required
- **Phase 12:** a single `append_audit()` function is the only write path; direct inserts are
  forbidden and blocked by permissions.
- **Phase 12:** canonical serialisation is defined explicitly (field order, encoding, null
  handling) and versioned, since the chain's validity depends on it being stable.
- **Phase 14:** `REVOKE UPDATE, DELETE` on both audit tables in a migration, plus the raising
  trigger.
- **Phase 19:** a test alters a historical row directly and asserts the verifier detects it.
- **Phase 20:** chain-verification failure is a high-severity alert routed as a security incident,
  not a data-quality warning.
- **Phase 22:** the runbook covers both re-anchoring after an intentional schema change and incident
  response after an unexpected break.
- **Horizon 2:** export chain checkpoints to write-once external storage.
