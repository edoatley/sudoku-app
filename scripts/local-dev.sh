#!/usr/bin/env bash
# local-dev.sh — Start the full local dev stack without Docker for backend/UI.
#
# Starts:
#   1. Quarkus backend (mvn quarkus:dev) — port 8080
#      The %dev profile points at LocalStack on port 4566; DevDatabaseInitializer
#      creates the DynamoDB tables automatically on first startup.
#   2. Vite UI dev server (npm run dev)  — port 5174
#
# Ctrl-C (or SIGTERM) cleanly shuts down both processes.
# LocalStack is managed by the %dev Quarkus profile (started externally or via
# the dev services — this script does not manage it).
#
# Usage:
#   bash scripts/local-dev.sh
#
# Prerequisites: docker, java, node/npm

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

log()  { echo -e "${BOLD}${CYAN}[local-dev]${RESET} $*"; }
warn() { echo -e "${YELLOW}[local-dev]${RESET} $*"; }
die()  { echo -e "${RED}[local-dev] ERROR:${RESET} $*" >&2; exit 1; }

# ── Prerequisite check ────────────────────────────────────────────────────────
for cmd in docker java node npm; do
  command -v "$cmd" &>/dev/null || die "'$cmd' not found — please install it first"
done

# ── PIDs tracked for cleanup ──────────────────────────────────────────────────
LOCALSTACK_CONTAINER="sudoku-localstack-dev-$$"
BACKEND_PID=""
UI_PID=""

cleanup() {
  echo ""
  warn "Shutting down..."

  if [[ -n "${UI_PID}" ]] && kill -0 "${UI_PID}" 2>/dev/null; then
    log "Stopping UI dev server (pid ${UI_PID})..."
    kill "${UI_PID}" 2>/dev/null || true
    wait "${UI_PID}" 2>/dev/null || true
  fi

  if [[ -n "${BACKEND_PID}" ]] && kill -0 "${BACKEND_PID}" 2>/dev/null; then
    log "Stopping Quarkus backend (pid ${BACKEND_PID})..."
    kill "${BACKEND_PID}" 2>/dev/null || true
    wait "${BACKEND_PID}" 2>/dev/null || true
  fi

  if docker ps -q --filter "name=${LOCALSTACK_CONTAINER}" 2>/dev/null | grep -q .; then
    log "Stopping LocalStack container..."
    docker rm -f "${LOCALSTACK_CONTAINER}" &>/dev/null || true
  fi

  log "Done."
}
trap cleanup EXIT INT TERM

# ── 1. LocalStack ─────────────────────────────────────────────────────────────
log "Starting LocalStack (DynamoDB on port 4566)..."
docker run -d --name "${LOCALSTACK_CONTAINER}" \
  -p 4566:4566 \
  localstack/localstack:latest \
  > /dev/null

log "Waiting for LocalStack to be ready..."
for i in $(seq 1 20); do
  if curl -sf http://localhost:4566/_localstack/health &>/dev/null; then
    log "LocalStack is ready"
    break
  fi
  [[ $i -eq 20 ]] && die "LocalStack did not become ready in time"
  sleep 2
done

# ── 2. Quarkus backend ────────────────────────────────────────────────────────
# DevDatabaseInitializer creates the DynamoDB tables on first startup.
log "Starting Quarkus backend (hot-reload enabled)..."
(
  cd "${REPO_ROOT}/backend"
  export CORS_ALLOWED_ORIGINS="http://localhost:5174"
  ./mvnw quarkus:dev -Ddebug=false
) &
BACKEND_PID=$!

log "Waiting for backend to be ready..."
for i in $(seq 1 40); do
  if curl -sf http://localhost:8080/api/v1/puzzles/generate &>/dev/null; then
    log "Backend is ready at http://localhost:8080"
    break
  fi
  if ! kill -0 "${BACKEND_PID}" 2>/dev/null; then
    die "Backend process exited unexpectedly"
  fi
  [[ $i -eq 40 ]] && die "Backend did not become ready in time"
  sleep 3
done

# ── 3. Vite UI dev server ─────────────────────────────────────────────────────
log "Starting Vite UI dev server (hot-reload enabled)..."
(
  cd "${REPO_ROOT}/ui"
  export VITE_API_URL=http://localhost:8080/api/v1
  export VITE_MOCK_API=false
  export VITE_SKIP_AUTH=true
  export VITE_DEV_TOOLS=true
  npm run dev -- --port 5174
) &
UI_PID=$!

# ── Ready ─────────────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}${GREEN}════════════════════════════════════════${RESET}"
echo -e "${BOLD}${GREEN}  Local dev stack is up${RESET}"
echo -e "${BOLD}${GREEN}════════════════════════════════════════${RESET}"
echo -e "  UI:       ${CYAN}http://localhost:5174${RESET}"
echo -e "  Backend:  ${CYAN}http://localhost:8080/api/v1${RESET}"
echo -e "  DynamoDB: ${CYAN}http://localhost:4566${RESET} (LocalStack)"
echo ""
echo -e "  To run hint-demo tests against this stack:"
echo -e "  ${BOLD}cd ui && npm run test:hint-demos${RESET}"
echo ""
echo -e "  ${YELLOW}Press Ctrl-C to stop everything${RESET}"
echo ""

# ── Wait until either child exits (crash) or Ctrl-C ──────────────────────────
wait "${BACKEND_PID}" "${UI_PID}"
