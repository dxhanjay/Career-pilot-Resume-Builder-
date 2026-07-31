# ADR-0019: Python + FastAPI for backend and workers; TypeScript + Next.js for the frontend

- **Status:** Accepted
- **Date:** 2026-08-01
- **Phase:** 5
- **Deciders:** Project owner + engineering team

## Context

Phase 4 defined the architecture without naming technology. Two language decisions now follow,
and they interact.

**Backend.** The system's riskiest component is resume parsing (R-06, rated high probability ×
high impact), and Phase 4's dependency graph shows text extraction and section detection as
load-bearing: everything downstream fails if they fail. The libraries for PDF layout extraction,
DOCX structure, OCR, NER, and embeddings are overwhelmingly Python. The JavaScript equivalents
are materially weaker for exactly this work.

Against that, a single-language stack (TypeScript everywhere) would give shared types between
frontend and backend and one toolchain for a very small team.

**Frontend.** Phase 1 rated distribution failure (R-07) as high probability × critical impact,
and Phase 2 made the free tier a distribution mechanism. With no marketing budget, organic search
is the only channel available. A client-rendered SPA is structurally weak at exactly that.
Separately, ADR-0017's cookie-based sessions work most cleanly when the web application and API
share an origin.

**Interaction.** Choosing Python for the backend and a JavaScript framework for the frontend
means two toolchains. Choosing Python for both (server-rendered templates) means fighting the
framework for the genuinely interactive surfaces — the interview screen and progress dashboards.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| **Python backend + Next.js frontend (chosen)** | Best parsing/AI ecosystem; SSR for SEO; each layer uses the right tool | Two toolchains for a 1–2 person team |
| TypeScript everywhere (Node API + Next.js) | One language, shared types, one toolchain | **Weak PDF/DOCX/NLP libraries** — would end up shelling out to Python for the riskiest component anyway |
| Node API + Python workers (polyglot backend) | Node's I/O for the API, Python's libraries for workers | Two images, two dependency trees, duplicated domain models — **breaks ADR-0014's one-image-multiple-entrypoints property** |
| Python backend + server-rendered templates | One runtime; simplest deployment | Interview screen and dashboards are genuinely interactive; constant friction |
| Python + React SPA (no SSR) | Simpler than Next.js | No SSR ⇒ weak SEO, against our binding constraint |
| Django instead of FastAPI | Admin, ORM, migrations included | Conventions pull toward fat models that fight hexagonal layering; DRF serialisers duplicate Pydantic; **admin exposes all model fields by default, fighting FR-ADM-006** |
| Go or Java backend | Performance; single binary (Go) | Thin document-AI ecosystem (Go); heavier for a small team (Java) |

## Decision

**Backend, workers, and scheduler: Python 3.12 with FastAPI, Pydantic v2, SQLAlchemy 2.0, and
Alembic.** All three deploy from one Docker image with different entrypoints, per ADR-0014.

**Frontend: Next.js 15 (App Router) with TypeScript in strict mode**, deployed on the same origin
as the API — the CDN routes `/api/*` to the Python service and everything else to Next.js.

Type sharing across the language boundary is recovered by generating TypeScript types from the
FastAPI-produced OpenAPI schema.

The three reasons FastAPI wins over Django, in order of weight:

1. **Pydantic serves three purposes with one type system** — HTTP boundary validation
   (NFR-SEC-006), the schema that constrains model output, and the guard stage's validator
   (Phase 4 §9.2, FR-IMP-007).
2. **OpenAPI is generated from the same annotations that validate**, so it cannot drift
   (NFR-MNT-005).
3. **It has no opinion about the domain layer**, which is what keeps the scoring rubric free of
   infrastructure and therefore testable in CI without a database or API key (NFR-AI-003).

## Consequences

### Positive
- The strongest available libraries are used for the component most likely to fail.
- SSR addresses SEO, the channel we most depend on and are weakest in.
- Pydantic unifies validation across HTTP and model output, removing a whole category of
  duplicated schema code.
- One image for API, worker, and scheduler means enqueuing and consuming code cannot drift.
- Same-origin deployment makes ADR-0017's cookie sessions straightforward, with no CORS to
  misconfigure.

### Negative / Costs
- **Two toolchains** — Python and Node — for a very small team (Risk R-35). Two dependency
  ecosystems, two lint/type setups, two audit surfaces.
- No shared source types; the generated-types pipeline must be kept in CI or it silently drifts.
- We assemble what Django would provide: ORM, migrations, and admin pages.
- Next.js App Router carries real complexity of its own.

### Follow-up actions required
- **Phase 6:** SQLAlchemy models sit in each module's `infrastructure` layer; the domain layer
  never imports them. Repositories return domain objects, not ORM rows.
- **Phase 12:** route handlers stay thin — validate, call a use case, serialise. `import-linter`
  enforces that no module imports another's internals.
- **Phase 13:** OpenAPI → TypeScript generation runs in CI, and a drift check fails the build.
- **Phase 14:** one multi-stage Dockerfile producing the shared image; separate build for the web
  app.
- Admin pages are purpose-built and minimal — the seven capabilities in FR-ADM-001…007, nothing
  more (Phase 2 §7.2).
