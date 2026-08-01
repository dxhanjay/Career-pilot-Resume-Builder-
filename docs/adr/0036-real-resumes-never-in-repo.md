# ADR-0036: Real resumes never enter the repository or CI

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 8
- **Deciders:** Project owner + engineering team

## Context

ADR-0034 permits a small set of volunteered real resumes (dataset D12, 15–20 documents) whose sole
purpose is to verify that performance on synthetic data transfers to real documents — the control
for Risk R-50, the main weakness of a synthetic-first strategy.

The convenient place to put them is `evals/real/`, alongside the synthetic corpus, so the same
harness runs both and CI reports both metrics. That convenience carries consequences that are easy
to underestimate:

- **Git history is permanent.** A resume committed once and deleted later remains in the history,
  in every clone, and in every fork. Removing it requires rewriting history across every copy — a
  practical impossibility once shared.
- **CI runners are ephemeral, shared, and log-verbose.** Test output, failure diagnostics, and
  artefact uploads routinely capture file contents. A parsing test that fails will print the text it
  parsed.
- **Repository access is broader than production access.** Anyone who can read the code can read
  the corpus. That is a wider circle than the one Phase 3 §7 permits for user personal data, and it
  has none of the audit logging that `admin.content_access_log` provides.
- **Consent is revocable** (ADR-0034). A revocation that cannot be honoured because the data is in
  Git history is a compliance failure, not an inconvenience.

Put plainly: we would be creating exactly the kind of uncontrolled personal-data estate that Phase 3
requires us to avoid for our users — for our own convenience, and for 20 documents.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Real resumes in the repository | Simple; one harness; CI reports everything | Permanent in Git history; leaks into CI logs; broad access; consent not revocable in practice |
| Real resumes in Git LFS | Slightly better hygiene | Same history, access, and CI-log problems |
| Encrypted in the repository, decrypted in CI | Keeps everything in one place | The key must exist in CI, so the CI-log exposure remains; and a leaked key exposes history permanently |
| Fully de-identified, then committed | Reduces sensitivity substantially | De-identification of documents is imperfect — layout, employer combinations, and unusual details can re-identify; and it is the *layout* we need, which is the part we cannot redact |
| **Separate encrypted store; never in repo or CI (chosen)** | No permanent copies; narrow access; consent honourable; CI stays clean | The transfer check becomes a manual pre-release step rather than automated |

## Decision

**Real resumes are never committed to the repository and never present in any CI environment.**

| Aspect | Rule |
|---|---|
| Storage | Encrypted external store, access restricted to the project owner |
| Repository | Referenced by opaque ID only; no file, no text, no excerpt |
| CI | Never present. **CI runs on synthetic data exclusively** |
| Transfer check | A deliberate, manual, pre-release step in a controlled local or staging environment |
| De-identification | Still applied before storage — names, emails, phones, addresses replaced with reserved-range equivalents. Belt and braces, since layout is what we need, not identity |
| Consent | Written, purpose-limited, revocable; withdrawal deletes the item and triggers re-validation |
| Retention | 24 months, or until consent is withdrawn |

**Enforcement is mechanical, not procedural.** Every corpus item carries a provenance record with a
`contains_real_pii` flag, and a CI rule rejects any item so marked from existing in the repository.
A secret-scanning-style check also fails the build on files matching resume-document heuristics
outside the synthetic corpus directory.

## Consequences

### Positive
- No permanent copies of real personal data in Git history, in forks, or in CI artefacts.
- Consent revocation is genuinely honourable — the data exists in one controlled place and can be
  deleted.
- We apply to our volunteers the same standard Phase 3 requires us to apply to paying users, which
  is the consistency the product's ethical positioning depends on.
- CI stays free of sensitive data, so test output, failure logs, and artefacts can be verbose
  without risk.
- The access circle for real personal data stays narrow and intentional.

### Negative / Costs
- **The transfer check cannot be automated in CI.** It becomes a manual pre-release step, which
  means it can be skipped under deadline pressure — a real risk that the release checklist must
  guard.
- Metrics on real data are not visible in pull requests, so a regression affecting real documents
  specifically is caught later than one affecting synthetic documents.
- Managing an encrypted external store and its access is small but genuine operational overhead.
- Onboarding a second developer later requires a deliberate decision about whether they get access
  at all.

### Follow-up actions required
- **Phase 8:** the consent form states plainly what we do, what we do not do, how long we keep it,
  and how to withdraw — written for a friend to read, not a lawyer.
- **Phase 14:** CI rules implementing the provenance check and the stray-document check, from the
  first commit of the corpus.
- **Phase 19:** the D12 transfer check is an explicit item on the release checklist, with its result
  recorded per release, so skipping it is visible rather than silent.
- **Phase 22:** the runbook covers consent withdrawal — delete, re-run validation, record the
  action.
- Revisit if the team grows: a second engineer needing access requires a documented decision, not a
  quiet credential share.
