# ADR-0032: The guard detects fabrication by diffing entities against the source and the JD

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 7
- **Deciders:** Project owner + engineering team

## Context

ADR-0004 committed the product to never fabricating user experience, and Phase 4 §9.2 placed a
mandatory guard stage between inference and persistence. Phase 6 added a database constraint —
`CHECK (grounding_verified = true)` — so an ungrounded rewrite cannot physically be stored.

None of that specifies **how fabrication is actually detected**. Without a concrete algorithm, the
guard degrades into a schema validator, and the strongest commitment in the product becomes a
sentence in a policy document.

The failure mode is specific and predictable. When a model receives both a resume and a job
description and is asked to improve a bullet, the most natural thing it can do is quietly import a
required skill from the JD into the rewritten bullet. The output looks excellent — the bullet now
matches the role perfectly — and it is precisely the fabricated resume ADR-0004 exists to prevent.
It is also the failure a human reviewer is least likely to catch, because the result reads well and
the invented content is plausible.

A second, subtler case: invented metrics. *"Improved the checkout flow"* becomes *"Improved checkout
conversion by 40%"*. The number is fluent, specific, and completely fabricated, and the user may not
notice it was not theirs before it reaches an interviewer.

NFR-AI-004 sets a **zero-tolerance CI gate** on hallucinated facts. Zero tolerance requires a
mechanical test, not a review process.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Trust the prompt ("do not invent facts") | Free; simple | Prompts are guidance, not enforcement; fails under any distribution shift or model change |
| Schema validation only | Catches malformed output | Fabricated content is perfectly well-formed — this catches none of it |
| **Entity-level diff against source and JD (chosen)** | Mechanical, testable, zero-tolerance-compatible; catches the two dominant failure modes | Needs entity extraction on output; risks over-triggering on legitimate rephrasing |
| A second LLM judges groundedness | Handles nuance | Non-deterministic; costs another call; a probabilistic check on a probabilistic output is not a guarantee |
| Human review of every suggestion | Highest accuracy | Impossible at scale; defeats the product |

## Decision

**Every generated rewrite passes a six-check guard before it may be persisted or shown.** Checks 3
and 5 are the load-bearing ones.

```python
def guard(output, source_text, source_entities, jd_text) -> Verdict:
    # 1 · SCHEMA
    parsed = ResponseModel.model_validate(output)            # FR-IMP-007

    # 2 · SPAN RESOLUTION — every cited span must exist in the source
    for span in parsed.source_spans:
        if not resolves_exactly(span, source_text):
            return REJECT("unresolvable_source_span")

    # 3 · NOVEL-ENTITY DIFF  ← primary fabrication check (FR-IMP-004)
    for e in extract_entities(parsed.after_text):
        if e.type in {ORG, DATE, CREDENTIAL, INSTITUTION, JOB_TITLE}:
            if not appears_in(e, source_entities):           # normalised, fuzzy
                return REJECT(f"fabricated_{e.type}: {e.value}")

    # 4 · NUMERIC CHECK — no invented metrics
    for n in numbers_in(parsed.after_text):
        if n not in numbers_in(source_span_text):
            return REJECT_OR_CONVERT_TO_FACT_PROMPT(n)       # FR-IMP-005

    # 5 · JD LEAKAGE  ← the failure mode most systems miss (FR-IMP-006)
    for skill in skills_in(parsed.after_text):
        if skill in skills_in(jd_text) and skill not in source_skills:
            return REJECT("jd_skill_injected")

    # 6 · INJECTION ECHO
    if looks_like_instruction_echo(parsed.after_text):
        return REJECT("injection_echo")

    return ACCEPT
```

**Check 4 converts rather than merely rejecting.** An invented number becomes a `fact_prompt`
asking the user for the real figure — turning a guardrail into the feature users find most useful
(FR-IMP-005).

**Failure handling:** retry once with the violation fed back as an explicit constraint; on a second
failure, drop *that suggestion* and log the reason. The job never fails, and raw model output is
never surfaced.

**Entity matching is normalised and fuzzy** — "Google" and "Google LLC" must match, or legitimate
rephrasing is rejected (Risk R-49).

**Guard rejection rate is a monitored SLI with a target band.** Too high means the prompt or the
model has drifted; too low may mean the checks have been weakened. It is the earliest warning
signal we have for either.

## Consequences

### Positive
- ADR-0004 becomes an enforced mechanism rather than a stated intention, and NFR-AI-004's
  zero-tolerance gate has something mechanical to test.
- Catches the two dominant real-world failure modes — JD skill leakage and invented metrics —
  including the one a human reviewer would most likely miss.
- The check is deterministic, so the eval suite is reproducible and can gate CI.
- Rejection reasons are structured, so failures are analysable and the prompt can be improved from
  evidence.
- Check 4's conversion turns enforcement into a product feature.
- The rejection-rate SLI gives early warning of provider-side model changes.

### Negative / Costs
- Requires entity extraction on generated output — an extra deterministic pass per suggestion.
- **Over-triggering is a real risk** (R-49): legitimate reformatting of an organisation name or a
  date can look novel. Fuzzy normalised matching mitigates it; sampled review tunes it.
- Cannot catch fabrication that introduces no new entity or number — for example, exaggerating
  scope ("led a team" from "worked in a team"). This is a **known gap**, stated rather than
  glossed; the mitigation is prompt design plus the sampled review, not a claim of completeness.
- Adds latency to every suggestion.

### Follow-up actions required
- **Phase 8:** a fabrication eval set built specifically around the two dominant modes — resumes
  paired with JDs requiring skills the resume lacks, and bullets inviting invented metrics.
- **Phase 9:** tune fuzzy entity-matching thresholds; measure over-trigger rate on legitimate
  rephrasing.
- **Phase 12:** the guard is the only write path for rewrites; the Phase 6 `CHECK` constraint
  provides the second layer.
- **Phase 19:** the fabrication suite is a **blocking S3 CI gate** at zero tolerance; the
  scope-exaggeration gap is covered by a separate, sampled human review rather than pretended away.
- **Phase 20:** rejection rate by reason is dashboarded, with alerts on both a spike and a
  collapse.
