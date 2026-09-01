# Deployment — Vercel (client) + Render (API and database)

**Status:** 🟡 Client live · API awaiting a one-time Render step

| | |
|---|---|
| **Client** | https://careerpilot-seven-tau.vercel.app — live |
| **API** | Not yet created. One step, below. |
| **Repository** | https://github.com/dxhanjay/Summer-Project-1 |

---

## Why two platforms

Vercel cannot run this backend. It hosts static sites, SSR frameworks, and
serverless functions — Node, Python, Go, Ruby. A Spring Boot service needs a
persistent JVM, a HikariCP pool holding open PostgreSQL connections, and a
background poller that claims parse jobs on a timer. None of those survive in a
function that is created per request and destroyed after it.

So the client goes to Vercel, which it suits perfectly, and the API goes to a
platform that runs containers.

**The single-image option still exists and is still preferred where one host can
run both.** The root `Dockerfile` compiles the React client into the Spring Boot
jar as static resources, giving one origin, one deploy, no CORS, and no API base
URL to misconfigure. Use it on Railway, Fly, Cloud Run, or any container host.
The split below exists because Vercel was the requirement.

---

## Step 1 — Create the API and database on Render

`render.yaml` at the repository root is a Blueprint: it declares the web service,
the PostgreSQL database, and every environment variable including the wiring
between them. Render reads it and creates both.

1. Go to https://dashboard.render.com/blueprints
2. **New Blueprint Instance**
3. Connect GitHub and pick **dxhanjay/Summer-Project-1**, branch `master`
4. Render shows what it will create — `careerpilot-api` and `careerpilot-db` —
   and reports any variables it needs. Nothing should be missing; the Cloudinary
   three are marked optional and may be left blank.
5. **Apply**

The first build takes roughly 8–12 minutes: it compiles the React client with
npm, then the backend with Maven, then produces a JRE runtime image.

**What to look for in the deploy log, in order:**

```
Successfully applied 10 migrations to schema "public"
Started CareerPilotApplication in N seconds
```

Ten migrations, not nine. `V10__hash_columns_to_varchar.sql` is the one that lets
Hibernate's schema validation pass at all — without it the application refuses to
start, which is exactly what it did the first time it met a real database.

Then check the health endpoint:

```bash
curl https://careerpilot-api.onrender.com/actuator/health/readiness
# {"status":"UP"}
```

---

## Step 2 — Point the client at the API

Once Render reports the service live, copy its URL (something like
`https://careerpilot-api.onrender.com`) and set it on Vercel:

```bash
cd frontend
vercel env add VITE_API_BASE_URL production
# paste the Render URL, with no trailing slash
vercel deploy --prod
```

The value is read at build time and inlined into the bundle, so it needs a
redeploy rather than a restart.

If the Render service ends up with a different name, update
`APP_CORS_ALLOWED_ORIGINS` and `APP_FRONTEND_BASE_URL` on Render to match the
Vercel origin. Those two values and `VITE_API_BASE_URL` are the entire contract
between the halves; a mismatch shows up in the browser as a CORS error, which
looks like a server outage and is not one.

---

## Step 3 — Verify

```bash
API=https://careerpilot-api.onrender.com

curl -s $API/actuator/health/readiness

curl -s -X POST $API/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"a-long-enough-password","fullName":"Your Name"}'

curl -s -X POST $API/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"a-long-enough-password"}'
```

Then in the browser, at the Vercel URL: register, sign in, upload a PDF resume,
watch the status move from *Reading…* to *Ready*, and open it. The **What the
machine saw** tab is the one worth checking — if the extracted text and the
recognised sections are there, the whole pipeline behind it worked.

---

## Known limitations of the free tiers

These are real, and worth knowing before you show the site to anyone.

**The API sleeps after 15 minutes of inactivity.** Render's free plan spins the
container down, and a JVM cold start takes 40–60 seconds. The first request after
a quiet period will look like the site is broken. It is not; it is waking up.
The paid Starter plan removes this.

**Uploaded resumes do not survive a redeploy.** The free plan has no persistent
disk, so `APP_STORAGE_PROVIDER=local` writes into the container filesystem and
loses everything when it restarts. The application says so in the startup log
rather than degrading quietly:

```
WARN  Local file storage is active in the PROD profile ... uploaded resumes will
      be LOST on the next redeploy or restart
```

To fix it, sign up at cloudinary.com (free, no card) and set four variables on
Render:

```
APP_STORAGE_PROVIDER=cloudinary
CLOUDINARY_CLOUD_NAME=...
CLOUDINARY_API_KEY=...
CLOUDINARY_API_SECRET=...
```

**Render's free PostgreSQL expires after 30 days.** Render deletes it and the
data with it. Upgrade the database, or plan to recreate it.

**Email verification is off.** `AUTH_REQUIRE_EMAIL_VERIFICATION=false`, because
no SMTP provider is configured and with it on every new account would sit at
PENDING waiting for a message that is never sent. To enable it, set `MAIL_HOST`,
`MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` and `MAIL_FROM` — any provider
works, it is plain SMTP — and flip the flag in the same change.

---

## Making yourself an administrator

The admin console at `/admin` requires `ROLE_ADMIN`. There is no seeded admin
account, deliberately: a default password is a well-known credential on every
deployment that forgets to change it, and "first user to register wins" is a race
anyone who finds the URL early can win.

1. Register normally through the site
2. On Render, set `APP_ADMIN_BOOTSTRAP_EMAILS` to that address
3. Restart the service

The grant is applied at startup and is additive — removing the address later does
not demote anyone, because revoking a role should be a deliberate action rather
than a side effect of editing a variable.

---

## What is deployed where

| | Vercel | Render |
|---|---|---|
| React client | ✅ | |
| REST API | | ✅ |
| PostgreSQL | | ✅ |
| Resume parsing (PDFBox, Tika) | | ✅ |
| ATS scoring, job matching, interview | | ✅ |
| Background job poller | | ✅ |

No AI provider is configured and none is needed. ATS scoring, job matching, and
interview evaluation are deterministic rule engines (ADR-0029), so there is no
API key to obtain, no per-request cost, and no model whose output changes between
two runs of the same input.
