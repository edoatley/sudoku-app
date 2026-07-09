#!/usr/bin/env bash
# AI Coach smoke test — verifies POST /ai/coach returns a real coaching reply AND
# that the interaction is durably logged to CloudWatch with full content.
#
# @spec SC-API-001, SC-API-002, SC-API-003 (request validation still enforced end-to-end)
# @spec SC-BE-005, SC-BE-006, SC-BE-007, SC-BE-008 (full-content COACH_REQUEST/COACH_RESPONSE logging)
# @spec SC-RL-001, SC-RL-002, SC-RL-003 (guardrails don't block a legitimate, enabled smoke-test user)
#
# Usage (CI):   bash scripts/github/coach-smoke-test.sh <api_base_url> <workspace>
# Usage (local): AWS_PROFILE=sandbox bash scripts/github/coach-smoke-test.sh \
#                  https://... rc-ai-coach-improvements-1 <id_token>
#
# Arguments:
#   $1  API base URL  (trailing slash stripped automatically)
#   $2  Terraform workspace name — used to build the CloudWatch log group name
#   $3  (optional) Bearer token — falls back to $SMOKE_ID_TOKEN env var
#
# Env (optional):
#   COACH_LOG_POLL_ATTEMPTS  How many times to poll CloudWatch before giving up (default: 6)
#   COACH_LOG_POLL_DELAY     Seconds to sleep between polls (default: 5)
#
# Exits 0 on success, 1 on failure.
set -euo pipefail

API_BASE="${1:?Usage: $0 <api_base_url> <workspace> [id_token]}"
API_BASE="${API_BASE%/}"
WORKSPACE="${2:?Usage: $0 <api_base_url> <workspace> [id_token]}"
ID_TOKEN="${3:-${SMOKE_ID_TOKEN:-}}"

POLL_ATTEMPTS="${COACH_LOG_POLL_ATTEMPTS:-6}"
POLL_DELAY="${COACH_LOG_POLL_DELAY:-5}"

if [[ -z "${ID_TOKEN}" ]]; then
  echo "ERROR: ID token required — pass as \$3 or set SMOKE_ID_TOKEN" >&2
  exit 1
fi

if [[ "${WORKSPACE}" == "default" ]]; then
  LOG_GROUP="/aws/lambda/sudoku"
else
  LOG_GROUP="/aws/lambda/sudoku-${WORKSPACE}"
fi

# Distinctive marker for unambiguous CloudWatch correlation — real players won't type this.
MARKER="SMOKE_TEST_COACH_CHECK_${GITHUB_RUN_ID:-local}_$(date +%s)"

# Known naked-single fixture — same board used in CoachResourceTest.java / BedrockCoachClientTest.java.
# Grid's wire format is {"rows": [[...]]} (custom serializer/deserializer, not a bare array —
# see domain/Grid.java's Javadoc and GridDeserializer.java).
jq -n --arg msg "${MARKER}" '{
  board: { rows: [[5,3,0,0,7,0,0,0,0],[6,0,0,1,9,5,0,0,0],[0,9,8,0,0,0,0,6,0],
                  [8,0,0,0,6,0,0,0,3],[4,0,0,8,0,3,0,0,1],[7,0,0,0,2,0,0,0,6],
                  [0,6,0,0,0,0,2,8,0],[0,0,0,4,1,9,0,0,5],[0,0,0,0,8,0,0,7,9]] },
  history: [],
  userMessage: $msg
}' > /tmp/coach_smoke_body.json

printf 'Authorization: Bearer %s\n' "${ID_TOKEN}" > /tmp/coach-auth-header.txt

STATUS=$(curl -s -o /tmp/coach_smoke_response.json -w "%{http_code}" \
  -X POST "${API_BASE}/api/v1/ai/coach" \
  -H "Content-Type: application/json" \
  -H @/tmp/coach-auth-header.txt \
  --data-binary @/tmp/coach_smoke_body.json)

echo "POST /api/v1/ai/coach → HTTP ${STATUS}"
cat /tmp/coach_smoke_response.json

if [[ "${STATUS}" != "200" ]]; then
  echo "❌ Expected 200, got ${STATUS}"
  exit 1
fi

HTTP_AI_MESSAGE=$(python3 -c "
import json
m = json.load(open('/tmp/coach_smoke_response.json')).get('aiMessage', '')
assert m.strip(), 'blank aiMessage'
print(m)
")
echo "✅ Coach API responded with a non-blank aiMessage"

# Poll CloudWatch for the COACH_REQUEST/COACH_RESPONSE pair — ingestion isn't instant.
# COACH_RESPONSE never contains userMessage, so we can't filter server-side by the marker
# alone; fetch the broader "COACH_" window (same proven filter as download-coach-logs.sh)
# and correlate the pair locally via cid once the marker-bearing request line is found.
START_TIME=$(( ($(date +%s) - 120) * 1000 ))

FOUND_REQUEST=""
FOUND_RESPONSE=""
CID=""

for attempt in $(seq 1 "${POLL_ATTEMPTS}"); do
  RAW=$(aws logs filter-log-events \
    --log-group-name "${LOG_GROUP}" \
    --filter-pattern '"COACH_"' \
    --start-time "${START_TIME}" \
    --query 'events[].message' \
    --output json 2>/dev/null || echo "[]")

  LINES=$(echo "${RAW}" | jq -r '.[]' | while IFS= read -r line; do
    trimmed="${line#"${line%%[![:space:]]*}"}"
    if echo "${trimmed}" | jq -e . >/dev/null 2>&1; then
      echo "${trimmed}"
    fi
  done)

  if [[ -z "${FOUND_REQUEST}" ]]; then
    FOUND_REQUEST=$(echo "${LINES}" | jq -c --arg m "${MARKER}" \
      'select(.type=="COACH_REQUEST" and .userMessage==$m)' | head -1)
    if [[ -n "${FOUND_REQUEST}" ]]; then
      CID=$(echo "${FOUND_REQUEST}" | jq -r '.cid')
    fi
  fi

  if [[ -n "${CID}" && -z "${FOUND_RESPONSE}" ]]; then
    FOUND_RESPONSE=$(echo "${LINES}" | jq -c --arg c "${CID}" \
      'select(.type=="COACH_RESPONSE" and .cid==$c)' | head -1)
  fi

  if [[ -n "${FOUND_REQUEST}" && -n "${FOUND_RESPONSE}" ]]; then
    echo "✅ Found COACH_REQUEST/COACH_RESPONSE pair (attempt ${attempt}/${POLL_ATTEMPTS}, cid ${CID})"
    break
  fi

  echo "  ⏳ log not yet ingested — retry ${attempt}/${POLL_ATTEMPTS} in ${POLL_DELAY}s"
  sleep "${POLL_DELAY}"
done

if [[ -z "${FOUND_REQUEST}" || -z "${FOUND_RESPONSE}" ]]; then
  echo "❌ COACH_REQUEST/COACH_RESPONSE pair not found in CloudWatch after ${POLL_ATTEMPTS} attempts"
  echo "   log group: ${LOG_GROUP}, marker: ${MARKER}"
  exit 1
fi

# Content assertions — exercise SC-BE-005..008 against real logged content, not mocks.
LOG_OK=1

check_log() {
  local label="$1" ok="$2"
  if [[ "${ok}" == "true" ]]; then
    echo "  ✅ ${label}"
  else
    echo "  ❌ ${label}"
    LOG_OK=0
  fi
}

LOGGED_USER_MESSAGE=$(echo "${FOUND_REQUEST}" | jq -r '.userMessage')
if [[ "${LOGGED_USER_MESSAGE}" == "${MARKER}" ]]; then
  check_log "COACH_REQUEST.userMessage matches exactly (SC-BE-005)" true
else
  check_log "COACH_REQUEST.userMessage matches exactly (SC-BE-005)" false
fi

BOARD_ROWS=$(echo "${FOUND_REQUEST}" | jq '.board | length')
if [[ "${BOARD_ROWS}" == "9" ]]; then
  check_log "COACH_REQUEST.board has 9 rows (SC-BE-006)" true
else
  check_log "COACH_REQUEST.board has 9 rows (SC-BE-006)" false
fi

CANDIDATES_ROWS=$(echo "${FOUND_REQUEST}" | jq '.candidatesGrid.rows | length')
if [[ "${CANDIDATES_ROWS}" == "9" ]]; then
  check_log "COACH_REQUEST.candidatesGrid has 9 rows (SC-BE-007)" true
else
  check_log "COACH_REQUEST.candidatesGrid has 9 rows (SC-BE-007)" false
fi

LOGGED_AI_MESSAGE=$(echo "${FOUND_RESPONSE}" | jq -r '.aiMessage')
if [[ -n "${LOGGED_AI_MESSAGE}" && "${LOGGED_AI_MESSAGE}" == "${HTTP_AI_MESSAGE}" ]]; then
  check_log "COACH_RESPONSE.aiMessage matches the HTTP response (SC-BE-008)" true
else
  check_log "COACH_RESPONSE.aiMessage matches the HTTP response (SC-BE-008)" false
fi

if [[ "${LOG_OK}" != "1" ]]; then
  echo "❌ One or more logged fields did not match expectations"
  exit 1
fi

echo "✅ AI coach smoke test passed (cid ${CID})"

# Append to GitHub step summary when running in CI
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "### AI Coach Smoke Test"
    echo ""
    echo "| Check | Result |"
    echo "|-------|--------|"
    echo "| POST /api/v1/ai/coach | ✅ ${STATUS} |"
    echo "| CloudWatch COACH_REQUEST/COACH_RESPONSE pair | ✅ found (cid ${CID}) |"
    echo "| Logged content matches (userMessage, board, candidatesGrid, aiMessage) | ✅ |"
  } >> "${GITHUB_STEP_SUMMARY}"
fi
