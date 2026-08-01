# ADR-0052: Define fairness as invariance, and state what we do not measure

- **Status:** Accepted
- **Date:** 2026-08-02
- **Phase:** 11
- **Deciders:** Project owner + engineering team

## Context

Phase 1 Risk R-12 identified bias — penalising non-native English, non-elite institutions, or career
gaps — as high-impact. FR-ATS-009 forbids scoring on name, gender, photograph, age, nationality, or
institution prestige. NFR-AI-006 sets a blocking gate: score variance ≤ 1 point across identity
perturbation.

What none of those state is **which definition of fairness we are claiming**, and that omission
matters. "Fair" is not one property. The formal literature offers several mutually incompatible
definitions — demographic parity, equalised odds, calibration within groups — and a system can
satisfy one while violating another. A product that says "we test for bias" without naming the
measure is making an unfalsifiable claim.

Our situation is also unusual in a way that rules most of those definitions out. They are defined for
**classifiers that make decisions with outcomes**: hired or not, approved or not. Under ADR-0002 we
make no decision about anyone — we produce advice for the person whose data it is, who remains free
to ignore it. Demographic parity has nothing to be parity *of*; equalised odds requires ground-truth
labels of "good candidate" that we do not have and could not construct without embedding exactly the
bias we are guarding against.

Claiming those measures anyway would be borrowed credibility. But saying nothing would leave the
strongest property we *do* have unnamed.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Claim "fairness" without specifying | Sounds reassuring | Unfalsifiable; indefensible under scrutiny; no test follows from it |
| Adopt demographic parity | Widely recognised | Requires decisions and group outcomes we do not have |
| Adopt equalised odds | Rigorous where applicable | Requires ground-truth "good candidate" labels — constructing them would embed the bias we are testing for |
| Calibration within groups | Meaningful for scored systems | Requires outcome data and demographic collection; we deliberately collect neither |
| **Invariance under identity perturbation (chosen)** | Directly testable; strictly stronger than parity for our shape; matches what FR-ATS-009 already forbids | Says nothing about whether the *advice* is equally useful across groups |

## Decision

**Our fairness property is invariance:**

> Identical content must receive an identical score, regardless of identity signals.

Formally, for content *c* and identity attributes *a*:
`|score(c, a₁) − score(c, a₂)| ≤ 1` for all *a₁, a₂*.

This is exactly what dataset D4 tests — 15 base resumes × 12 variants, content byte-identical,
varying only name (across gender and ethnic association), institution tier, pronouns, and photograph
presence — and what NFR-AI-006 gates in CI at slice S2.

**Invariance is a stronger property than parity for a system of our shape.** Parity permits identity
to influence the score as long as group averages balance out. Invariance forbids identity from
influencing the score at all. FR-ATS-009 provides the structural defence by excluding those
attributes from scoring inputs; D4 verifies the exclusion actually held rather than assuming it.

**What we explicitly do not implement, and why** — recorded so the omissions are decisions:

| Measure | Why not |
|---|---|
| Demographic parity | Requires a decision with outcomes; we produce advice |
| Equalised odds | Requires ground-truth quality labels we cannot construct without bias |
| Calibration within groups | Requires outcome data and demographic collection we deliberately avoid |
| **Advice equity** | ⭐ **Genuinely applicable, currently unmeasurable.** Whether suggestions are equally *useful* across groups needs usage and outcome data. Declared as an open Horizon 2 gap, not claimed |

**Two limitations are documented rather than glossed** (Phase 11 §9.2):

1. **Invariance cannot detect bias in the rubric itself.** All twelve variants are scored by the
   same rubric, so a culturally biased rule penalises them equally and variance is zero. This is why
   §9.3's per-rule fairness review exists as a second, human layer — and why a passing D4 gate must
   not be read as proof of fairness (Risk R-68).
2. **Single-axis perturbation misses intersectional effects.** Varying name and institution
   independently does not test their interaction. A known gap, to be closed with paired-axis
   variants when the corpus expands.

## Consequences

### Positive
- The fairness claim is **specific, testable, and falsifiable** — a passing or failing number, not a
  reassurance.
- Invariance is the strongest available property for our system shape, and stronger than the
  measures we declined.
- Mechanically enforced as a blocking CI gate rather than asserted in a policy document.
- Only possible because the corpus is synthetic (ADR-0035) — holding content byte-identical while
  varying a name is trivial with generated data and impossible with real resumes. A concrete return
  on that decision.
- Naming the measures we do *not* implement prevents overclaiming and makes the residual risk
  visible.

### Negative / Costs
- Invariance says nothing about advice quality across groups — the property arguably closest to real
  user harm, and the one we cannot yet measure.
- A passing D4 gate is easy to over-read as "the system is fair." The rubric-review layer and this
  ADR's limitations section are the only defences against that, and both depend on people reading
  them.
- Intersectional effects remain untested at MVP.
- Declining the standard academic measures means the claim is less recognisable to a reviewer
  expecting them — this ADR is the answer.

### Follow-up actions required
- **Phase 8 / slice S2:** D4 built and the NFR-AI-006 gate wired as blocking.
- **Phase 11 §9.3:** the per-rule fairness review runs before rubric v1 is published, and its outcome
  is recorded with the rubric version.
- **Phase 8 expansion:** paired-axis variants added when the corpus grows to 150, closing the
  intersectional gap.
- **Phase 20:** score distribution monitored for drift; a systematic shift is a bias signal even when
  D4 passes.
- **Horizon 2:** design advice-equity measurement once usage data exists — this is the most
  important unclosed gap in the fairness story, and it should be treated as such rather than
  forgotten.
- Any claim of "fair" in user-facing or marketing copy must reference invariance specifically, not
  fairness in general.
