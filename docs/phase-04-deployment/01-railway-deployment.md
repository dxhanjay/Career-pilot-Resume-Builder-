# Phase 4.1 — Railway Deployment Guide

**Status:** 📝 Ready to execute
**You drive this phase.** I have prepared everything that can be prepared; the
steps below need your Railway account.

---

## What "done" means

Phase 4 is complete when **all six** of these are true. Not five.

| # | Check | How you know |
|---|---|---|
| 1 | The image builds on Railway | Deploy log ends with a successful build |
| 2 | The container starts and stays up | No restart loop; log shows `Started CareerPilotApplication` |
| 3 | Flyway applied V1 and V2 | Log shows `Successfully applied 2 migrations` |
| 4 | `/actuator/health` returns `{"status":"UP"}` | `curl` against the public URL |
| 5 | Registration and login work end to end | The smoke test passes |
| 6 | Refresh-token reuse detection works in production | The smoke test's final check passes |

Check 6 matters as much as the rest. Everything else proves the app is running;
that one proves the security mechanism survived the trip.

---

## Before you start

- A GitHub account with this repository pushed
- A Railway account (railway.app — the free trial is sufficient)
- Your `JWT_SECRET`, generated for you during this phase. Keep it somewhere safe;
  it is not in the repository and cannot be recovered from it.

---

## Step 1 — Push to GitHub

Railway deploys from a Git repository, so this must happen first.

```bash
git remote -v          # if empty, add your remote:
git remote add origin https://github.com/<you>/careerpilot-ai.git
git push -u origin master
```

**Before pushing, confirm no secret is in the repository:**

```bash
git log --all -p -- backend/.env | head -20     # must print nothing
```

If that prints anything, a `.env` was committed at some point. The file being
deleted later does not help — it is still in history, and the credential must be
rotated rather than hidden.

---

## Step 2 — Create the project and the database

1. Railway → **New Project** → **Deploy PostgreSQL**.
2. Wait for it to provision. Note the service name — it is usually **Postgres**,
   and Step 4 references it by exactly that name.

Provision the database *before* the application. The app's readiness probe
depends on a reachable database, so deploying it first produces a service that
fails its health check and restarts in a loop — which looks like an application
bug and is not one.

---

## Step 3 — Add the backend service

1. In the same project: **New** → **GitHub Repo** → select this repository.
2. Open the new service → **Settings**.
3. Set **Root Directory** to `backend`.

**Root Directory is the step most likely to be skipped, and skipping it fails
the build.** This is a monorepo: at the repository root there is no `pom.xml`
and no `Dockerfile`, so Railway finds nothing to build. Setting it to `backend`
makes both visible and makes `backend/railway.json` take effect.

`railway.json` then supplies the rest — Dockerfile builder, health check path,
restart policy. You do not need to set a build or start command by hand; if you
do, it overrides the Dockerfile's `ENTRYPOINT` and will point at a jar path that
does not exist inside the image.

---

## Step 4 — Environment variables

Service → **Variables** → **Raw Editor**, and paste this block.

```env
SPRING_PROFILES_ACTIVE=prod

SPRING_DATASOURCE_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
SPRING_DATASOURCE_USERNAME=${{Postgres.PGUSER}}
SPRING_DATASOURCE_PASSWORD=${{Postgres.PGPASSWORD}}

JWT_SECRET=<paste the generated secret>
JWT_ACCESS_TOKEN_TTL=15m
JWT_REFRESH_TOKEN_TTL=7d

AUTH_REQUIRE_EMAIL_VERIFICATION=false
AUTH_MAX_FAILED_LOGIN_ATTEMPTS=5
AUTH_LOCKOUT_DURATION=15m
AUTH_VERIFICATION_TOKEN_TTL=24h
AUTH_PASSWORD_RESET_TOKEN_TTL=1h

APP_FRONTEND_BASE_URL=http://localhost:5173
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173

MAIL_FROM=no-reply@careerpilot.ai
MAIL_HOST=localhost
MAIL_PORT=1025
MAIL_SMTP_AUTH=false
MAIL_SMTP_STARTTLS=false

DB_POOL_MAX_SIZE=5
DB_POOL_MIN_IDLE=1
```

### ⚠ The one that catches everyone: `SPRING_DATASOURCE_URL`

Railway's Postgres plugin publishes `DATABASE_URL` in **libpq** format:

```
postgresql://user:password@host:5432/railway
```

That is **not a JDBC URL.** Setting `SPRING_DATASOURCE_URL=${{Postgres.DATABASE_URL}}`
produces:

```
Driver org.postgresql.Driver claims to not accept jdbcUrl, postgresql://...
```

which reads like a driver problem and is a URL-format problem. The block above
builds a proper `jdbc:postgresql://` URL from Railway's individual `PG*`
reference variables instead. That is why three variables are used rather than one.

### Why some values are what they are

| Variable | Reason |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | Without it the app runs the **dev** profile: Swagger exposed, `/actuator/env` reachable, SQL logged. |
| `AUTH_REQUIRE_EMAIL_VERIFICATION=false` | No SMTP provider is configured yet, so verification emails cannot be delivered — enforcing it would make every account unable to log in. Set it to `true` the moment SMTP is real. |
| `DB_POOL_MAX_SIZE=5` | Railway's starter Postgres allows relatively few connections. A pool of 10 plus a second container during a rolling deploy can exhaust them, and the symptom is a failed deploy rather than a pool error. |
| `MAIL_*` pointing at localhost | Mail sending is `@Async` and swallows delivery failures, so the app runs correctly with no mail server. Registration succeeds; the email silently fails and is logged. |
| `APP_CORS_ALLOWED_ORIGINS` | Update to the Vercel domain in Phase 11. A wrong value here breaks the browser only — Postman and curl still work, which makes it confusing to diagnose. |

**`JWT_SECRET` has no default and no fallback.** If it is missing, the
application refuses to start, and the log says so explicitly. That is deliberate:
a predictable signing key lets anyone mint a token claiming `ROLE_ADMIN`.

---

## Step 5 — Deploy and read the log

Railway builds on save. Open **Deployments** → the active one → **View Logs**.

A healthy startup contains, in order:

```
Flyway Community Edition ... by Redgate
Successfully validated 2 migrations
Migrating schema "public" to version "1 - enable extensions"
Migrating schema "public" to version "2 - create auth tables"
Successfully applied 2 migrations
Tomcat started on port 8080
Started CareerPilotApplication in 12.4 seconds
```

If you see `Successfully applied 2 migrations`, the database connection, the
credentials, and the schema are all correct — that single line settles most of
what could go wrong.

---

## Step 6 — Expose a public URL

Settings → **Networking** → **Generate Domain**.

Railway sets `PORT` itself; the application reads it via `server.port=${PORT:8080}`.
Do **not** set `PORT` manually — overriding it with a value Railway is not routing
to produces a health check against a port nothing is listening on, and an endless
restart loop with no error in the application log.

---

## Step 7 — Verify

Quick check first:

```bash
curl https://<your-app>.up.railway.app/actuator/health
# {"status":"UP"}
```

Then the real one. From the repository root:

```powershell
.\scripts\smoke-test.ps1 -BaseUrl "https://<your-app>.up.railway.app"
```

```bash
./scripts/smoke-test.sh https://<your-app>.up.railway.app
```

The script exercises health, registration, duplicate rejection, login, wrong
password, an authenticated request, an anonymous rejection, token rotation, and
**refresh-token reuse detection**. It prints a pass/fail line per check and exits
non-zero if any fails.

Phase 4 is done when every line is green.

---

## Troubleshooting

Ordered by how often each actually happens.

### Build fails: `pom.xml not found` / `Dockerfile does not exist`

**Root Directory** is not set to `backend`. Step 3.

### Startup fails: `Driver claims to not accept jdbcUrl`

`SPRING_DATASOURCE_URL` is a libpq URL. Use the three-variable form from Step 4.

### Startup fails: `Could not resolve placeholder 'JWT_SECRET'`

The variable is missing. Add it. Working as designed.

### Startup fails: `JWT secret must be at least 32 characters`

The value is too short for HS256. Use the generated 64-character secret.

### Restart loop, no obvious error in the log

Nearly always the health check. Check in this order:

1. Is `PORT` set manually? Remove it.
2. Is the database reachable? A failed readiness probe is a killed container.
3. Did the app take longer than `healthcheckTimeout` (180s) to start? Cold JVM
   plus Flyway can be slow on a small instance.

### Container vanishes with no stack trace

Almost certainly an OOM kill by the kernel — the JVM never gets to log. The
Dockerfile sets `-XX:MaxRAMPercentage=75.0` to prevent the classic cause (the JVM
sizing a heap for the host's memory rather than the container's). If it persists,
raise the service's memory limit.

### `relation "users" does not exist`

Flyway did not run, but Hibernate's `ddl-auto: validate` did. Check the log for a
Flyway error above the failure — usually a permissions problem on the database
user, not a schema problem.

### `Validate failed: migration checksum mismatch`

An already-applied migration file was edited. **Do not edit applied migrations.**
Fix forward with a new `V3__...sql`. On a throwaway database you may instead drop
the schema and redeploy; never do that on one with real data.

### Health check passes, but every browser request fails with a CORS error

`APP_CORS_ALLOWED_ORIGINS` does not include the calling origin. Postman and curl
are unaffected because CORS is a browser mechanism, which is what makes this
confusing. Relevant from Phase 11 onward.

### First request after idle takes 10+ seconds

Railway sleeps idle containers on lower tiers. This is why the architecture makes
every long operation an async polled job (risk R5): a cold start delays the first
response rather than failing it.

---

## After it works

1. Tell me, and I will mark Phase 4 complete.
2. Record the public URL — Phase 11's frontend needs it as `VITE_API_BASE_URL`.
3. **Rotate `JWT_SECRET` if it was ever pasted anywhere shared** (a chat, a
   ticket, a screenshot). Rotation costs users nothing: their access tokens are
   invalidated and the client simply refreshes.

---

## Deliberately not done in this phase

- **Custom domain and TLS** — Railway's generated domain already has TLS. A
  custom domain belongs with the frontend in Phase 11.
- **A real SMTP provider** — needs a decision from you and a verified sending
  domain. Until then verification is off and mail failures are logged, not fatal.
- **Autoscaling and multiple replicas** — one replica is correct at zero traffic.
  The API is stateless and the job engine reclaims stale locks, so scaling later
  is a settings change, not a code change.
- **Monitoring and alerting** — Phase 14.
