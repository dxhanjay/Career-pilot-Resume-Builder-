# ADR-0017: httpOnly cookie sessions with rotating refresh tokens and family revocation

- **Status:** Accepted
- **Date:** 2026-07-31
- **Phase:** 4
- **Deciders:** Project owner + engineering team

## Context

The session mechanism must satisfy several requirements simultaneously:

- FR-AUTH-005: short-lived access token plus a rotating refresh token.
- FR-AUTH-006: "log out of all sessions" must work.
- NFR-SEC-007: no unauthorised access to another user's resources.
- The data at stake is unusually sensitive — a compromised session exposes a complete employment
  history, contact details, and interview answers discussing failures and salary.

The common default in single-page applications is to store a JWT in `localStorage` and attach it
as an `Authorization` header. This is popular because it is simple and avoids CSRF entirely. It
has one serious weakness: **any XSS vulnerability, anywhere on the origin — including in a
third-party script — can read `localStorage` and exfiltrate the token.** The attacker then holds
a valid credential that cannot be revoked before expiry.

Our XSS exposure is not hypothetical. We render user-uploaded resume content, user-pasted job
descriptions, and model-generated text back into the page. That is three untrusted content
sources rendered into the DOM on the most-used screen in the product.

A second requirement pulls against stateless tokens: FR-AUTH-006 requires server-side
revocation. A pure stateless JWT cannot be revoked before expiry by definition — the server
holds no record of it.

## Options Considered

| Option | Pros | Cons |
|---|---|---|
| JWT in `localStorage` | Simple; no CSRF concern; stateless | **XSS reads it directly**; cannot revoke; poor fit for the sensitivity of this data |
| JWT in memory only | Not readable after reload; no persistence | Lost on refresh; requires a refresh call on every page load anyway; still XSS-readable while resident |
| **httpOnly cookies + rotating refresh + server-side session records (chosen)** | XSS cannot read the token; revocation works; theft becomes detectable | Requires CSRF defence; requires a session lookup |
| Opaque session ID only (classic sessions) | Simplest revocation story | Session lookup on every request with no short-lived-token optimisation |
| Third-party identity provider for everything | Offloads the problem | External dependency on the login path; cost; less control; still need local session semantics |

## Decision

**Sessions are carried in `httpOnly`, `Secure`, `SameSite` cookies. Access tokens are short-lived
(~15 minutes). Refresh tokens are long-lived, rotating, and tracked as a family.**

1. **httpOnly cookies** — JavaScript cannot read the credential, so an XSS bug cannot exfiltrate
   the session. Given that we render three untrusted content sources into the page, this is the
   decisive property.
2. **CSRF defence is mandatory** — cookies are sent automatically, so `SameSite=Lax` plus a
   double-submit token on state-changing requests is required. This is the accepted cost of the
   choice above, not an oversight.
3. **Refresh rotation with reuse detection** — each refresh invalidates the old token and issues
   a new one. If an already-used refresh token is presented, that implies theft, and **the entire
   session family is revoked**, not just the replayed token. Revoking only the replayed token
   would leave the attacker's descendant tokens valid.
4. **Server-side session records**, cached for speed, enable FR-AUTH-006 and immediate
   revocation.
5. **Admin sessions are separate and elevated**, with a shorter lifetime and re-authentication
   required for break-glass content access (FR-ADM-006, FR-ADM-007).
6. Login responses are constant-time and identical whether or not the email exists
   (FR-AUTH-008).

## Consequences

### Positive
- An XSS vulnerability no longer means automatic account takeover — the highest-value mitigation
  available given what we render.
- "Log out everywhere" works, and a compromised session can be killed immediately.
- Refresh-token theft becomes a *detectable event* rather than a silent compromise, and the
  response revokes the whole family.
- Session records give an audit trail of devices and locations, useful for both security review
  and user-facing session management.

### Negative / Costs
- CSRF protection must be implemented and maintained on every state-changing endpoint; forgetting
  it on one endpoint reintroduces the risk.
- Cookies complicate cross-origin API access, which constrains how the frontend is deployed
  (same-site deployment strongly preferred).
- A session lookup on token refresh means the system is not purely stateless — accepted, since
  FR-AUTH-006 makes statelessness impossible anyway.
- Mobile or third-party API clients later would need a separate token-based path.

### Follow-up actions required
- **Phase 6:** `sessions` table with `family_id`, device fingerprint, hashed IP, issued/expiry
  timestamps, and revocation state; refresh tokens stored hashed, never in plaintext.
- **Phase 12:** CSRF middleware applied to all state-changing routes by default, with an explicit
  opt-out list rather than an opt-in list; reuse detection implemented at the refresh endpoint.
- **Phase 13:** frontend deployed same-site with the API so cookies work without cross-origin
  complications; no token ever touches `localStorage`.
- **Phase 17:** strict Content-Security-Policy to reduce XSS likelihood in the first place —
  cookies mitigate the consequence, CSP reduces the cause; both are needed.
- **Phase 19:** tests assert that a replayed refresh token revokes the whole family, and that
  logout-all invalidates every session immediately.
