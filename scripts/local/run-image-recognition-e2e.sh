#!/usr/bin/env bash
# run-image-recognition-e2e.sh — Run image recognition e2e tests against real Bedrock (sandbox profile).
#
# Usage:
#   bash scripts/local/run-image-recognition-e2e.sh
#
# Requires AWS SSO login:
#   aws sso login --profile sandbox
#
# Tests call handler.handler() directly — no Lambda deploy needed.
# Results include a pretty-printed grid for each fixture image.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IR_DIR="${REPO_ROOT}/image_recognition"
VENV="${IR_DIR}/.venv"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

# ── AWS credentials check ─────────────────────────────────────────────────────
echo -e "\n${BOLD}${CYAN}=== Checking AWS credentials (sandbox profile) ===${RESET}"
if ! AWS_PROFILE=sandbox aws sts get-caller-identity --output text &>/dev/null; then
  echo -e "${RED}✗ AWS sandbox profile not authenticated.${RESET}"
  echo -e "  Run: ${BOLD}aws sso login --profile sandbox${RESET}"
  exit 1
fi
echo -e "${GREEN}✓ Authenticated${RESET}"

# ── Python venv ───────────────────────────────────────────────────────────────
if [[ ! -f "${VENV}/bin/python" ]]; then
  echo -e "\n${YELLOW}Setting up .venv...${RESET}"
  python3 -m venv "${VENV}"
  "${VENV}/bin/pip" install -q -r "${IR_DIR}/requirements-dev.txt"
fi

# ── Run e2e tests ─────────────────────────────────────────────────────────────
echo -e "\n${BOLD}${CYAN}=== Running image recognition e2e tests ===${RESET}"
echo -e "  Models: Nemotron Nano 12B v2 (primary) → Haiku 4.5 (cross-check/fallback)"
echo -e "  PIL preprocessing: disabled (deferred)\n"

(
  cd "${IR_DIR}"
  source "${VENV}/bin/activate"
  AWS_PROFILE=sandbox AWS_REGION_NAME=eu-west-2 \
    python -m pytest tests/test_e2e_bedrock.py -v -m e2e -s
)
