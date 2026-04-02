#!/usr/bin/env bash
# api-smoke-tests.sh
#
# Runs a suite of HTTP smoke tests against the deployed API Gateway to verify
# the core endpoints are responding correctly.
#
# Usage:
#   bash scripts/github/api-smoke-tests.sh <api-gateway-url>
#
# Exit code: 0 if all tests pass, 1 if any fail.
# Appends a Markdown summary table to $GITHUB_STEP_SUMMARY when in CI.

set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: $0 <api-gateway-url>" >&2
  exit 1
fi

API_BASE="${1%/}"
echo "Testing API Gateway: ${API_BASE}"

SUMMARY=""
FAILED=0

check() {
  local label="$1" status="$2" expected="$3" extra="$4" body_file="${5:-}"
  if [ "${status}" = "${expected}" ] && { [ -z "${extra}" ] || eval "${extra}"; }; then
    echo "  ✅ ${label} → ${status}"
    SUMMARY="${SUMMARY}\n| ${label} | ✅ ${status} |"
  else
    echo "  ❌ ${label} → ${status} (expected ${expected})"
    [ -n "${body_file}" ] && [ -f "${body_file}" ] && echo "     body: $(cat "${body_file}")"
    SUMMARY="${SUMMARY}\n| ${label} | ❌ ${status} (expected ${expected}) |"
    FAILED=1
  fi
}

# GET /api/v1/health — must return 200 with {"status": ...}
STATUS=$(curl -s -o /tmp/health.json -w "%{http_code}" "${API_BASE}/api/v1/health")
check "GET /api/v1/health" "${STATUS}" "200" "grep -q '\"status\"' /tmp/health.json" /tmp/health.json

# GET /api/v1/puzzles/generate — must return 200 with originalGrid
STATUS=$(curl -s -o /tmp/generate.json -w "%{http_code}" "${API_BASE}/api/v1/puzzles/generate")
check "GET /api/v1/puzzles/generate" "${STATUS}" "200" "grep -q '\"originalGrid\"' /tmp/generate.json" /tmp/generate.json

# GET /api/v1/puzzles/generate?difficulty=hard — must return 200
STATUS=$(curl -s -o /tmp/generate_hard.json -w "%{http_code}" "${API_BASE}/api/v1/puzzles/generate?difficulty=hard")
check "GET /api/v1/puzzles/generate?difficulty=hard" "${STATUS}" "200" "" /tmp/generate_hard.json

# POST /api/v1/games without token — JWT authorizer must reject with 401
STATUS=$(curl -s -o /tmp/games_noauth.json -w "%{http_code}" -X POST "${API_BASE}/api/v1/games" \
  -H "Content-Type: application/json" -d '{"difficulty":"easy"}')
check "POST /api/v1/games (no token → 401)" "${STATUS}" "401" "" /tmp/games_noauth.json

# POST /api/v1/puzzles/import without token — JWT authorizer must reject with 401
STATUS=$(curl -s -o /tmp/import_noauth.json -w "%{http_code}" -X POST "${API_BASE}/api/v1/puzzles/import" \
  -H "Content-Type: application/json" -d '{"image":"dGVzdA=="}')
check "POST /api/v1/puzzles/import (no token → 401)" "${STATUS}" "401" "" /tmp/import_noauth.json

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "### API Smoke Tests"
    echo ""
    echo "| Endpoint | Result |"
    echo "|----------|--------|"
    printf "%b\n" "${SUMMARY}"
  } >> "$GITHUB_STEP_SUMMARY"
fi

[ "${FAILED}" = "0" ] || { echo "One or more API smoke tests failed"; exit 1; }
echo "All API smoke tests passed."
