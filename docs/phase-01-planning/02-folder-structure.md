# Phase 1.2 — Folder Structure

**Status:** 📝 Awaiting approval

---

## 1. Why decide this before writing code

Package structure is the cheapest thing to get right on day one and among the most expensive to
retrofit. Once a controller reaches directly into a repository, that shortcut is copied by the next
twenty controllers, and by month three "we use Clean Architecture" is a comment in the README rather
than a property of the code.

Two decisions below carry most of the weight:

1. **Package by feature first, layer second.** `com.careerpilot.resume.api` rather than
   `com.careerpilot.api.resume`. Feature-first means a change to resume handling touches one
   top-level directory. Layer-first means every feature is smeared across five, and the compiler
   cannot help you keep features from entangling.
2. **The domain layer imports nothing infrastructural.** No `jakarta.persistence`, no Spring, no
   Cloudinary SDK. This is what makes the scoring rubric testable in CI with no database and no API
   key — a plain JUnit test with plain objects. It is enforced mechanically (ArchUnit, §5), because
   a rule nobody checks is a rule nobody follows.

---

## 2. Repository root

```
careerpilot-ai/
├── backend/                 Spring Boot application (Maven)
├── frontend/                React + Vite SPA
├── docs/                    Design documents, one directory per phase group
├── postman/                 Exported Postman collection + environments
├── docker/                  Dockerfile(s) and docker-compose for local dev
├── .github/
│   └── workflows/           CI: build, test, lint, security scan
├── .gitignore
└── README.md
```

Monorepo, deliberately. Backend and frontend version together, one PR can change an endpoint and its
caller, and there is one CI pipeline to reason about. The cost is that the Vercel and Railway builds
each need a root directory setting — a one-line configuration, paid once.

---

## 3. Backend

```
backend/
├── pom.xml
├── mvnw / mvnw.cmd                 Maven wrapper — CI uses the same Maven as you do
├── src/
│   ├── main/
│   │   ├── java/com/careerpilot/
│   │   │   ├── CareerPilotApplication.java
│   │   │   │
│   │   │   ├── common/                       ← shared kernel; depends on nothing else
│   │   │   │   ├── exception/
│   │   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   │   ├── ApiException.java
│   │   │   │   │   ├── ResourceNotFoundException.java
│   │   │   │   │   ├── BusinessRuleViolationException.java
│   │   │   │   │   └── ErrorResponse.java
│   │   │   │   ├── dto/
│   │   │   │   │   ├── ApiResponse.java       Uniform envelope
│   │   │   │   │   └── PageResponse.java      Never leak Spring's Page
│   │   │   │   ├── audit/
│   │   │   │   │   └── AuditableEntity.java   createdAt / updatedAt / createdBy
│   │   │   │   ├── util/
│   │   │   │   └── validation/                Custom @Constraint annotations
│   │   │   │
│   │   │   ├── config/                       ← composition root
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   ├── OpenApiConfig.java
│   │   │   │   ├── AsyncConfig.java           Task executor for jobs
│   │   │   │   ├── CorsConfig.java
│   │   │   │   ├── RateLimitConfig.java
│   │   │   │   └── properties/                @ConfigurationProperties records
│   │   │   │       ├── JwtProperties.java
│   │   │   │       ├── CloudinaryProperties.java
│   │   │   │       └── ClaudeProperties.java
│   │   │   │
│   │   │   ├── auth/                          ← feature module
│   │   │   │   ├── api/
│   │   │   │   │   ├── AuthController.java
│   │   │   │   │   └── dto/
│   │   │   │   │       ├── RegisterRequest.java
│   │   │   │   │       ├── LoginRequest.java
│   │   │   │   │       ├── TokenResponse.java
│   │   │   │   │       └── RefreshRequest.java
│   │   │   │   ├── application/
│   │   │   │   │   ├── AuthService.java
│   │   │   │   │   ├── TokenService.java
│   │   │   │   │   └── port/
│   │   │   │   │       └── EmailSender.java   Interface; impl lives in infrastructure
│   │   │   │   ├── domain/
│   │   │   │   │   ├── User.java
│   │   │   │   │   ├── Role.java
│   │   │   │   │   ├── RefreshToken.java
│   │   │   │   │   └── PasswordResetToken.java
│   │   │   │   └── infrastructure/
│   │   │   │       ├── UserRepository.java
│   │   │   │       ├── RoleRepository.java
│   │   │   │       ├── RefreshTokenRepository.java
│   │   │   │       ├── JwtTokenProvider.java
│   │   │   │       ├── JwtAuthenticationFilter.java
│   │   │   │       └── SmtpEmailSender.java
│   │   │   │
│   │   │   ├── profile/          same api/application/domain/infrastructure shape
│   │   │   ├── resume/
│   │   │   ├── parsing/
│   │   │   ├── analysis/                      ATS scoring
│   │   │   ├── builder/
│   │   │   ├── matching/                      JD matching + rewriting
│   │   │   ├── interview/
│   │   │   ├── notification/
│   │   │   ├── admin/
│   │   │   │
│   │   │   ├── ai/                            ← shared AI infrastructure
│   │   │   │   ├── AiClient.java              Port — the rest of the app sees only this
│   │   │   │   ├── ClaudeAiClient.java        Adapter
│   │   │   │   ├── PromptTemplate.java
│   │   │   │   ├── AiUsageRecorder.java       Every call logged for cost control
│   │   │   │   └── guard/
│   │   │   │       └── EntityDiffGuard.java   Detects fabricated experience
│   │   │   │
│   │   │   ├── storage/
│   │   │   │   ├── FileStorage.java           Port
│   │   │   │   └── CloudinaryFileStorage.java Adapter
│   │   │   │
│   │   │   └── jobs/                          ← async execution
│   │   │       ├── domain/Job.java
│   │   │       ├── JobService.java
│   │   │       ├── JobRepository.java
│   │   │       └── JobPoller.java             @Scheduled reclaim of stuck jobs
│   │   │
│   │   └── resources/
│   │       ├── application.yml                Shared config, no secrets
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       ├── db/migration/                  Flyway — V1__init.sql, V2__…
│   │       └── prompts/                       Versioned prompt templates
│   │
│   └── test/
│       ├── java/com/careerpilot/
│       │   ├── architecture/ArchitectureTest.java     ArchUnit rules — see §5
│       │   ├── auth/…                                 Mirrors main
│       │   └── support/
│       │       ├── AbstractIntegrationTest.java       Testcontainers Postgres
│       │       └── TestDataFactory.java
│       └── resources/
│           ├── application-test.yml
│           └── fixtures/resumes/                      Adversarial parse corpus
```

### Why each layer exists

| Layer | Contains | May depend on | Must never contain |
|---|---|---|---|
| `api` | Controllers, request/response DTOs, mappers | `application`, `common` | Business logic, JPA entities |
| `application` | Use-case services, transaction boundaries, ports | `domain`, `common` | HTTP types, SQL, vendor SDKs |
| `domain` | Entities, value objects, domain rules | `common` only | Spring, JPA, anything with a network call |
| `infrastructure` | Repositories, adapters, filters | all of the above | Business decisions |

The dependency rule points inward. `domain` is the only layer with no outward edges, which is
precisely why it is the layer worth protecting.

**One pragmatic compromise, stated openly:** domain entities carry JPA annotations rather than being
pure POJOs with a separate persistence model. A strict hexagonal design would keep them separate and
map between them. For a 1–2 person team that mapping layer is a large, permanent tax for a benefit
that mostly matters when you swap databases — which we will not do. We keep the *dependency
direction* honest and accept the annotation coupling. This is a trade, and it is recorded as one so
that nobody later mistakes it for an oversight.

---

## 4. Frontend

```
frontend/
├── package.json
├── vite.config.js
├── tailwind.config.js
├── .env.example                    VITE_API_BASE_URL
├── index.html
└── src/
    ├── main.jsx
    ├── App.jsx
    ├── router/
    │   ├── index.jsx
    │   ├── ProtectedRoute.jsx
    │   └── AdminRoute.jsx
    ├── api/
    │   ├── client.js               Axios instance: baseURL, interceptors
    │   ├── authApi.js
    │   ├── resumeApi.js
    │   ├── analysisApi.js
    │   ├── matchingApi.js
    │   └── interviewApi.js
    ├── context/
    │   ├── AuthContext.jsx
    │   └── ToastContext.jsx
    ├── hooks/
    │   ├── useAuth.js
    │   ├── useJobPolling.js        Polls async job status
    │   └── useDebounce.js
    ├── components/
    │   ├── ui/                     Button, Input, Card, Modal, Spinner, Badge
    │   ├── layout/                 Navbar, Sidebar, Footer, PageShell
    │   └── feature/
    │       ├── resume/
    │       ├── analysis/
    │       └── interview/
    ├── pages/
    │   ├── auth/                   Login, Register, ForgotPassword, ResetPassword
    │   ├── dashboard/
    │   ├── resume/                 Upload, List, ParseReview, Analysis
    │   ├── builder/
    │   ├── matching/
    │   ├── interview/              Setup, Session, Report, History
    │   ├── profile/
    │   └── admin/
    ├── lib/
    │   ├── validators.js           Zod / yup schemas for React Hook Form
    │   └── formatters.js
    └── styles/index.css
```

`api/client.js` is the only file that knows the backend exists. Every request goes through one Axios
instance with one interceptor pair: attach the access token on the way out, transparently refresh on
a 401 on the way back. Scattering `axios.get` across components means the refresh logic gets
implemented four times and forgotten in the fifth place.

---

## 5. Enforcing the structure

Conventions decay. These do not, because CI fails:

**ArchUnit** (`src/test/java/com/careerpilot/architecture/ArchitectureTest.java`):

| Rule | Catches |
|---|---|
| `domain` must not depend on Spring, JPA, or any `..infrastructure..` | Business logic acquiring a database dependency |
| `api` must not depend on `..infrastructure..` | Controllers reaching past the service layer |
| No feature package may depend on another feature's `domain` or `infrastructure` | Cross-feature entanglement; features may talk only through `application` ports |
| Classes named `*Controller` must reside in `..api..` | Drift |
| No class annotated `@Entity` may be a controller return type | **NFR-SEC-05** — entity leakage |
| No field injection (`@Autowired` on a field) | Untestable classes and hidden dependencies |

That sixth rule is worth singling out. "Never expose entities" is stated in every code-review
checklist and violated in every codebase that only states it. An ArchUnit rule turns it into a build
failure.

**Additional CI gates:** Spotless (format), Checkstyle (style), JaCoCo (coverage floor per
NFR-TEST-01), OWASP Dependency-Check (known-vulnerable dependencies), ESLint + Prettier on the
frontend.

---

## 6. Naming conventions

| Kind | Pattern | Example |
|---|---|---|
| Controller | `<Feature>Controller` | `ResumeController` |
| Service | `<Feature>Service` | `ResumeService` |
| Repository | `<Entity>Repository` | `ResumeRepository` |
| Request DTO | `<Action><Entity>Request` | `UploadResumeRequest` |
| Response DTO | `<Entity>Response` | `ResumeResponse` |
| Mapper | `<Entity>Mapper` | `ResumeMapper` |
| Port (interface) | Capability noun | `FileStorage`, `AiClient` |
| Adapter | `<Vendor><Port>` | `CloudinaryFileStorage` |
| Migration | `V<n>__<snake_case>.sql` | `V3__add_resume_checksum.sql` |
| Test | `<Class>Test` / `<Class>IT` | `ResumeServiceTest`, `ResumeControllerIT` |
| React component | `PascalCase.jsx` | `ResumeCard.jsx` |
| React hook | `useCamelCase.js` | `useJobPolling.js` |

`Test` is a unit test — fast, no Spring context. `IT` is an integration test — Spring context and a
Testcontainers Postgres. Splitting them by suffix lets the fast suite run on every save and the slow
suite on every push.
