#!/usr/bin/env bash
# Download puzzle-play observability logs from CloudWatch Logs for analysis.
# Covers the whole play session for a puzzle: COACH_*, HINT_REQUEST/HINT_RESPONSE,
# NUMBER / NUMBER_RESULT / NUMBER_CLEAR, UNDO, and EVENTS_TRUNCATED markers — all correlated
# by pid (the gameId). By default prints newline-delimited JSON (one event per line);
# with --summary prints a human-readable digest per puzzle.
#
# Usage:
#   bash scripts/logs/download-puzzle-logs.sh [options]
#
# Options:
#   --puzzle-id <id>     Only events for this pid (gameId). Narrows the CloudWatch query.
#   --user-id <id>       Only events for this userId. Narrows the CloudWatch query.
#   --hours <n>          How many hours back to search (default: 24)
#   --summary            Print a readable per-puzzle digest instead of raw NDJSON
#   --workspace <name>   Terraform workspace (default: derived from git branch,
#                        "main" -> "default"). Pass explicitly to override.
#   --output <file>      Write output to file instead of stdout
#   --profile <name>     AWS CLI profile name (optional)
#
# Event types and key fields:
#   NUMBER          pid, userId, r, c, v, ts
#   NUMBER_RESULT   pid, userId, r, c, v, correct        (server-derived move validity)
#   NUMBER_CLEAR    pid, userId, r, c, ts
#   UNDO            pid, userId, r, c, v (removed), prevV (restored), undoneType, ts
#   HINT_REQUEST    pid, userId, cid, minRank, excludedRanks
#   HINT_RESPONSE   pid, userId, cid, techniqueName, strategyRank, difficulty, found
#   COACH_REQUEST / COACH_RESPONSE   pid, cid, ... (see download-coach-logs.sh)
#
# Example — everything for one puzzle, readable:
#   bash scripts/logs/download-puzzle-logs.sh --puzzle-id <gameId> --summary --hours 1
#
# Example — raw events for one user, piped to jq:
#   bash scripts/logs/download-puzzle-logs.sh --user-id <sub> | \
#     jq 'select(.type=="NUMBER_RESULT" and .correct==false)'

set -euo pipefail

# Default workspace mirrors resolve-environment.sh's branch->workspace rule (not sourced —
# that script is CI-output-oriented; this stays a pure local tool).
BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")"
if [[ "${BRANCH}" == "main" ]]; then
  WORKSPACE="default"
elif [[ -n "${BRANCH}" && "${BRANCH}" != "HEAD" ]]; then
  WORKSPACE="$(echo "${BRANCH}" | tr '/' '-' | tr '.' '-' | cut -c1-32)"
else
  WORKSPACE=""
fi
HOURS=24
OUTPUT=""
PUZZLE_ID=""
USER_ID=""
SUMMARY=false
PROFILE_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --puzzle-id) PUZZLE_ID="$2"; shift 2 ;;
    --user-id)   USER_ID="$2";   shift 2 ;;
    --hours)     HOURS="$2";     shift 2 ;;
    --summary)   SUMMARY=true;   shift 1 ;;
    --workspace) WORKSPACE="$2"; shift 2 ;;
    --output)    OUTPUT="$2";    shift 2 ;;
    --profile)   PROFILE_ARGS=(--profile "$2"); shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

if [[ -z "${WORKSPACE}" ]]; then
  echo "ERROR: could not determine git branch — pass --workspace explicitly" >&2
  exit 1
fi

if [[ "$WORKSPACE" == "default" ]]; then
  LOG_GROUP="/aws/lambda/sudoku"
else
  LOG_GROUP="/aws/lambda/sudoku-${WORKSPACE}"
fi

# Server-side narrowing: filter on the most specific id supplied; otherwise match the event-type
# tokens (?TERM = OR). NUMBER covers NUMBER/NUMBER_RESULT/NUMBER_CLEAR; HINT_ covers both hints.
if [[ -n "${PUZZLE_ID}" ]]; then
  FILTER_PATTERN="\"${PUZZLE_ID}\""
elif [[ -n "${USER_ID}" ]]; then
  FILTER_PATTERN="\"${USER_ID}\""
else
  FILTER_PATTERN='?COACH_ ?HINT_ ?NUMBER ?UNDO ?EVENTS_TRUNCATED'
fi

START_TIME=$(( ($(date +%s) - HOURS * 3600) * 1000 ))

echo "Fetching puzzle logs from ${LOG_GROUP} (last ${HOURS}h)..." >&2

RAW=$(aws logs filter-log-events \
  "${PROFILE_ARGS[@]}" \
  --log-group-name "${LOG_GROUP}" \
  --filter-pattern "${FILTER_PATTERN}" \
  --start-time "${START_TIME}" \
  --query 'events[].message' \
  --output json 2>/dev/null || echo "[]")

COUNT=$(echo "${RAW}" | jq 'length')
if [[ "${COUNT}" -eq 0 ]]; then
  echo "No puzzle logs found in ${LOG_GROUP} for the last ${HOURS}h." >&2
  exit 0
fi

echo "Found ${COUNT} log event(s). Extracting JSON lines..." >&2

# CloudWatch message is a full log line (timestamp LEVEL [logger] (thread) {json}), not bare
# JSON — take everything from the first '{' onward rather than assuming the line already starts
# with one.
NDJSON=$(echo "${RAW}" | jq -r '.[]' | while IFS= read -r line; do
  json_part="${line#*\{}"
  if [[ "${json_part}" != "${line}" ]]; then
    json_part="{${json_part}"
    if echo "${json_part}" | jq -e . >/dev/null 2>&1; then
      echo "${json_part}"
    fi
  fi
done)

# Keep only our event types, and honour the pid/user filters exactly (the CloudWatch pattern is a
# coarse substring match that can catch neighbouring lines).
FILTERED=$(echo "${NDJSON}" | jq -c --arg pid "${PUZZLE_ID}" --arg uid "${USER_ID}" '
  select(.type as $t | ["COACH_REQUEST","COACH_RESPONSE","HINT_REQUEST","HINT_RESPONSE","NUMBER","NUMBER_RESULT","NUMBER_CLEAR","UNDO","EVENTS_TRUNCATED"] | index($t))
  | select($pid == "" or .pid == $pid)
  | select($uid == "" or .userId == $uid)
' || true)

if [[ -z "${FILTERED}" ]]; then
  echo "No matching puzzle-play events after filtering." >&2
  exit 0
fi

render() {
  if [[ "${SUMMARY}" == true ]]; then
    echo "${FILTERED}" | jq -s -r '
      def hhmm(ms): (ms/1000) | gmtime | strftime("%Y-%m-%dT%H:%M:%SZ");
      group_by(.pid)[]
      | (.[0].pid) as $pid
      | ((map(.userId) | map(select(. != null)))[0] // "?") as $user
      | (map(.ts) | map(select(. != null))) as $ts
      | (map(select(.type=="NUMBER")) | length) as $placed
      | (map(select(.type=="NUMBER_RESULT" and .correct==true)) | length) as $correct
      | (map(select(.type=="NUMBER_RESULT" and .correct==false)) | length) as $incorrect
      | (map(select(.type=="NUMBER_CLEAR")) | length) as $clears
      | (map(select(.type=="UNDO")) | length) as $undos
      | (map(select(.type=="HINT_REQUEST")) | length) as $hints
      | (map(select(.type=="COACH_REQUEST")) | length) as $coach
      | (map(select(.type=="EVENTS_TRUNCATED")) | length) as $trunc
      | (map(select(.type=="HINT_RESPONSE") | (.techniqueName // "none"))
          | group_by(.) | map("\(.[0]) x\(length)") | join(", ")) as $techniques
      | "Puzzle \($pid)   user=\($user)",
        "  span:    \(if ($ts|length)>0 then hhmm($ts|min) + "  ->  " + hhmm($ts|max) else "n/a" end)",
        "  numbers: \($placed) placed  (\($correct) correct, \($incorrect) incorrect)",
        "  clears:  \($clears)",
        "  undos:   \($undos)",
        "  hints:   \($hints)\(if $techniques != "" then "  [" + $techniques + "]" else "" end)",
        "  coach:   \($coach) turn(s)\(if $trunc>0 then "   (\($trunc) truncated batch marker[s])" else "" end)",
        ""
    '
  else
    echo "${FILTERED}"
  fi
}

if [[ -n "${OUTPUT}" ]]; then
  render > "${OUTPUT}"
  echo "Written to ${OUTPUT}" >&2
else
  render
fi
