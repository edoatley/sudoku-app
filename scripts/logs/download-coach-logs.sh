#!/usr/bin/env bash
# Download AI coach interaction logs from CloudWatch Logs for analysis.
# Outputs newline-delimited JSON (NDJSON) — one object per line.
#
# Usage:
#   bash scripts/logs/download-coach-logs.sh [options]
#
# Options:
#   --workspace <name>   Terraform workspace name (default: rc-ai-coach)
#                        Use "default" for production.
#   --hours <n>          How many hours back to search (default: 24)
#   --output <file>      Write NDJSON to file instead of stdout
#   --profile <name>     AWS CLI profile name (optional)
#
# Output fields (COACH_REQUEST):
#   type, cid, modelId, technique, historyLen, userMsgLen, ts,
#   userMessage, board (9x9 placed-digit rows), candidatesGrid ({"rows": [...]})
#
# Output fields (COACH_RESPONSE):
#   type, cid, revealHint, inputTokens, outputTokens,
#   cacheReadTokens, cacheWriteTokens, latencyMs, fallback, aiMessage,
#   errorType, errorMsg (fallback events only)
#
# Example — show token usage and latency for each call:
#   bash scripts/logs/download-coach-logs.sh | \
#     jq 'select(.type == "COACH_RESPONSE") | {cid, inputTokens, outputTokens, latencyMs, fallback}'
#
# Example — reconstruct a full conversation turn (prompt + reply), joined by cid:
#   bash scripts/logs/download-coach-logs.sh | \
#     jq -s 'group_by(.cid)[] | {cid: .[0].cid, userMessage: (.[0].userMessage), aiMessage: (map(select(.type=="COACH_RESPONSE"))[0].aiMessage)}'

set -euo pipefail

WORKSPACE="rc-ai-coach"
HOURS=24
OUTPUT=""
PROFILE_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --workspace) WORKSPACE="$2"; shift 2 ;;
    --hours)     HOURS="$2";     shift 2 ;;
    --output)    OUTPUT="$2";    shift 2 ;;
    --profile)   PROFILE_ARGS=(--profile "$2"); shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

if [[ "$WORKSPACE" == "default" ]]; then
  LOG_GROUP="/aws/lambda/sudoku"
else
  LOG_GROUP="/aws/lambda/sudoku-${WORKSPACE}"
fi

START_TIME=$(( ($(date +%s) - HOURS * 3600) * 1000 ))

echo "Fetching coach logs from ${LOG_GROUP} (last ${HOURS}h)..." >&2

RAW=$(aws logs filter-log-events \
  "${PROFILE_ARGS[@]}" \
  --log-group-name "${LOG_GROUP}" \
  --filter-pattern '"COACH_"' \
  --start-time "${START_TIME}" \
  --query 'events[].message' \
  --output json 2>/dev/null || echo "[]")

COUNT=$(echo "${RAW}" | jq 'length')
if [[ "${COUNT}" -eq 0 ]]; then
  echo "No coach logs found in ${LOG_GROUP} for the last ${HOURS}h." >&2
  exit 0
fi

echo "Found ${COUNT} log event(s). Extracting JSON lines..." >&2

NDJSON=$(echo "${RAW}" | jq -r '.[]' | while IFS= read -r line; do
  trimmed="${line#"${line%%[![:space:]]*}"}"
  if echo "${trimmed}" | jq -e . >/dev/null 2>&1; then
    echo "${trimmed}"
  fi
done)

if [[ -n "${OUTPUT}" ]]; then
  echo "${NDJSON}" > "${OUTPUT}"
  echo "Written to ${OUTPUT}" >&2
else
  echo "${NDJSON}"
fi
