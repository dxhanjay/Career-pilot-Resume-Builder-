# Phase 5 — Technology Stack

**Project:** AI Resume Analyzer & Mock Interview Platform
**Status:** Draft — awaiting approval
**Date:** 2026-08-01
**Depends on:** [Phase 1](../phase-01-problem-definition.md) ✅ · [Phase 2](../product/phase-02-product-planning.md) ✅ · [Phase 3](../requirements/phase-03-requirement-engineering.md) ✅ · [Phase 4](phase-04-system-architecture.md) ✅

---

## 1. Objective

Select the concrete technology for every component in the Phase 4 architecture, with the
alternatives compared and the reasoning recorded — so that Phase 6 can design a schema against
a real database, Phase 12/13 can implement against real frameworks, and nobody re-opens these
questions in month four.

---

## 2. Why This Phase Matters

Phase 4 deliberately named nothing. Now we choose — and the choices bind.

Three failure modes this phase prevents:

1. **Choosing by familiarity or fashion.** The two most common selection criteria in practice
   are "what I used last time" and "what's trending". Neither is a criterion. §4 defines
   weighted criteria derived from our actual constraints, and every choice below is argued
   against them.
2. **Choosing a stack that fights the architecture.** Phase 4 requires machine-enforced module
   boundaries, deterministic domain logic free of infrastructure, generated OpenAPI, and a
   fake AI adapter in S0. A stack that makes any of those awkward will quietly erode them.
3. **Discovering licensing or cost problems after building.** §13 contains a licensing finding
   that would have been genuinely expensive to discover in month three, and §20 checks the whole
   stack against NFR-COST-005 (≤ $150/month) *before* we commit.

> **The governing principle: we are a 1–2 person part-time team. Every technology must earn its
> operational cost.** Boring, managed, and well-documented beats powerful and demanding — every
> time, at this size.

---

## 3. Deliverables

- [x] Weighted selection criteria derived from Phase 1–4 constraints (§4)
- [x] Backend language and framework, with alternatives (§5, §6)
- [x] Task queue, broker, and the Kafka/RabbitMQ/Redis comparison (§7)
- [x] Database decision incl. PostgreSQL vs MongoDB and the vector question (§8, §9)
- [x] Frontend framework and supporting stack (§10, §11)
- [x] Document-processing and AI libraries **with licence audit** (§12, §13)
- [x] Authentication build-vs-buy analysis (§14)
- [x] Containers, orchestration, cloud, and the Nginx question (§15–§17)
- [x] CI/CD, observability, email, payments (§18–§20)
- [x] Complete stack table (§21)
- [x] Monthly cost model checked against NFR-COST-005 (§22)
- [x] Concrete folder structure with real filenames (§23)
- [x] ADR-0019 … ADR-0023

---

## 4. Selection Criteria

Weighted from the constraints established in Phases 1–4. Every choice below is scored against
these, not against preference.

| # | Criterion | Weight | Origin |
|---|---|:--:|---|
| C1 | **Fits the Phase 4 architecture** — async workers, enforced boundaries, ports/adapters, generated OpenAPI | 25% | Phase 4 |
| C2 | **Ecosystem for document parsing and AI** — the core technical risk | 20% | R-06, Phase 1 wedge |
| C3 | **Low operational burden** — managed over self-hosted; few moving parts | 20% | ADR-0005, ADR-0009, R-11 |
| C4 | **Cost at MVP scale** | 15% | NFR-COST-005 (≤ $150/mo) |
| C5 | **Maturity, documentation, hiring pool** | 10% | Team of 1–2; must be able to find answers |
| C6 | **Licence compatibility with commercial SaaS** | 10% | ADR-0005 (real monetisable product) |

**Disqualifiers** (no weighting — these eliminate outright):
- Cannot satisfy a MUST-level requirement from Phase 3
- Licence incompatible with closed-source commercial use
- Requires an operational capability we don't have (a cluster to babysit, a 24/7 on-call)

---

## 5. Backend Language

| Option | C1 Arch | C2 AI/parsing | C3 Ops | C4 Cost | C5 Maturity | Verdict |
|---|---|---|---|---|---|---|
| **Python 3.12+** | ✅ Mature async, strong typing via hints | ✅ **Dominant** — pdfplumber, spaCy, sentence-transformers, Tesseract bindings, every model SDK | ✅ | ✅ | ✅ | ✅ **Chosen** |
| Node.js / TypeScript | ✅ Async-native; shared types with frontend | ❌ **PDF/DOCX/NLP libraries are markedly weaker**; would end up shelling out to Python anyway | ✅ | ✅ | ✅ | ◐ Strong runner-up, loses on C2 |
| Go | ✅ Excellent concurrency, single binary | ❌ Very thin document-AI ecosystem | ✅ | ✅ | ◐ | ❌ |
| Java / Kotlin | ✅ Apache Tika is genuinely good for parsing | ◐ Weaker ML tooling than Python | ❌ Heavier for a small team | ◐ | ✅ | ❌ |
| Polyglot: Node API + Python workers | ◐ | ✅ | ❌ **Two toolchains, two images, two dependency sets** — breaks ADR-0014's shared-image property | ◐ | ✅ | ❌ |

### Decision: **Python for API, workers, and scheduler**

**The deciding argument is C2.** This product is a document-processing system before it is
anything else. Resume parsing is the load-bearing component (Phase 4 §8 dependency graph) and
the highest-rated technical risk (R-06). The mature libraries for PDF layout extraction, DOCX
structure, OCR, NER, and embeddings are Python libraries. Choosing anything else means either
using materially weaker tools for our riskiest component, or running Python anyway as a second
runtime.

The polyglot option deserves its rejection stated plainly: it is architecturally tempting
(Node's I/O for the API, Python's libraries for the workers) and operationally wrong for a
part-time team. ADR-0014 specifies API and workers deploying from **one image with different
entrypoints** precisely so the enqueuing code and the consuming code cannot drift. Two languages
means two images, two dependency trees, two CI paths, and duplicated domain models.

**What we give up:** shared types between backend and frontend. Mitigated by generating
TypeScript types from the OpenAPI schema (§11), which is a solved problem and arguably cleaner
than sharing source.

---

## 6. Backend Framework

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| **FastAPI** | Async-native; **Pydantic v2 gives boundary validation and LLM structured-output parsing in one type system**; OpenAPI generated automatically (NFR-MNT-005); minimal, composes well with Clean Architecture; excellent docs | No ORM, migrations, or admin — you assemble them | ✅ **Chosen** |
| Django + DRF | Batteries included; admin nearly free; ORM + migrations; mature auth | Conventions pull toward fat models and app-centric structure that fights hexagonal layering; async support retrofitted; DRF serialisers are a second, weaker validation system alongside Pydantic; **its admin exposes all model fields by default — which actively fights FR-ADM-006** | ◐ Rejected, see below |
| Flask | Minimal, flexible | Async is bolted on; no built-in validation or OpenAPI; you assemble even more than FastAPI | ❌ |
| Litestar | Modern, fast, Pydantic-native, DI built in | Smaller community — matters a lot when a team of two hits an obscure bug (C5) | ◐ Close second |

### Decision: **FastAPI + Pydantic v2 + SQLAlchemy 2.0 + Alembic**

Three reasons, in order of weight:

1. **Pydantic is the same tool for three jobs.** It validates HTTP input at the boundary
   (NFR-SEC-006), it defines the schema that constrains model output, and it *is* the guard
   stage's schema validator (Phase 4 §9.2, FR-IMP-007). One type system covering request
   validation, response serialisation, and LLM structured output is a genuine simplification —
   not a marginal preference.
2. **OpenAPI is generated, not maintained.** NFR-MNT-005 requires an always-current spec.
   FastAPI produces it from the same type annotations that do the validation, so it cannot
   drift.
3. **It stays out of the way of the architecture.** Phase 4 requires domain logic with zero
   infrastructure dependencies so the rubric is testable without a database or an API key
   (NFR-AI-003). FastAPI has no opinion about your domain layer. Django has several.

**On losing the Django admin** — this is the strongest argument for Django, and it is weaker
here than it looks. FR-ADM-006 requires that **admins cannot read resume content by default**.
Django's admin defaults to exposing every field of every registered model; achieving our
requirement means fighting the framework's default at every model. Meanwhile the entire admin
surface we actually need is seven requirements (FR-ADM-001…007) — a small purpose-built set of
pages, which we would end up building anyway.

**Supporting choices:** SQLAlchemy 2.0 (typed, async-capable, and importantly the *repository*
pattern sits naturally on it — keeping ORM objects out of the domain layer), Alembic for
versioned reversible migrations (NFR-MNT-004), `dependency-injector` or FastAPI's own DI for
wiring adapters to ports.

---

## 7. Task Queue & Message Broker

### The broker question, reframed

Phase 4 §9.1 put the **job state machine in the database** — states, attempts, leases, and
idempotency keys are Postgres rows. ADR-0016 added the transactional outbox, and the scheduler
sweeps stale unpublished rows and expired leases.

That changes what the broker must be. **The broker is a delivery transport, not the durability
guarantee.** If a message is lost, the outbox row is still unpublished and the sweep republishes
it; if a worker dies, the lease expires and the job returns to the queue. Durability is already
solved in Postgres.

This is decisive, because it removes the main reason to run a heavyweight broker.

### Broker comparison

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| **Redis** | **Already required** for cache, sessions, and rate limits (Phase 4 §13) — so it adds *zero* new services; fast; simple; supported by every Python queue library | Weaker delivery durability than a real broker — **acceptable here**, because Postgres holds the truth | ✅ **Chosen for MVP** |
| RabbitMQ | Proper AMQP semantics: priority queues, dead-letter exchanges, acks, durable queues | One more service to run, monitor, and patch (C3); its durability guarantees duplicate what Postgres already gives us | ◐ **Named upgrade path** |
| Kafka | Extremely high throughput; replayable log; partitioning | **Wrong tool.** We need task distribution, not an event log. Operationally heavy (brokers, ZooKeeper/KRaft, topic management) for a system doing ~500 jobs/day at peak. Adopting it would be a textbook case of architecture-by-résumé | ❌ Rejected |
| Managed SQS/Cloud Tasks | Durable, DLQ built in, zero ops | Couples to one cloud early (§17 keeps us portable); adds latency; another billing dimension | ◐ Reconsider on cloud migration |

### Worker library comparison

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| **Dramatiq** | Simple, sane defaults, **supports both Redis and RabbitMQ brokers behind the same API** — so the upgrade path is a config change; built-in retries with backoff, DLQ, middleware hooks | Smaller community than Celery | ✅ **Chosen** |
| Celery | Largest ecosystem; battle-tested; extensive features | Sprawling configuration; well-known reliability footguns (visibility timeouts, late-ack semantics); async support is poor | ◐ Fallback if we hit a Dramatiq wall |
| arq | Async-native, lightweight, Redis-based | Less mature; fewer middleware hooks; our heaviest work is CPU-bound parsing, where async buys little | ◐ |
| RQ | Simplest possible | No priority queues — **fails NFR-CAP-004** (free must not starve Pro) | ❌ Disqualified |

**Decision: Dramatiq on Redis**, with priority queues per tier (NFR-CAP-004) and RabbitMQ as a
documented one-config-line migration if routing needs outgrow Redis
([ADR-0021](../adr/0021-redis-broker-postgres-truth.md)).

> **Note the shape of this reasoning.** We didn't pick the "most correct" broker; we noticed that
> an earlier architectural decision (state in Postgres + outbox) had already absorbed the
> requirement the heavyweight broker would satisfy — so paying for it twice would be waste. This
> is what it means for architecture to precede technology.

---

## 8. Database

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| **PostgreSQL 16+** | Full ACID — **required by ADR-0007/0016**: credit reservation + job row + outbox row must commit atomically; `JSONB` gives document flexibility for parsed resume structures; **`pgvector` gives semantic search without another datastore**; `SKIP LOCKED` for queue-ish patterns; mature managed offerings everywhere; excellent tooling | Schema migrations require discipline; vertical scaling has a ceiling (far beyond MVP) | ✅ **Chosen** |
| MongoDB | Flexible documents suit parsed-resume shapes; easy horizontal scaling | **Multi-document transactions are a weaker fit for the ledger**, which is the least forgiving part of the system; no built-in relational integrity; a second system needed for vectors anyway; JSONB already gives us the flexibility we actually wanted | ❌ |
| MySQL/MariaDB | Mature, widely available | Weaker JSON support, no first-class vector extension, less rich indexing | ❌ |
| SQLite | Zero ops; great for local | Single-writer; unfit for concurrent workers | ◐ Local tests only |

### Decision: **PostgreSQL as the single datastore** ([ADR-0020](../adr/0020-postgresql-single-datastore.md))

The decisive requirement is atomicity. ADR-0016's entire value is that this commits or doesn't:

```sql
BEGIN;
  INSERT INTO resumes ...;
  INSERT INTO jobs ...;
  INSERT INTO credit_entries ...;   -- the reservation
  INSERT INTO outbox ...;           -- the event
COMMIT;
```

Every alternative makes this harder, and this transaction is the mechanism that prevents both
stranded credits and vanished jobs.

**Document flexibility without MongoDB.** Parsed resume structures are irregular and evolving —
the classic argument for a document store. `JSONB` with GIN indexing covers it inside the same
database, so we get the flexibility where we want it *and* transactions where we need them.

**This also answers Phase 4's open question 1.** `pgvector` provides embedding similarity search
inside the primary database. At MVP corpus size this is comfortably sufficient, and it removes an
entire system from the operational surface (C3). A dedicated vector service (Qdrant, Pinecone,
Weaviate) is the documented upgrade path, triggered by measured index size or query latency —
per ADR-0018's reopening-condition discipline.

---

## 9. Cache, Sessions, Rate Limiting

**Redis 7+** — one service covering four needs (Phase 4 §13): session records cache, rate-limit
counters, AI response cache, and the Dramatiq broker.

Consistent with Phase 4's rule that **every cache must be safely losable**: sessions fall back to
Postgres, rate limits fail closed to a lower static cap, the AI cache falls back to recomputation
(costing money, not correctness), and lost broker messages are recovered by the outbox sweep.

---

## 10. Frontend Framework

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| **Next.js 15 (App Router) + TypeScript strict** | SSR/SSG for the marketing surface — **SEO matters because distribution is our binding constraint (R-07)**; React ecosystem depth; strong i18n routing (ADR-0013); server components reduce client bundle; same-origin deployment works cleanly with cookie auth (ADR-0017) | Adds a Node runtime alongside Python; App Router has real complexity | ✅ **Chosen** |
| React SPA (Vite) | Simplest mental model; pure static hosting | **No SSR ⇒ weak SEO** for landing and shareable reports; would need a separate marketing site | ◐ |
| Vue 3 / Nuxt | Excellent DX; gentler learning curve; Nuxt matches Next's capabilities | Smaller ecosystem for the accessible-component primitives we need (ADR-0011) | ◐ |
| Angular | Batteries included; strong typing; enterprise conventions | Heavy for a 1–2 person team; steepest curve; least suited to rapid iteration | ❌ |
| Server-rendered Python templates | One language, one runtime, no Node | The interview screen and progress dashboards are genuinely interactive; would fight the framework constantly | ❌ |

### Decision: **Next.js + TypeScript strict mode**

**SEO is the argument that decides it.** Phase 1 R-07 rated distribution failure as high
probability × critical impact, and Phase 2 made the free tier a distribution mechanism. Organic
search is the only channel available at a ₹0 marketing budget. A client-rendered SPA is a
structural handicap in exactly the dimension where we're weakest.

**Deployment shape** (resolves the ADR-0017 same-site requirement): one origin, with the CDN
routing `/api/*` to the Python API and everything else to Next.js. Cookies work with no
cross-origin complications, and there is no CORS configuration to get wrong.

---

## 11. Frontend Supporting Stack

| Concern | Choice | Why | Alternatives rejected |
|---|---|---|---|
| Styling | **Tailwind CSS** | Colocated, no dead CSS, dark mode built in, consistent spacing scale | CSS Modules (more boilerplate), styled-components (runtime cost) |
| Components | **Radix UI primitives, composed in-repo (shadcn/ui pattern)** | **Headless primitives ship correct ARIA, focus management, and keyboard interaction** — the cheapest path to CI-gated WCAG 2.2 AA (ADR-0011); components live in our repo so we can fix a11y without waiting on a maintainer | MUI/Chakra (heavy, opinionated theming, harder to audit) |
| Server state | **TanStack Query** | **Most of our state is server state** — job polling, analyses, history. Caching, refetch, and polling are exactly its job | Redux (ceremony for data that isn't client state) |
| Client state | **Zustand** | The genuinely-client state is small (UI toggles, wizard step) | Redux Toolkit (disproportionate) |
| Forms | **React Hook Form + Zod** | Zod schemas generated from OpenAPI keep client and server validation aligned | Formik (heavier, less type-safe) |
| API types | **openapi-typescript** generated from the FastAPI spec | Recovers the type-sharing we gave up in §5, without sharing source | Hand-written types (drift guaranteed) |
| i18n | **next-intl** | Externalised messages, ICU formatting, locale routing (NFR-I18N-001…007) | react-i18next (workable; less integrated with App Router) |
| Charts | **Recharts** | Accessible-friendly, composable; score history is simple | D3 direct (unnecessary power) |
| PDF export | **Server-side, Python (WeasyPrint)** | **Tagged, accessible PDF output (NFR-A11Y-012)** and consistent rendering; client-side generation cannot reliably produce PDF/UA | jsPDF / html2canvas (raster output, inaccessible) |
| Testing | **Vitest + Testing Library + Playwright** | Playwright drives E2E *and* the `@axe-core/playwright` accessibility gate | Cypress (slower, weaker multi-browser) |

> **The Radix decision is an accessibility decision, not a styling one.** Building an accessible
> combobox, dialog, or tab set from scratch is where a11y budgets actually go. Adopting audited
> primitives is what makes NFR-A11Y-014's CI gate realistically passable.

---

## 12. Document Processing & AI Libraries

| Concern | Choice | Licence | Why |
|---|---|---|---|
| PDF text + layout | **pdfplumber** (on pdfminer.six) | MIT | Word-level bounding boxes and table detection — **exactly what FR-ATS-003 needs to detect multi-column and table layouts** |
| PDF fallback / speed | **pypdf** | BSD | Metadata, page counts, quick structural checks |
| DOCX | **python-docx** | MIT | Paragraph/style/table access for structure detection |
| OCR (S5) | **Tesseract** via `pytesseract` | Apache-2.0 | Mature, self-hostable, no per-page fee |
| Language/NLP | **spaCy** | MIT | NER, sentence segmentation, rule-based matching for section detection |
| Embeddings | **sentence-transformers** *or* a hosted embedding API | Apache-2.0 | Decided in Phase 10; the `EmbeddingPort` (ADR-0015) makes it swappable |
| Model SDKs | Behind `ai-port` only | — | Never imported outside `packages/ai-port` (ADR-0015) |
| Schema validation | **Pydantic v2** | MIT | The guard stage's validator |
| Grammar (S5) | **LanguageTool** (self-host) or an LLM pass | LGPL / — | Evaluate in Phase 7 |

---

## 13. ⚠️ Licence Audit — a finding worth flagging

**PyMuPDF (`fitz`) is the fastest and most accurate PDF layout library in Python, and we are not
using it.**

PyMuPDF is licensed **AGPL-3.0**. The AGPL's network clause extends copyleft to software provided
over a network — which is precisely what a SaaS product does. Using it in our commercial,
closed-source platform would, on a plain reading, create a source-disclosure obligation. Artifex
sells a commercial licence, which is the legitimate route if we ever want it.

This is the kind of problem that is cheap to avoid now and genuinely expensive to discover in
month three, after the parsing module is built around its API.

**Policy adopted** ([ADR-0023](../adr/0023-permissive-licences-only.md)): **permissive licences
only (MIT, BSD, Apache-2.0, ISC, MPL-2.0) for anything linked into the product.** Copyleft
(GPL/AGPL/LGPL-linked) requires an explicit exception with legal review, or a purchased commercial
licence. A licence-scanning step runs in CI so a transitively-introduced AGPL dependency fails the
build rather than arriving silently.

**Consequence we accept:** pdfplumber is slower than PyMuPDF. Our budget allows 5,000 ms for text
extraction (Phase 3 §6.1), which pdfplumber meets comfortably for typical 1–2 page resumes. If
extraction becomes the bottleneck, buying the Artifex licence is a clean, costed decision rather
than an emergency.

---

## 14. Authentication — a genuinely close call

| Option | Pros | Cons |
|---|---|---|
| **Build in-house on vetted libraries** | Full control over ADR-0017's rotation + family revocation semantics; no vendor on the login path; no per-MAU cost; consent versioning and entitlements are ours anyway | **Auth is security-critical code written by a small team** — the classic self-inflicted wound |
| Clerk / Auth0 / WorkOS | Battle-tested; MFA, OAuth, session management included; fast to integrate | Vendor on the critical login path (availability coupling); cost scales with users; we still build consent, entitlements, and break-glass; customising session-family revocation may not be possible |
| Supabase Auth | Good free tier; open source | Pulls toward adopting the wider Supabase platform |

### Decision: **build in-house, on audited primitives** — but this is a real trade-off, not a slam dunk

**Why building wins here:** ADR-0017's requirements are specific and non-standard — refresh
rotation with **whole-family revocation on reuse detection**, server-side session records for
FR-AUTH-006, constant-time responses for FR-AUTH-008, elevated admin auth with audited break-glass
(FR-ADM-006/007), and versioned consent records (FR-PRIV-005). We need server-side session state
regardless. A managed provider would sit *alongside* that model rather than replace it, adding a
dependency without removing work.

We do not write cryptography. We use `argon2-cffi` (Argon2id, FR-AUTH-003), `pyjwt` for signing,
the framework's secure cookie handling, and `authlib` for Google OAuth.

**Honest risk statement:** this is the single riskiest build-vs-buy call in the stack. It is
mitigated by a dedicated authorisation test suite (NFR-SEC-007), a Phase 17 security review of
this module specifically, and dependency scanning. **If you would rather not own authentication,
Clerk is the pick** — say so now, because retrofitting a provider after the session model is built
is unpleasant.

---

## 15. Containers & Orchestration

| Technology | Decision | Reasoning |
|---|---|---|
| **Docker** | ✅ Adopted | One image, multiple entrypoints (`api`, `worker`, `scheduler`) — directly implements ADR-0014's no-drift property. Multi-stage builds keep images small |
| **Docker Compose** | ✅ Adopted for local | `docker compose up` gives Postgres + Redis + API + worker + web — satisfies NFR-MNT-003's 30-minute onboarding with one command |
| **Kubernetes** | ❌ Rejected | Per ADR-0018: a cluster to secure, upgrade, and debug for roughly two services. Reopens after microservice extraction |
| **Terraform / OpenTofu** | ◐ Phase 14 | Warranted once infrastructure exceeds a handful of managed resources; premature at MVP where the PaaS config is a single manifest file |

---

## 16. Nginx — answered honestly

The charter lists Nginx for comparison. The honest answer: **we don't need it, and adding it
would be cargo cult.**

Nginx's roles here — TLS termination, reverse proxying, static file serving, and coarse rate
limiting — are all provided by the CDN and the platform's ingress (§17). Introducing Nginx would
mean writing and maintaining configuration for a layer we don't operate.

**When it would return:** if we migrate to raw VMs or EC2 instances (an H3 possibility), Nginx or
Caddy becomes the ingress. Recorded so the option is known rather than forgotten.

---

## 17. Cloud & Hosting

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| **Render** | Managed web services + **background workers + cron** + managed Postgres (with pgvector) + Redis, private networking — **maps 1:1 onto our exact component set**; predictable pricing; minimal ops | Fewer regions than hyperscalers; less control; scaling ceiling well above MVP but below enterprise | ✅ **Chosen for MVP** |
| Railway | Excellent DX; similar shape | Less mature networking and compliance posture | ◐ |
| Fly.io | Great global distribution; strong for latency | More DIY; Postgres is self-managed-ish | ◐ |
| DigitalOcean App Platform | Cheap; managed DB/Redis | Weaker worker/cron ergonomics | ◐ |
| **AWS** | Everything, at any scale; **ap-south-1 (Mumbai)** for India latency and residency; mature compliance | **Materially higher operational burden** — IAM, VPC, ECS/Fargate task definitions, ALB, RDS, ElastiCache, CloudWatch. Real work for a part-time team | ✅ **Named H2/H3 destination** |
| GCP | Cloud Run is excellent for stateless containers; strong data tooling | Cloud Run's request-scoped model fits workers less naturally; smaller India presence than AWS | ◐ Credible alternative |
| Azure | Strong enterprise/compliance story | Weakest DX of the three for this workload; no advantage for us | ❌ |
| Vercel | Best-in-class for Next.js | Frontend only — we'd still need somewhere for Python, Postgres, Redis, and workers | ◐ **Viable for the web tier specifically** |

### Decision: **Render for MVP; AWS (ap-south-1) as the documented migration destination** ([ADR-0022](../adr/0022-managed-paas-then-aws.md))

Render is chosen because its primitives *are* our architecture: a web service, a background
worker, a cron job, a managed Postgres, and a managed Redis. On AWS the same topology is a VPC,
subnets, security groups, an ALB, ECS task definitions, RDS, ElastiCache, EventBridge, and a
pile of IAM. **That difference is measured in weeks of a part-time team's time, spent on
infrastructure rather than on the parsing quality that actually determines whether this product
works.**

⚠️ **The data-residency trigger, flagged now.** We are India-first (ADR-0005) and Render's nearest
region is Singapore. This is fine for latency and, on current reading, fine legally. **It stops
being fine if** an institutional customer (H3) requires India-resident data, or if DPDP
obligations tighten for our category. AWS `ap-south-1` is the answer when that happens, and
ADR-0022 records it as a defined trigger rather than a surprise.

**Portability discipline:** we use no Render-proprietary API. Everything is a Docker container
plus environment variables (12-factor, NFR-MNT). The migration is real work but bounded.

---

## 18. Edge, CDN & Object Storage

| Concern | Choice | Why |
|---|---|---|
| CDN / WAF / DNS | **Cloudflare** | Free-tier CDN and WAF that meaningfully covers NFR-SEC-001/005/008; edge rate limiting; **routes `/api/*` to the Python API and everything else to Next.js on one origin**, which is what makes cookie auth simple |
| Object storage | **Cloudflare R2** | S3-compatible API (so migration is trivial) with **zero egress fees** — material for a product that serves resume files and generated PDFs repeatedly; satisfies NFR-SEC-010's separate-origin requirement naturally |
| Signed URLs | R2 presigned | Files are never proxied through the API (Phase 4 §13) |

---

## 19. CI/CD

**GitHub Actions.** The repository is Git; Actions has the ecosystem for every gate we need and a
free tier that comfortably covers a project this size. GitLab CI is equally capable and would be
the choice if the repository moved to GitLab; CircleCI and Jenkins add cost or operations without
adding capability here.

The pipeline enforces what earlier phases declared — this is where the gates become real:

| Stage | Tool | Enforces |
|---|---|---|
| Lint + format | **Ruff** (replaces black + flake8 + isort) | NFR-MNT-006 |
| Types | **mypy --strict** / `tsc --noEmit` | NFR-MNT-006 |
| **Module boundaries** | **`import-linter`** | ⭐ **ADR-0014, R-26** — the concrete answer to "how is a boundary enforced" |
| Unit + integration | pytest, Vitest | NFR-MNT-001 (≥80%, ≥90% on scoring/credits) |
| API contract | Schemathesis against the OpenAPI spec | NFR-SEC-006 |
| **Accessibility** | `@axe-core/playwright` | ⭐ **NFR-A11Y-014, ADR-0011, R-25** |
| Secrets | gitleaks | NFR-SEC-003 |
| Dependencies | `pip-audit`, `npm audit` | NFR-SEC-004 |
| **Licences** | `pip-licences` + `license-checker` with an allowlist | ⭐ **ADR-0023** |
| **AI evals** | pytest over `evals/` | ⭐ **NFR-AI-001/003/004/006/008 — the blocking slice gates** |
| E2E | Playwright | Golden path |
| Build + deploy | Docker → Render | — |

> **`import-linter` is the single most important line in this table.** ADR-0014's boundaries and
> risk R-26 depend entirely on a machine enforcing them. Without this, the modular monolith is a
> naming convention.

---

## 20. Observability, Email, Payments

| Concern | Choice | Why |
|---|---|---|
| Errors | **Sentry** | Best-in-class Python + Next.js integration; generous free tier; release tracking |
| Traces / metrics / logs | **OpenTelemetry SDK** → **Grafana Cloud free tier** (or Axiom/Better Stack) | Vendor-neutral instrumentation (NFR-OBS-001/002) — the backend can change without touching application code |
| Uptime | **Better Stack** or Cloudflare health checks, multi-region | NFR-AVL-002 requires **external** measurement |
| Product analytics | **PostHog** (cloud free tier) | Event taxonomy from Phase 2 §13; self-hostable later; EU region available |
| Email | **Resend** at MVP; **AWS SES** at scale | Excellent DX and free tier now; SES is far cheaper per-message later |
| Payments (H2) | **Razorpay** (India) + **Stripe** (global), **hosted checkout only** | ADR-0008's regional pricing; hosted checkout keeps us **entirely outside PCI-DSS scope** (Phase 2 §18) |

---

## 21. The Complete Stack

| Layer | Technology | Licence | Phase-4 component |
|---|---|---|---|
| Frontend | Next.js 15, TypeScript strict, Tailwind, Radix, TanStack Query, Zustand, RHF + Zod, next-intl | MIT | Web Application |
| API | Python 3.12, FastAPI, Pydantic v2, SQLAlchemy 2.0, Alembic | MIT/BSD | API Service |
| Workers | Dramatiq, same image, different entrypoint | LGPL-3.0¹ | Worker Service |
| Scheduler | Dramatiq cron / Render cron | — | Scheduler |
| Database | PostgreSQL 16 + pgvector + JSONB | PostgreSQL | Relational DB + Vector Index |
| Cache/Broker | Redis 7 | RSALv2/SSPL² | Cache + Message Queue |
| Object storage | Cloudflare R2 | — | Object Storage |
| Parsing | pdfplumber, pypdf, python-docx, Tesseract, spaCy | MIT/BSD/Apache | parsing module |
| AI access | Provider SDKs behind `ai-port` only | — | ai-port package |
| PDF output | WeasyPrint | BSD | report generation |
| Edge | Cloudflare CDN + WAF | — | CDN |
| Hosting | Render (→ AWS ap-south-1) | — | Deployment |
| CI/CD | GitHub Actions | — | — |
| Observability | Sentry, OpenTelemetry, Grafana Cloud, PostHog | — | — |

¹ **Dramatiq is LGPL-3.0.** Used as an unmodified library dependency (not statically linked or
modified), which is the standard permitted use — but it is an exception to §13's policy and is
therefore recorded explicitly in ADR-0023 rather than passing silently. If this is uncomfortable,
Celery (BSD) is the drop-in alternative.

² **Redis licensing has changed in recent years** (RSALv2/SSPL). We use it as an unmodified
managed service, not redistributed, which is unaffected. **Valkey** (BSD fork) is the drop-in
alternative if the licence position ever matters to us.

> Flagging both of these rather than presenting the stack as uniformly permissive is the point of
> having a licence policy at all.

---

## 22. Cost Model — checked against NFR-COST-005

`[TO VERIFY]` — provider pricing changes; confirm before committing. Figures are monthly USD at
MVP scale (Phase 3 §6.2 baseline: ~1,000 users, ~50 analyses/day).

| Item | MVP | At campus-season peak |
|---|---|---|
| Render web service (API) | $7–25 | $25–50 |
| Render worker | $7–25 | $25–75 (autoscaled) |
| Render cron (scheduler) | $0–7 | $7 |
| Managed PostgreSQL | $7–20 | $20–95 |
| Managed Redis | $10 | $10–30 |
| Next.js hosting (Render or Vercel) | $0–20 | $20 |
| Cloudflare (CDN/WAF/DNS) | $0 | $0–20 |
| Cloudflare R2 storage | ~$1 | ~$3 |
| Sentry / PostHog / Grafana / uptime | $0 (free tiers) | $0–29 |
| Resend email | $0–20 | $20 |
| **Subtotal — infrastructure** | **$32–128** | **$130–349** |
| **AI inference** (Phase 10 sets this) | **~$5–50** | **~$50–500** |
| **Total** | **$37–178** | — |

**Reading this honestly:** the MVP baseline fits inside NFR-COST-005's $150 ceiling if we stay on
lower service tiers and AI spend stays modest — but **the headroom is thin, and AI inference is
the variable that decides it.**

Three consequences, all already designed for:
- The **content-hash cache** (Phase 4 §9.2) is not an optimisation, it is what keeps us inside
  budget.
- The **global spend circuit breaker** (FR-CRED-008) is the control that prevents a runaway from
  blowing through the ceiling overnight.
- **Peak-season cost exceeds the MVP ceiling by design.** That's correct — peak means real usage,
  which means the free/paid split should be generating revenue. It is called out here so it isn't
  mistaken for a budget failure when it happens.

---

## 23. Folder Structure — now concrete

Phase 4's structure with real filenames.

```
Summer Project 1/
├── docker-compose.yml              # postgres · redis · api · worker · web  → one command
├── pyproject.toml                  # ruff · mypy · pytest · import-linter config
├── alembic.ini
├── .github/workflows/ci.yml        # every gate from §19
│
├── apps/
│   ├── api/
│   │   ├── main.py                 # FastAPI app, DI container, middleware chain
│   │   ├── routes/                 # v1/{auth,resumes,analyses,matches,interviews,jobs,admin}.py
│   │   ├── middleware/             # correlation.py · auth.py · ratelimit.py · errors.py · csrf.py
│   │   └── Dockerfile              # multi-stage; shared by worker + scheduler
│   ├── worker/main.py              # Dramatiq entrypoint; imports handlers
│   ├── scheduler/main.py           # retention · reconciliation · lease sweep · outbox sweep
│   └── web/                        # Next.js 15 App Router
│       ├── app/{(marketing),(app)}/…
│       ├── components/ui/          # Radix-composed, in-repo (a11y editable)
│       ├── lib/api/                # generated from OpenAPI (openapi-typescript)
│       ├── messages/en-IN.json     # externalised strings (NFR-I18N-001)
│       └── tests/e2e/              # Playwright + axe
│
├── modules/                        # the modular monolith — import-linter enforced
│   ├── identity/{api,application,domain,infrastructure,migrations,tests}/
│   ├── ingestion/…
│   ├── parsing/…
│   ├── analysis/
│   │   └── domain/rubrics/
│   │       ├── ats_v1.en_IN.yaml   # ⭐ rubric as versioned, locale-scoped DATA
│   │       └── ats_v1.en_US.yaml
│   ├── matching/ · improvement/ · interview/ · credits/ · notification/ · admin/
│
├── packages/
│   ├── platform/                   # config · logging · tracing · errors · ids · clock · flags
│   ├── ai_port/
│   │   ├── ports.py                # InferencePort · EmbeddingPort
│   │   ├── decorators/             # cache · metering · retry · breaker · guard
│   │   └── adapters/fake.py        # ⭐ ships in S0, before any provider is chosen
│   ├── job_engine/                 # state machine · leases · outbox relay
│   └── contracts/
│
├── evals/                          # ⭐ blocking AI quality gates
│   ├── golden_corpus/ · fabrication/ · bias/ · injection/
├── tests/{e2e,load,chaos}/         # k6 load · kill-worker-mid-job chaos
└── infra/render.yaml               # infrastructure as a single manifest
```

---

## 24. Implementation Strategy

The stack lands in the order the slices need it — nothing is set up before it's used.

| Slice | Technology introduced |
|---|---|
| **S0** | Docker Compose, FastAPI skeleton, Postgres + Alembic, Redis, Dramatiq, job engine + outbox, Next.js shell, GitHub Actions with **import-linter and axe gates from the first commit**, Sentry, `FakeAdapter` |
| **S1** | pdfplumber, python-docx, spaCy, golden-corpus eval harness |
| **S2** | Rubric YAML loader, determinism + bias eval suites, first real AI provider behind the port |
| **S3** | pgvector, embeddings, guard-stage grounding checks, fabrication + injection evals |
| **S4** | Interview session state, SSE for turn latency, WeasyPrint reports |
| **S5** | Cloudflare R2, credits ledger, retention scheduler, admin pages, PostHog, R2 malware scanning |

**The gates go in first, in S0.** `import-linter` and `axe` in the pipeline before there is much
to check is deliberate: adding a gate to a clean repository costs an hour; adding it to a repository
with 200 violations costs a week and usually gets skipped (R-25, R-26).

---

## 25. Best Practices Applied

- **Weighted criteria published before choices**, so decisions are arguable rather than asserted
- **Rejections explained** — including strong runners-up (Node, Django, Litestar, RabbitMQ)
- **Licence audit performed**, with two exceptions flagged rather than glossed
- **Build-vs-buy stated as a close call** where it genuinely is (authentication)
- **Cost modelled against the NFR** before commitment, not discovered afterwards
- **Portability preserved** — no proprietary platform APIs; migration triggers documented
- **CI gates introduced at S0**, when they are cheap
- **One database, one cache, one broker** — operational surface minimised deliberately

---

## 26. Security Considerations

| Choice | Security consequence |
|---|---|
| Pydantic at every boundary | Schema validation is structural, not remembered (NFR-SEC-006) |
| `argon2-cffi` (Argon2id) | Memory-hard hashing (FR-AUTH-003) |
| httpOnly cookies + CSRF middleware | ADR-0017; CSRF applied by default with an explicit opt-out list |
| Cloudflare WAF + edge rate limits | Coarse layer before anything reaches Python (NFR-SEC-008) |
| R2 separate origin + presigned URLs | Uploaded files unreachable from the app origin (NFR-SEC-010) |
| Sandboxed parsing in workers | Malformed PDFs are contained with CPU/memory caps (NFR-CAP-005) |
| gitleaks + pip-audit + npm audit in CI | NFR-SEC-003/004 |
| **Licence scanning in CI** | Prevents an AGPL dependency arriving transitively (ADR-0023) |
| Provider SDKs importable only inside `ai_port` | Enforced by import-linter — no unguarded model call can exist |

---

## 27. Scalability Considerations

| Bottleneck (Phase 4 §23 order) | How this stack responds |
|---|---|
| 1 · AI provider rate limits | Redis-backed response cache; `ai_port` supports a fallback adapter; Phase 10 may add a second provider |
| 2 · Database connections | PgBouncer (Render offers pooling); SQLAlchemy pool tuning |
| 3 · Worker CPU on extraction | Dramatiq processes scale horizontally on queue depth; 2 vCPU-min cap per job |
| 4 · DB write throughput | Postgres read replicas, then time-partitioning of analyses — far beyond MVP |
| Vector search growth | pgvector → dedicated vector service, per ADR-0018's reopening conditions |
| Broker throughput | Redis → RabbitMQ is a Dramatiq configuration change |

---

## 28. Risks (Phase 5 additions)

| ID | Risk | P×I | Mitigation |
|---|---|:--:|---|
| **R-32** | **pdfplumber's accuracy is insufficient for NFR-AI-001 (F1 ≥ 0.90)** — we rejected the best library on licensing | 🟠×🔴 | Measured in S1, week 3. Fallbacks in order: tune with spaCy rules → add pypdf structural signals → **purchase the Artifex commercial licence** (a costed decision, not an emergency) |
| **R-33** | **In-house auth contains a vulnerability** | 🟠×🔴 | Vetted primitives only; dedicated authz test suite; Phase 17 review targets this module first; **Clerk remains a viable pivot until S5** |
| R-34 | Render's ceiling is hit sooner than expected, or India residency becomes mandatory | 🟠×🟠 | No proprietary APIs used; ADR-0022 documents the AWS ap-south-1 trigger and path |
| R-35 | Python + TypeScript means two toolchains for a small team | 🔴×🟡 | Unavoidable given the frontend choice; mitigated by generated API types and a single CI pipeline |
| R-36 | Free tiers (Sentry, PostHog, Grafana, Cloudflare) change or expire | 🟠×🟡 | All are OpenTelemetry-based or self-hostable; no vendor-specific instrumentation in application code |
| R-37 | AI spend pushes total cost past NFR-COST-005 | 🟠×🔴 | Cache hit-rate SLI, per-feature cost attribution, global circuit breaker — all designed in Phase 4 |

---

## 29. Production Readiness Checklist — Phase 5 Exit

| # | Criterion | Status |
|---|---|---|
| 1 | Selection criteria defined and weighted before choosing | ✅ |
| 2 | Every charter-listed technology compared and ruled on | ✅ |
| 3 | Rejections explained, including strong alternatives | ✅ |
| 4 | Stack verified against the Phase 4 architecture | ✅ |
| 5 | Stack verified against Phase 3 MUST requirements | ✅ |
| 6 | **Licence audit completed; policy adopted; exceptions recorded** | ✅ |
| 7 | Cost modelled against NFR-COST-005 | ✅ (thin headroom, stated) |
| 8 | CI gates mapped to specific tools | ✅ |
| 9 | Boundary enforcement has a named mechanism | ✅ `import-linter` |
| 10 | Portability and migration triggers documented | ✅ |
| 11 | ADR-0019…0023 recorded | ✅ |
| 12 | Provider selection (models) | ⬜ **Phase 10** |
| 13 | Golden corpus assembled | ⬜ **still blocking S1** |
| 14 | Phase 5 approved | ⬜ |

---

## 30. Open Questions

1. ⚠️ **Authentication build-vs-buy** — §14 is the closest call in this document and the one I'd
   most like your input on. Build in-house (my recommendation, for ADR-0017's specific semantics),
   or adopt Clerk and accept less control? *(Cheap to change now; unpleasant after S5.)*
2. **Cost ceiling confirmation** — the $150/month NFR has thin headroom once AI inference is
   included. Is $150 a hard ceiling, or a target with room to move to ~$250 if the product is
   working?
3. **Hosting** — comfortable starting on Render, or would you prefer to begin on AWS ap-south-1
   and absorb the setup cost now to avoid a migration later? *(My recommendation: Render. The
   migration is bounded, and weeks of infrastructure work at MVP is weeks not spent on parsing
   quality — which is what actually determines whether this product works.)*
4. ⚠️ **Golden corpus** — now blocking three separate things (S1's gate, R-21, R-32). This is the
   most urgent unanswered question in the project.
5. Still open: commercial intent, geography, team size, retention window, locale default.

---

## 31. Phase 5 Summary

| Question | Answer |
|---|---|
| **Backend?** | Python 3.12 + FastAPI + Pydantic v2 + SQLAlchemy — chosen for the document-parsing ecosystem, which is the riskiest component |
| **Why not Node?** | Strong on everything except the one thing that decides this product: PDF/DOCX/NLP libraries |
| **Why not Django?** | Its conventions fight hexagonal layering, and its admin's expose-everything default fights FR-ADM-006 |
| **Database?** | PostgreSQL only — ACID for the ledger, JSONB for flexibility, pgvector for embeddings. **One system instead of three** |
| **Broker?** | Redis, because Postgres already holds job truth — the broker is transport, not durability. Kafka rejected as the wrong tool |
| **Frontend?** | Next.js + TypeScript strict — **SEO decides it**, since distribution is our binding constraint |
| **Hosting?** | Render now (its primitives *are* our architecture); AWS ap-south-1 on a documented residency or scale trigger |
| **Notable finding?** | **The best PDF library is AGPL** — rejected on licensing, with a costed commercial fallback |
| **Thinnest margin?** | Cost. $37–178/month against a $150 ceiling; the cache and circuit breaker are what hold it |
| **Closest call?** | Authentication build-vs-buy (§14) — genuinely arguable, and I'd like your call |

---

**Do you approve this phase? Shall we move to the next one?**
