#!/usr/bin/env bash
# seed-from-prod.sh — Download production DynamoDB data and load it into the local
# LocalStack instance so the dev data browser has real records to display.
#
# Prerequisites:
#   - aws CLI installed and configured with profile 'sandbox' pointing at the prod account
#   - jq installed
#   - LocalStack running on port 4566 (start via: bash scripts/local/local-dev.sh)
#
# Usage:
#   bash scripts/local/seed-from-prod.sh
#
# The script scans both prod tables (SudokuGames, SudokuPlayers), writes the items
# into LocalStack in batches of 25, and prints a before/after count for each table.

set -euo pipefail

LOCALSTACK_ENDPOINT="http://localhost:4566"
AWS_REGION="eu-west-2"
PROD_GAMES_TABLE="SudokuGames"
PROD_PLAYERS_TABLE="SudokuPlayers"
LOCAL_GAMES_TABLE="SudokuGames"
LOCAL_PLAYERS_TABLE="SudokuPlayers"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

log()  { echo -e "${BOLD}${CYAN}[seed-from-prod]${RESET} $*"; }
warn() { echo -e "${YELLOW}[seed-from-prod]${RESET} $*"; }
die()  { echo -e "${RED}[seed-from-prod] ERROR:${RESET} $*" >&2; exit 1; }

# ── Prerequisite check ────────────────────────────────────────────────────────
for cmd in aws jq; do
  command -v "$cmd" &>/dev/null || die "'$cmd' not found — please install it first"
done

aws configure list --profile sandbox &>/dev/null || die "AWS profile 'sandbox' not found — run 'aws configure --profile sandbox' first"

if ! curl -sf "${LOCALSTACK_ENDPOINT}/_localstack/health" &>/dev/null; then
  die "LocalStack is not running at ${LOCALSTACK_ENDPOINT} — start it first with: bash scripts/local/local-dev.sh"
fi

# ── Batch-write helper ────────────────────────────────────────────────────────
# Writes an array of DynamoDB items (in DynamoDB JSON format) to a local table
# in batches of 25 (the DynamoDB API limit).
batch_write_items() {
  local table="$1"
  local items_json="$2"
  local total
  total=$(echo "$items_json" | jq 'length')

  if [[ "$total" -eq 0 ]]; then
    warn "No items to write to ${table}"
    return
  fi

  local written=0
  local offset=0
  local batch_size=25

  while [[ $offset -lt $total ]]; do
    local batch
    batch=$(echo "$items_json" | jq --argjson start "$offset" --argjson size "$batch_size" '.[$start:$start+$size]')

    local request_items
    request_items=$(jq -n \
      --arg table "$table" \
      --argjson items "$batch" \
      '{ ($table): [ $items[] | { "PutRequest": { "Item": . } } ] }')

    aws dynamodb batch-write-item \
      --endpoint-url "$LOCALSTACK_ENDPOINT" \
      --region "$AWS_REGION" \
      --request-items "$request_items" \
      --output json > /dev/null

    written=$((written + $(echo "$batch" | jq 'length')))
    offset=$((offset + batch_size))
    log "  Written ${written}/${total} items to ${table}..."
  done
}

# ── Full scan with pagination ─────────────────────────────────────────────────
scan_all_items() {
  local table="$1"
  local all_items="[]"
  local last_evaluated_key=""

  while true; do
    local args=(
      --profile sandbox
      --region "$AWS_REGION"
      --table-name "$table"
      --output json
    )
    if [[ -n "$last_evaluated_key" ]]; then
      args+=(--exclusive-start-key "$last_evaluated_key")
    fi

    local response
    response=$(aws dynamodb scan "${args[@]}")

    local page_items
    page_items=$(echo "$response" | jq '.Items')
    all_items=$(echo "$all_items $page_items" | jq -s 'add')

    last_evaluated_key=$(echo "$response" | jq -r '.LastEvaluatedKey // empty')
    if [[ -z "$last_evaluated_key" ]]; then
      break
    fi
  done

  echo "$all_items"
}

# ── Main ──────────────────────────────────────────────────────────────────────
log "Starting prod → LocalStack data seed"
echo ""

# Games
log "Scanning prod table: ${PROD_GAMES_TABLE}..."
games_items=$(scan_all_items "$PROD_GAMES_TABLE")
games_count=$(echo "$games_items" | jq 'length')
log "Found ${games_count} game(s) in prod"

local_games_before=$(aws dynamodb scan \
  --endpoint-url "$LOCALSTACK_ENDPOINT" \
  --region "$AWS_REGION" \
  --table-name "$LOCAL_GAMES_TABLE" \
  --select COUNT \
  --output json 2>/dev/null | jq '.Count // 0' || echo 0)
log "LocalStack ${LOCAL_GAMES_TABLE} currently has ${local_games_before} item(s)"

if [[ "$games_count" -gt 0 ]]; then
  log "Writing ${games_count} game(s) to LocalStack..."
  batch_write_items "$LOCAL_GAMES_TABLE" "$games_items"
fi

local_games_after=$(aws dynamodb scan \
  --endpoint-url "$LOCALSTACK_ENDPOINT" \
  --region "$AWS_REGION" \
  --table-name "$LOCAL_GAMES_TABLE" \
  --select COUNT \
  --output json | jq '.Count')
log "LocalStack ${LOCAL_GAMES_TABLE} now has ${local_games_after} item(s)"

echo ""

# Players
log "Scanning prod table: ${PROD_PLAYERS_TABLE}..."
players_items=$(scan_all_items "$PROD_PLAYERS_TABLE")
players_count=$(echo "$players_items" | jq 'length')
log "Found ${players_count} player(s) in prod"

local_players_before=$(aws dynamodb scan \
  --endpoint-url "$LOCALSTACK_ENDPOINT" \
  --region "$AWS_REGION" \
  --table-name "$LOCAL_PLAYERS_TABLE" \
  --select COUNT \
  --output json 2>/dev/null | jq '.Count // 0' || echo 0)
log "LocalStack ${LOCAL_PLAYERS_TABLE} currently has ${local_players_before} item(s)"

if [[ "$players_count" -gt 0 ]]; then
  log "Writing ${players_count} player(s) to LocalStack..."
  batch_write_items "$LOCAL_PLAYERS_TABLE" "$players_items"
fi

local_players_after=$(aws dynamodb scan \
  --endpoint-url "$LOCALSTACK_ENDPOINT" \
  --region "$AWS_REGION" \
  --table-name "$LOCAL_PLAYERS_TABLE" \
  --select COUNT \
  --output json | jq '.Count')
log "LocalStack ${LOCAL_PLAYERS_TABLE} now has ${local_players_after} item(s)"

echo ""
echo -e "${BOLD}${GREEN}════════════════════════════════════════${RESET}"
echo -e "${BOLD}${GREEN}  Seed complete${RESET}"
echo -e "${BOLD}${GREEN}════════════════════════════════════════${RESET}"
echo -e "  Games:   ${CYAN}${local_games_after} item(s)${RESET}"
echo -e "  Players: ${CYAN}${local_players_after} item(s)${RESET}"
echo ""
echo -e "  Open ${CYAN}http://localhost:5174${RESET} → Game menu → Developer → Data Browser"
echo ""
