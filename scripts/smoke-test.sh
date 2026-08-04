#!/usr/bin/env bash
#
# Verifies a CareerPilot AI deployment end to end.
#
# POSIX counterpart to smoke-test.ps1 — same checks, same exit semantics, so CI
# on Linux and a developer on Windows verify identically. Exits non-zero if any
# check fails.
#
# The final rotation check is the important one: it proves refresh-token reuse
# detection survived deployment. That mechanism fails silently — when it breaks,
# logins still succeed and nobody notices until a stolen token is used.
#
# Usage:
#   ./scripts/smoke-test.sh https://careerpilot-api.up.railway.app
#   ./scripts/smoke-test.sh http://localhost:8080

set -uo pipefail

BASE_URL="${1:-}"
if [[ -z "$BASE_URL" ]]; then
  echo "usage: $0 <base-url>" >&2
  exit 2
fi
BASE_URL="${BASE_URL%/}"

command -v jq >/dev/null 2>&1 || { echo "jq is required: https://jqlang.github.io/jq/" >&2; exit 2; }

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[0;33m'; GREY='\033[0;90m'; NC='\033[0m'
PASSED=0; FAILED=0

result() {           # result <0|1> <name> [detail]
  if [[ "$1" -eq 0 ]]; then
    PASSED=$((PASSED + 1)); printf "  ${GREEN}PASS${NC}  %s" "$2"
  else
    FAILED=$((FAILED + 1)); printf "  ${RED}FAIL${NC}  %s" "$2"
  fi
  [[ -n "${3:-}" ]] && printf "  ${GREY}(%s)${NC}" "$3"
  printf "\n"
}

# Writes the body to $BODY and echoes the status code. --fail is deliberately
# NOT used: several checks expect a 4xx and must assert it rather than abort.
BODY=""
api() {              # api <METHOD> <PATH> [JSON_BODY] [BEARER]
  local method="$1" path="$2" json="${3:-}" bearer="${4:-}"
  local args=(-s -o /tmp/cp_smoke_body -w '%{http_code}' -X "$method"
              -H 'Content-Type: application/json' --max-time 60)
  [[ -n "$bearer" ]] && args+=(-H "Authorization: Bearer $bearer")
  [[ -n "$json"   ]] && args+=(-d "$json")
  local code; code=$(curl "${args[@]}" "$BASE_URL$path")
  BODY=$(cat /tmp/cp_smoke_body)
  echo "$code"
}

jsonf() { echo "$BODY" | jq -r "$1" 2>/dev/null || echo ""; }
uuid()  { cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen | tr '[:upper:]' '[:lower:]'; }

echo
echo -e "${YELLOW}CareerPilot AI - deployment smoke test${NC}"
echo "Target: $BASE_URL"
echo

# ---------------------------------------------------------------------------
echo -e "${YELLOW}Health${NC}"

code=$(api GET /actuator/health)
[[ "$code" == "200" && "$(jsonf '.status')" == "UP" ]]; result $? "health endpoint reports UP" "HTTP $code"

code=$(api GET /actuator/health/readiness)
[[ "$code" == "200" ]]; result $? "readiness probe passes (database reachable)" "HTTP $code"

# ---------------------------------------------------------------------------
echo
echo -e "${YELLOW}Registration${NC}"

EMAIL="smoke-$(uuid)@example.com"
PASSWORD="a-long-enough-password"
REG_JSON=$(printf '{"email":"%s","password":"%s","fullName":"Smoke Test"}' "$EMAIL" "$PASSWORD")

code=$(api POST /api/v1/auth/register "$REG_JSON")
[[ "$code" == "201" ]]; result $? "registration returns 201" "HTTP $code"

echo "$BODY" | jq -e '.data.roles | index("ROLE_USER")' >/dev/null 2>&1
result $? "ROLE_USER granted at registration"

# NFR-SEC-05 checked on the wire, so a field added to the entity later cannot
# start leaking without failing here.
LEAKED=""
for field in passwordHash password failedLoginAttempts aiCreditsUsedMonth lockedUntil; do
  grep -q "$field" <<<"$BODY" && LEAKED="$LEAKED $field"
done
[[ -z "$LEAKED" ]]; result $? "response leaks no internal fields" "${LEAKED:-}"

code=$(api POST /api/v1/auth/register "$REG_JSON")
[[ "$code" == "409" ]]; result $? "duplicate address rejected with 409" "HTTP $code"

code=$(api POST /api/v1/auth/register '{"email":"not-an-email","password":"short","fullName":"X"}')
[[ "$code" == "422" ]]; result $? "invalid payload rejected with 422" "HTTP $code"

# ---------------------------------------------------------------------------
echo
echo -e "${YELLOW}Login${NC}"

LOGIN_JSON=$(printf '{"email":"%s","password":"%s"}' "$EMAIL" "$PASSWORD")
code=$(api POST /api/v1/auth/login "$LOGIN_JSON")
[[ "$code" == "200" ]]; result $? "login returns 200" "HTTP $code"

ACCESS_TOKEN=$(jsonf '.data.accessToken')
REFRESH_TOKEN=$(jsonf '.data.refreshToken')
[[ -n "$ACCESS_TOKEN" && "$ACCESS_TOKEN" != "null" ]]; result $? "access token issued"
[[ -n "$REFRESH_TOKEN" && "$REFRESH_TOKEN" != "null" ]]; result $? "refresh token issued"

code=$(api POST /api/v1/auth/login "$(printf '{"email":"%s","password":"wrong-password-here"}' "$EMAIL")")
WRONG_CODE="$code"; WRONG_ERR=$(jsonf '.data.code')
[[ "$code" == "401" ]]; result $? "wrong password rejected with 401" "HTTP $code"

code=$(api POST /api/v1/auth/login "$(printf '{"email":"nobody-%s@example.com","password":"wrong-password-here"}' "$(uuid)")")
UNKNOWN_ERR=$(jsonf '.data.code')
[[ "$code" == "$WRONG_CODE" && "$UNKNOWN_ERR" == "$WRONG_ERR" ]]
result $? "unknown address is indistinguishable from wrong password"

# ---------------------------------------------------------------------------
echo
echo -e "${YELLOW}Authorization${NC}"

code=$(api GET /api/v1/auth/me)
[[ "$code" == "401" ]]; result $? "anonymous request rejected with 401" "HTTP $code"
[[ "$(jsonf '.data.code')" == "UNAUTHORIZED" ]]; result $? "401 uses the standard error envelope"

code=$(api GET /api/v1/auth/me "" "not.a.real.token")
[[ "$code" == "401" ]]; result $? "forged token rejected with 401" "HTTP $code"

code=$(api GET /api/v1/auth/me "" "$ACCESS_TOKEN")
[[ "$code" == "200" && "$(jsonf '.data.email')" == "$EMAIL" ]]
result $? "valid token returns the current user" "HTTP $code"

# ---------------------------------------------------------------------------
echo
echo -e "${YELLOW}Refresh token rotation${NC}"

code=$(api POST /api/v1/auth/refresh "$(printf '{"refreshToken":"%s"}' "$REFRESH_TOKEN")")
[[ "$code" == "200" ]]; result $? "refresh returns 200" "HTTP $code"
ROTATED=$(jsonf '.data.refreshToken')

[[ -n "$ROTATED" && "$ROTATED" != "$REFRESH_TOKEN" ]]
result $? "a different refresh token is issued (rotation)"

# --- the check that matters -------------------------------------------------
# Replay the consumed token. This is what a stolen token looks like. Both it AND
# the victim's current token must die.
code=$(api POST /api/v1/auth/refresh "$(printf '{"refreshToken":"%s"}' "$REFRESH_TOKEN")")
ERR=$(jsonf '.data.code')
[[ "$code" == "401" && "$ERR" == "TOKEN_REUSE_DETECTED" ]]
result $? "replaying a consumed token is detected as theft" "code: $ERR"

code=$(api POST /api/v1/auth/refresh "$(printf '{"refreshToken":"%s"}' "$ROTATED")")
[[ "$code" == "401" ]]
result $? "* the whole token family is revoked, not just the replayed token" "HTTP $code"

# ---------------------------------------------------------------------------
echo
echo -e "${YELLOW}Enumeration resistance${NC}"

code1=$(api POST /api/v1/auth/forgot-password "$(printf '{"email":"nobody-%s@example.com"}' "$(uuid)")")
code2=$(api POST /api/v1/auth/forgot-password "$(printf '{"email":"%s"}' "$EMAIL")")
[[ "$code1" == "202" && "$code2" == "202" ]]
result $? "forgot-password answers identically for known and unknown addresses"

# ---------------------------------------------------------------------------
echo
echo -e "${YELLOW}Production hardening${NC}"

code=$(api GET /v3/api-docs)
[[ "$code" != "200" ]]; result $? "OpenAPI document is not public" \
  "$([[ "$code" == "200" ]] && echo 'EXPOSED - is SPRING_PROFILES_ACTIVE=prod set?' || echo "HTTP $code")"

code=$(api GET /actuator/env)
[[ "$code" != "200" ]]; result $? "/actuator/env is not public" "HTTP $code"

# ---------------------------------------------------------------------------
rm -f /tmp/cp_smoke_body
echo
printf -- '-%.0s' {1..60}; echo
if [[ "$FAILED" -eq 0 ]]; then
  echo -e "${GREEN}$PASSED passed, 0 failed.${NC}"
  echo -e "${GREEN}Deployment verified.${NC}"
  exit 0
else
  echo -e "${RED}$PASSED passed, $FAILED FAILED.${NC}"
  echo -e "${YELLOW}See docs/phase-04-deployment/01-railway-deployment.md - Troubleshooting.${NC}"
  exit 1
fi
