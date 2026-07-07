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
#
# Cold-start handling: the Lambda signals a cold JVM via HTTP 503 on /health
# (status: "warming"). Endpoints that are expensive on a cold JVM (hard puzzle
# generation) may also transiently return 500. Both are retried up to
# RETRY_ATTEMPTS times with RETRY_DELAY_SECONDS between attempts — analogous
# to a Kubernetes readiness probe.

set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: $0 <api-gateway-url>" >&2
  exit 1
fi

API_BASE="${1%/}"
RETRY_ATTEMPTS="${SMOKE_RETRY_ATTEMPTS:-5}"
RETRY_DELAY="${SMOKE_RETRY_DELAY:-10}"

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

# Calls an endpoint and retries when the Lambda signals a cold start (503 on
# /health with status:warming) or returns a transient 500. Succeeds as soon as
# the expected status code is received.
#
# Usage: retry_check <label> <url> <expected_status> [extra_condition]
retry_check() {
  local label="$1" url="$2" expected="$3" extra="${4:-}"
  local body_file="/tmp/smoke_$(echo "$label" | tr ' ?/=' '_').json"
  local curl_extra=()

  local status attempt
  for attempt in $(seq 1 "${RETRY_ATTEMPTS}"); do
    status=$(curl -s -o "${body_file}" -w "%{http_code}" --max-time 30 \
      "${curl_extra[@]+"${curl_extra[@]}"}" "${url}" 2>/dev/null || echo "000")

    if [ "${status}" = "${expected}" ] && { [ -z "${extra}" ] || eval "${extra} ${body_file}"; }; then
      [ "${attempt}" -gt 1 ] && echo "  (succeeded on attempt ${attempt})"
      check "${label}" "${status}" "${expected}" "" "${body_file}"
      return
    fi

    # 503 = Lambda warming (cold start signal); 500 = transient error during warm-up
    if { [ "${status}" = "503" ] || [ "${status}" = "500" ]; } && [ "${attempt}" -lt "${RETRY_ATTEMPTS}" ]; then
      echo "  ⏳ ${label} → ${status} (cold start — retry ${attempt}/${RETRY_ATTEMPTS} in ${RETRY_DELAY}s)"
      sleep "${RETRY_DELAY}"
    else
      check "${label}" "${status}" "${expected}" "" "${body_file}"
      return
    fi
  done
}

# GET /api/v1/health — must return 200 with {"status":"ok"} once warm
retry_check "GET /api/v1/health" \
  "${API_BASE}/api/v1/health" "200" \
  'grep -q "\"status\":\"ok\"" '

# GET /api/v1/puzzles/generate — must return 200 with originalGrid
retry_check "GET /api/v1/puzzles/generate" \
  "${API_BASE}/api/v1/puzzles/generate" "200" \
  'grep -q "\"originalGrid\"" '

# GET /api/v1/puzzles/generate?difficulty=hard — CPU-heavy on cold JVM, may need retries
retry_check "GET /api/v1/puzzles/generate?difficulty=hard" \
  "${API_BASE}/api/v1/puzzles/generate?difficulty=hard" "200"

# POST /api/v1/games without token — JWT authorizer must reject with 401
STATUS=$(curl -s -o /tmp/games_noauth.json -w "%{http_code}" -X POST "${API_BASE}/api/v1/games" \
  -H "Content-Type: application/json" -d '{"difficulty":"easy"}' 2>/dev/null || true)
check "POST /api/v1/games (no token → 401)" "${STATUS}" "401" "" /tmp/games_noauth.json

# POST /api/v1/ai/scan without token — JWT authorizer must reject with 401
# AI routes are only active on rc-* workspaces (ai_coach_enabled = !is_default); skip on main.
if [ "${ENV_TYPE:-main}" = "rc" ]; then
  STATUS=$(curl -s -o /tmp/scan_noauth.json -w "%{http_code}" -X POST "${API_BASE}/api/v1/ai/scan" \
    -H "Content-Type: application/json" -d '{"image":"dGVzdA=="}' 2>/dev/null || true)
  check "POST /api/v1/ai/scan (no token → 401)" "${STATUS}" "401" "" /tmp/scan_noauth.json
fi

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
