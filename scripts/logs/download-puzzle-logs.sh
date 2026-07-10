#!/usr/bin/env bash
# Download puzzle-play observability logs from CloudWatch Logs for analysis.
# Covers the whole play session for a puzzle: COACH_*, HINT_REQUEST/HINT_RESPONSE,
# NUMBER / NUMBER_RESULT / NUMBER_CLEAR, and EVENTS_TRUNCATED markers — all correlated
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
#   --workspace <name>   Terraform workspace name, used as-is (no derivation).
#   --branch <name>|all  Resolve the workspace from a branch name using the same
#                        branch->workspace rule as auto-detection ("main" -> default) —
#                        use this when the puzzle you're after ran on a branch other
#                        than the one you currently have checked out. "all" scans every
#                        /aws/lambda/sudoku* log group instead of a single workspace —
#                        useful when you don't remember (or the branch/environment has
#                        since been torn down) which one a gameId came from. Slower:
#                        one CloudWatch query per environment. Overrides --workspace
#                        and git-branch auto-detection.
#   --output <file>      Write output to file instead of stdout
#   --profile <name>     AWS CLI profile name (optional)
#
# Event types and key fields:
#   NUMBER          pid, userId, r, c, v, ts
#   NUMBER_RESULT   pid, userId, r, c, v, correct        (server-derived move validity)
#   NUMBER_CLEAR    pid, userId, r, c, ts
#   HINT_REQUEST    pid, userId, cid, minRank, excludedRanks
#   HINT_RESPONSE   pid, userId, cid, techniqueName, strategyRank, difficulty, found
#   COACH_REQUEST / COACH_RESPONSE   pid, cid, ... (see download-coach-logs.sh)
#
# Example — everything for one puzzle, readable:
#   bash scripts/logs/download-puzzle-logs.sh --puzzle-id <gameId> --summary --hours 1
#
# Example — a puzzle on a branch other than the one you have checked out:
#   bash scripts/logs/download-puzzle-logs.sh --puzzle-id <gameId> --branch rc-foo
#
# Example — you don't know (or remember) which environment a puzzle ran on:
#   bash scripts/logs/download-puzzle-logs.sh --puzzle-id <gameId> --branch all
#
# Example — raw events for one user, piped to jq:
#   bash scripts/logs/download-puzzle-logs.sh --user-id <sub> | \
#     jq 'select(.type=="NUMBER_RESULT" and .correct==false)'

set -euo pipefail

# Mirrors resolve-environment.sh's branch->workspace rule (not sourced — that script is
# CI-output-oriented; this stays a pure local tool). Shared by auto-detection and --branch.
branch_to_workspace() {
  local branch="$1"
  if [[ "${branch}" == "main" ]]; then
    echo "default"
  else
    echo "${branch}" | tr '/' '-' | tr '.' '-' | cut -c1-32
  fi
}

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")"
if [[ -n "${CURRENT_BRANCH}" && "${CURRENT_BRANCH}" != "HEAD" ]]; then
  WORKSPACE="$(branch_to_workspace "${CURRENT_BRANCH}")"
else
  WORKSPACE=""
fi
HOURS=24
OUTPUT=""
PUZZLE_ID=""
USER_ID=""
SUMMARY=false
PROFILE_ARGS=()
BRANCH_ARG=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --puzzle-id) PUZZLE_ID="$2"; shift 2 ;;
    --user-id)   USER_ID="$2";   shift 2 ;;
    --hours)     HOURS="$2";     shift 2 ;;
    --summary)   SUMMARY=true;   shift 1 ;;
    --workspace) WORKSPACE="$2"; shift 2 ;;
    --branch)    BRANCH_ARG="$2"; shift 2 ;;
    --output)    OUTPUT="$2";    shift 2 ;;
    --profile)   PROFILE_ARGS=(--profile "$2"); shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

SCAN_ALL=false
if [[ "${BRANCH_ARG}" == "all" ]]; then
  SCAN_ALL=true
elif [[ -n "${BRANCH_ARG}" ]]; then
  WORKSPACE="$(branch_to_workspace "${BRANCH_ARG}")"
fi

if [[ "${SCAN_ALL}" != true && -z "${WORKSPACE}" ]]; then
  echo "ERROR: could not determine git branch — pass --workspace or --branch explicitly" >&2
  exit 1
fi

if [[ "${SCAN_ALL}" == true ]]; then
  if [[ -z "${PUZZLE_ID}" && -z "${USER_ID}" ]]; then
    echo "NOTE: --branch all with no --puzzle-id/--user-id scans every environment with" >&2
    echo "      the coarse type-token filter — this can be slow and noisy. Consider adding one." >&2
  fi
  echo "Discovering /aws/lambda/sudoku* log groups..." >&2
  mapfile -t LOG_GROUPS < <(aws logs describe-log-groups \
    "${PROFILE_ARGS[@]}" \
    --log-group-name-prefix "/aws/lambda/sudoku" \
    --query 'logGroups[].logGroupName' --output text 2>/dev/null | tr '\t' '\n' | grep -v 'image-recognition' || true)
  if [[ ${#LOG_GROUPS[@]} -eq 0 ]]; then
    echo "No /aws/lambda/sudoku* log groups found." >&2
    exit 0
  fi
  echo "Scanning ${#LOG_GROUPS[@]} log group(s): ${LOG_GROUPS[*]}" >&2
else
  if [[ "${WORKSPACE}" == "default" ]]; then
    LOG_GROUPS=("/aws/lambda/sudoku")
  else
    LOG_GROUPS=("/aws/lambda/sudoku-${WORKSPACE}")
  fi
fi

# Server-side narrowing: filter on the most specific id supplied; otherwise match the event-type
# tokens (?TERM = OR). NUMBER covers NUMBER/NUMBER_RESULT/NUMBER_CLEAR; HINT_ covers both hints.
if [[ -n "${PUZZLE_ID}" ]]; then
  FILTER_PATTERN="\"${PUZZLE_ID}\""
elif [[ -n "${USER_ID}" ]]; then
  FILTER_PATTERN="\"${USER_ID}\""
else
  FILTER_PATTERN='?COACH_ ?HINT_ ?NUMBER ?EVENTS_TRUNCATED'
fi

START_TIME=$(( ($(date +%s) - HOURS * 3600) * 1000 ))

FILTERED=""
for LOG_GROUP in "${LOG_GROUPS[@]}"; do
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
    echo "  No log events found in ${LOG_GROUP}." >&2
    continue
  fi
  echo "  Found ${COUNT} log event(s) in ${LOG_GROUP}. Extracting JSON lines..." >&2

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

  # Keep only our event types, and honour the pid/user filters exactly (the CloudWatch pattern is
  # a coarse substring match that can catch neighbouring lines).
  GROUP_FILTERED=$(echo "${NDJSON}" | jq -c --arg pid "${PUZZLE_ID}" --arg uid "${USER_ID}" '
    select(.type as $t | ["COACH_REQUEST","COACH_RESPONSE","HINT_REQUEST","HINT_RESPONSE","NUMBER","NUMBER_RESULT","NUMBER_CLEAR","EVENTS_TRUNCATED"] | index($t))
    | select($pid == "" or .pid == $pid)
    | select($uid == "" or .userId == $uid)
  ' || true)

  if [[ -n "${GROUP_FILTERED}" ]]; then
    if [[ -n "${FILTERED}" ]]; then
      FILTERED="${FILTERED}"$'\n'"${GROUP_FILTERED}"
    else
      FILTERED="${GROUP_FILTERED}"
    fi
  fi
done

if [[ -z "${FILTERED}" ]]; then
  echo "No matching puzzle-play events after filtering." >&2
  exit 0
fi

render() {
  if [[ "${SUMMARY}" == true ]]; then
    # We drop the -s (slurp) flag so jq processes the NDJSON stream line-by-line.
    echo "${FILTERED}" | jq -r '
      if .type == "HINT_REQUEST" then
        "HINT_REQUEST - excludedRanks \(.excludedRanks | tojson)"
      elif .type == "HINT_RESPONSE" then
        "HINT_RESPONSE - found \(.found)\(if .techniqueName then ", technique \(.techniqueName)" else "" end)"
      elif .type == "NUMBER" then
        "NUMBER - row: \(.r), column: \(.c), value: \(.v)"
      elif .type == "NUMBER_RESULT" then
        "NUMBER_RESULT - row: \(.r), column: \(.c), value: \(.v), correct: \(.correct)"
      elif .type == "COACH_REQUEST" then
        "COACH_REQUEST - userMessage: \(.userMessage), technique \(.technique)"
      elif .type == "COACH_RESPONSE" then
        "COACH_RESPONSE - aiMessage: \(.aiMessage), fallback \(.fallback)"
      elif .type == "NUMBER_CLEAR" then
        "NUMBER_CLEAR - row: \(.r), column: \(.c)"
      elif .type == "EVENTS_TRUNCATED" then
        "EVENTS_TRUNCATED"
      else
        .type
      end
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
