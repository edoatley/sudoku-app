#!/usr/bin/env bash
# migrate-games-abandoned.sh — One-time data migration for SudokuGames tables.
#
# Changes applied to each item:
#   1. status IN_PROGRESS → ABANDONED
#   2. startedAt missing  → set to current UTC timestamp
#
# Targets both prod (SudokuGames) and rc (SudokuGames-rc-test-cicd) tables.
#
# Prerequisites:
#   - aws CLI installed and configured with profile 'sandbox'
#   - jq installed
#
# Usage:
#   bash scripts/local/migrate-games-abandoned.sh
#   bash scripts/local/migrate-games-abandoned.sh --dry-run   # preview only

set -euo pipefail

AWS_REGION="eu-west-2"
TABLES=("SudokuGames" "SudokuGames-rc-test-cicd")
NOW=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
DRY_RUN=false

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

log()  { echo -e "${BOLD}${CYAN}[migrate]${RESET} $*"; }
warn() { echo -e "${YELLOW}[migrate]${RESET} $*"; }
die()  { echo -e "${RED}[migrate] ERROR:${RESET} $*" >&2; exit 1; }

for arg in "$@"; do
  [[ "$arg" == "--dry-run" ]] && DRY_RUN=true
done

[[ "$DRY_RUN" == "true" ]] && warn "DRY-RUN mode — no changes will be written"

# ── Prerequisite check ─────────────────────────────────────────────────────────
for cmd in aws jq; do
  command -v "$cmd" &>/dev/null || die "'$cmd' not found — please install it first"
done
aws configure list --profile sandbox &>/dev/null || die "AWS profile 'sandbox' not found"

# ── Full scan with pagination ──────────────────────────────────────────────────
scan_all_items() {
  local table="$1"
  local all_items="[]"
  local last_key=""

  while true; do
    local args=(
      --profile sandbox
      --region "$AWS_REGION"
      --table-name "$table"
      --output json
    )
    [[ -n "$last_key" ]] && args+=(--exclusive-start-key "$last_key")

    local response
    response=$(aws dynamodb scan "${args[@]}")

    local page_items
    page_items=$(echo "$response" | jq '.Items')
    all_items=$(echo "$all_items $page_items" | jq -s 'add')

    last_key=$(echo "$response" | jq -r '.LastEvaluatedKey // empty')
    [[ -z "$last_key" ]] && break
  done

  echo "$all_items"
}

# ── Process one table ──────────────────────────────────────────────────────────
migrate_table() {
  local table="$1"
  local updated=0
  local skipped=0

  log "Scanning table: ${table}..."
  local items
  items=$(scan_all_items "$table")
  local total
  total=$(echo "$items" | jq 'length')
  log "Found ${total} item(s) in ${table}"

  for i in $(seq 0 $((total - 1))); do
    local item
    item=$(echo "$items" | jq ".[$i]")

    local user_id game_id status has_started_at
    user_id=$(echo "$item" | jq -r '.userId.S')
    game_id=$(echo "$item" | jq -r '.gameId.S')
    status=$(echo "$item" | jq -r '.status.S // ""')
    has_started_at=$(echo "$item" | jq -r 'has("startedAt")')

    local needs_status_update=false
    local needs_started_at=false
    [[ "$status" == "IN_PROGRESS" ]] && needs_status_update=true
    [[ "$has_started_at" == "false" ]] && needs_started_at=true

    if [[ "$needs_status_update" == "false" && "$needs_started_at" == "false" ]]; then
      skipped=$((skipped + 1))
      continue
    fi

    # Build UpdateExpression dynamically
    local set_parts=()
    local expr_names=()
    local expr_values=()

    if [[ "$needs_status_update" == "true" ]]; then
      set_parts+=("#s = :abandoned")
      expr_names+=('"#s": "status"')
      expr_values+=('"':abandoned'": {"S": "ABANDONED"}')
    fi

    if [[ "$needs_started_at" == "true" ]]; then
      set_parts+=("startedAt = :now")
      expr_values+=('"':now'": {"S": "'"$NOW"'"}')
    fi

    local update_expr="SET $(IFS=', '; echo "${set_parts[*]}")"
    local names_json="{$(IFS=','; echo "${expr_names[*]}")}"
    local values_json="{$(IFS=','; echo "${expr_values[*]}")}"

    if [[ "$DRY_RUN" == "true" ]]; then
      log "  [DRY-RUN] Would update ${user_id}/${game_id}: status=${needs_status_update} startedAt=${needs_started_at}"
    else
      local update_args=(
        --profile sandbox
        --region "$AWS_REGION"
        --table-name "$table"
        --key "{\"userId\":{\"S\":\"${user_id}\"},\"gameId\":{\"S\":\"${game_id}\"}}"
        --update-expression "$update_expr"
        --expression-attribute-values "$values_json"
      )
      [[ "${#expr_names[@]}" -gt 0 ]] && update_args+=(--expression-attribute-names "$names_json")

      aws dynamodb update-item "${update_args[@]}" --output json > /dev/null
      log "  Updated ${user_id}/${game_id}"
    fi

    updated=$((updated + 1))
  done

  echo ""
  echo -e "  ${BOLD}${table}${RESET}: updated ${GREEN}${updated}${RESET}, skipped ${skipped}"
}

# ── Main ───────────────────────────────────────────────────────────────────────
echo ""
log "Starting migration (timestamp: ${NOW})"
echo ""

for table in "${TABLES[@]}"; do
  migrate_table "$table"
  echo ""
done

echo -e "${BOLD}${GREEN}════════════════════════════════════════${RESET}"
echo -e "${BOLD}${GREEN}  Migration complete${RESET}"
echo -e "${BOLD}${GREEN}════════════════════════════════════════${RESET}"
echo ""
