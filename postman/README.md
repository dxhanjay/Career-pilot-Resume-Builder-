# Postman

## Files

| File | Purpose |
|---|---|
| `CareerPilot-AI.postman_collection.json` | The API collection, with assertions on every request |
| `CareerPilot-Local.postman_environment.json` | Points at `http://localhost:8080` |
| `CareerPilot-Railway.postman_environment.json` | Points at your Railway URL — **edit `baseUrl` after deploying** |

## Setup

1. Postman → **Import** → select all three files.
2. Choose an environment from the top-right selector.
3. For Railway, set `baseUrl` to your generated domain (no trailing slash).

Nothing else needs configuring. Tokens are captured automatically: `Login` and
`Refresh` write `accessToken` and `refreshToken` into the environment, and every
protected request reads `{{accessToken}}` from collection-level bearer auth. No
token is ever copied by hand.

## Running the whole thing

Collection Runner → select **Authentication** → Run.

The requests are numbered because they form one scenario:

```
Register → duplicate rejected → Login → wrong password → unknown address
        → Me → anonymous rejected → Refresh → replay → family revoked
```

Requests 9 and 10 deliberately trigger refresh-token reuse detection and revoke
the session. That is the intent, not a defect — they are the two requests that
prove the security mechanism actually works in the environment you are pointed
at. Re-run `Register` to begin a fresh scenario.

A `runEmail` is generated per run, so the collection can be executed repeatedly
against the same environment without colliding on the unique email index.

## What is not automated

`Verify email`, `Reset password`, and `Change password` need a token from an
emailed link, or would invalidate the scenario's other requests. They are
included with documentation and example bodies, but sit outside the ordered run.

## Relationship to the smoke test

[`scripts/smoke-test.ps1`](../scripts/smoke-test.ps1) and
[`scripts/smoke-test.sh`](../scripts/smoke-test.sh) check the same behaviour from
a terminal and exit non-zero on failure, which makes them usable as a deployment
gate. This collection is for exploring the API by hand and reading responses.

Use the scripts to answer *"did the deploy work?"*, and Postman to answer
*"what does this endpoint actually return?"*
