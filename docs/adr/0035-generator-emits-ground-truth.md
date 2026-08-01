# ADR-0035: Generate structured truth first, then render — labels come free

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 8
- **Deciders:** Project owner + engineering team

## Context

ADR-0034 committed to a synthetic evaluation corpus. That leaves the question of *how* to generate
it, and the obvious approach and the correct approach differ significantly.

The obvious approach: ask a model to write realistic resumes, render them to PDF, then annotate the
results — marking section boundaries, entity spans, and layout properties by hand. At roughly 20
minutes per document, a 50-document corpus costs about 17 hours of annotation, and every expansion
costs proportionally more.

The correct approach inverts the order. **If we generate the structured facts first and render them
into a document, we already know every label exactly** — the sections, because we placed them; the
entities, because we wrote them; the date ranges, because we chose them; the column count, because
we selected the template.

Annotation is the dominant cost in almost every evaluation corpus. Inverting the generation order
eliminates it entirely for the generated portion.

There is a second, harder problem. Real resumes are messy in specific, catalogueable ways — typos,
mixed date formats, smart quotes and mojibake from copy-paste, inconsistent bullet characters,
stray orphan lines. A cleanly-rendered synthetic resume tests none of that, and a pipeline tuned on
clean documents will underperform on real ones (Risk R-50).

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Generate documents, then annotate | Straightforward; annotator sees what the model sees | ~17 hours for 50 documents; expansion cost scales linearly; annotation errors corrupt metrics silently |
| Generate documents, auto-annotate with a model | Cheaper than manual | The labels come from the same class of system under test — circular, and unusable as ground truth |
| **Generate structured truth, then render (chosen)** | ⭐ Labels exact and free; layout truth known by construction; expansion is a config change | Requires building a renderer; output is cleaner than reality |
| Hand-author documents and labels together | Full control | Extremely slow; no systematic layout coverage |

## Decision

**Content is generated as structured data, then rendered into documents. The structured data *is*
the label set.**

```
Persona spec → Content generator (schema-constrained) → Structured truth
                                                             ├→ Template renderer → PDF / DOCX
                                                             └→ Label sidecar (JSONL)
                                                                        ↓
                                                              Noise injector → corpus
```

The truth record captures sections and their order, entities with types and values, skills, date
ranges with their rendered form, and — critically — `layout_truth`: column count, presence of
tables, text-in-images, and header/footer content. **Layout truth is known because we chose the
template**, which is what makes the adversarial layout suite (D2) verifiable rather than a matter of
judgement.

**Noise injection is part of the generator, not an afterthought.** Generated documents are
deliberately corrupted with the defects real resumes exhibit: character-level typos, mixed date
formats within one document, smart quotes and non-breaking spaces, inconsistent bullet glyphs,
irregular spacing, truncated contact blocks, tense inconsistencies, and orphan lines belonging to no
section. Noise is **parameterised and seeded**, so a corpus version regenerates byte-identically.

Two constraints on generated identities: all emails use `example.com`, all phone numbers use
reserved ranges, and names are checked against the taxonomy so no persona coincides with a real
person by construction.

## Consequences

### Positive
- **Annotation cost for the primary corpus is zero.** The ~17 hours that would have gone into
  labelling go into the generator instead — and the generator is reusable.
- Labels are exact rather than approximate, removing an entire class of corpus errors (R-51) from
  the generated portion.
- `layout_truth` makes the deterministic layout analyser testable against ground truth, which is the
  only mechanical defence against R-44's silent-wrong-answer failure mode.
- The bias perturbation set (D4) becomes trivial: regenerate the same structured content with a
  different name, and everything else is byte-identical by construction.
- Corpus expansion, locale expansion, and new adversarial layouts are configuration changes.
- Seeded generation means a corpus version is reproducible, so it can be versioned like code
  (ADR-0038).
- Noise injection makes a specific, testable claim about realism rather than a vague one.

### Negative / Costs
- Building the generator and 16 templates is roughly three days of upfront work before any gate can
  run.
- **Generated content risks homogeneity** — the same model writing 50 resumes tends toward the same
  phrasing and structure (R-52). Mitigated by an explicit persona matrix and diversity assertions in
  the balance check.
- The noise catalogue is our hypothesis about how real resumes are messy. If the hypothesis is
  incomplete, we have blind spots — which is precisely what the D12 transfer check exists to detect.
- Rendering fidelity is a dependency: if our renderer produces PDFs structurally unlike those from
  Word, Canva, or LaTeX, we test our renderer rather than the real distribution.

### Follow-up actions required
- **Phase 8 build:** templates must be produced by *different* rendering paths where possible —
  a Word-derived DOCX, a LaTeX-derived PDF, an HTML-to-PDF path — so we are not only testing one
  renderer's output conventions.
- **Phase 9:** the noise catalogue is validated against the D12 real set; defects observed there
  and absent from the catalogue are added.
- **Phase 19:** balance and diversity assertions run in CI so homogeneity is detected rather than
  assumed absent.
- **Ongoing:** when a production parsing failure is diagnosed, the failure mode is added to the
  noise catalogue or the template set, so the corpus learns from reality without ingesting user
  content.
