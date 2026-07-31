# ADR-0013: Build internationalisation-ready; defer localisation; parameterise advice by locale

- **Status:** Accepted
- **Date:** 2026-07-31
- **Phase:** 3
- **Deciders:** Project owner + engineering team

## Context

Two commonly-conflated things must be separated:

- **Internationalisation (i18n)** — building so the product *can* be localised: externalised
  strings, no sentence concatenation, UTF-8 throughout, UTC timestamps, locale-aware
  formatting, layouts that tolerate text expansion.
- **Localisation (l10n)** — actually adapting to a locale: translations, regional conventions,
  local payment methods, non-English content processing.

The cost asymmetry is stark. i18n done from the start costs roughly 2% additional effort and
is mostly a matter of discipline. Retrofitting it into a codebase with hardcoded English
strings and concatenated sentences is a multi-week rewrite touching every component.
Localisation, by contrast, costs real money per locale and delivers value only where there is
demand.

ADR-0005 defaults us to India-first with English resumes. That makes actual translation
premature — but it does **not** make locale-awareness premature, for a reason specific to this
product.

**The non-obvious problem: our advice is locale-specific, and being wrong is worse than being
untranslated.** The ATS rubric encodes conventions that genuinely differ by market:

| Convention | US / EU norm | Common in India |
|---|---|---|
| Photograph on resume | Omit | Frequently included |
| Date of birth, marital status, father's name | Never included | Often included |
| Resume length (early career) | 1 page | 2 pages common |
| "CV" vs "resume" | Distinct documents | Used interchangeably |
| Degree nomenclature | BS / BA | B.Tech / B.E. |
| Date format | MM/DD or DD/MM | DD/MM |

A rubric with a single hardcoded rule that fires regardless of market is not a translation
gap — it is a **correctness bug that produces confidently wrong advice**, which is precisely
what our USP claims not to do. And unpicking a global rule set into a locale-aware one later
is far harder than building the locale dimension in from the start, because by then the rules,
their weights, their tests, and their golden-corpus expectations all assume a single market.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| Ignore i18n entirely | Fastest now | Multi-week retrofit later; blocks the H3 expansion the roadmap depends on |
| Full localisation at MVP | Ready for every market | Doubles eval sets and QA surface for markets we have no users in; contradicts ADR-0006's scope discipline |
| **i18n-ready, l10n deferred, rubric locale-parameterised (chosen)** | Cheap now; expansion is a configuration change; avoids wrong advice | Discipline required with no immediate visible payoff |
| i18n-ready but rubric hardcoded to one market | Slightly less work | Leaves the actual correctness problem in place — the expensive half |

## Decision

**Build i18n-ready from slice S0; defer localisation to Horizon 3; parameterise the scoring
rubric by locale from the start.**

Adopted at MVP (i18n):
- All user-facing strings externalised; none hardcoded (NFR-I18N-001)
- No string concatenation for sentences — parameterised templates only (NFR-I18N-002)
- UTF-8 end to end: storage, transport, rendering, filenames (NFR-I18N-003)
- Timestamps stored in UTC, rendered in the user's timezone (NFR-I18N-004)
- Locale-aware date, number, and currency formatting (NFR-I18N-005)
- Layouts tolerate 30% text expansion (NFR-I18N-006)
- No text baked into images (NFR-I18N-007)
- Currency and region as first-class fields on pricing and billing (NFR-I18N-008, per ADR-0008)
- **The ATS rubric carries a locale dimension** (NFR-I18N-011)

Deferred to H3 (l10n):
- UI translations
- Non-English resume parsing
- Additional locale rule sets beyond the first two

**MVP ships one active locale** — `en-IN` conventions as default, `en-US` as the alternative —
but the *mechanism* for locale-specific rules exists from the first rubric commit.

## Consequences

### Positive
- Market expansion in H3 becomes a configuration and content exercise rather than a rewrite.
- Avoids giving confidently wrong, market-inappropriate advice — the failure mode most
  damaging to the product's core claim.
- Locale-aware date parsing directly improves entity extraction accuracy (FR-PARSE-004), since
  DD/MM vs MM/DD ambiguity is a real parsing error source, not only a display concern.
- The institutional channel in H3 becomes viable in multiple regions without re-engineering.

### Negative / Costs
- ~2% ongoing effort with no visible benefit during H1, which makes it easy to abandon under
  deadline pressure.
- Externalised strings are marginally less convenient to author than inline literals.
- The rubric's locale dimension adds a parameter to every rule, its tests, and its golden-corpus
  expectations even while only one locale is active.
- Requires a lint rule to enforce, because discipline alone will not hold.

### Follow-up actions required
- **Phase 7:** rubric rules are defined with an explicit locale scope; rules that are
  genuinely universal are marked as such rather than defaulting to universal.
- **Phase 8:** the golden corpus records each resume's locale, and expectations are
  locale-specific.
- **Phase 12/13:** i18n framework selected in Phase 5; a lint rule fails the build on hardcoded
  user-facing strings, so NFR-I18N-001 is enforced rather than hoped for.
- **Phase 19:** at least one test asserts locale-specific rubric behaviour differs correctly
  between `en-IN` and `en-US`.
