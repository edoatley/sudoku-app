#!/usr/bin/env bash
# local-alltests.sh — Run all test suites locally, mirroring the CI PR pipeline.
# Usage:
#   bash scripts/local/local-alltests.sh [--skip-image-recognition] [--skip-lint] [--skip-audit]
#                                        [--skip-e2e] [--skip-backend] [--skip-integration]
#                                        [--skip-infra]
#
# Exits 0 if all suites pass, 1 if any suite fails.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Disable AWS CLI pager so output goes straight to stdout (no interactive less)
export AWS_PAGER=""

# ── Colours ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

# ── Parse flags ──────────────────────────────────────────────────────────────
SKIP_IMAGE_RECOGNITION=false
SKIP_LINT=false
SKIP_AUDIT=false
SKIP_E2E=false
SKIP_BACKEND=false
SKIP_INTEGRATION=false
SKIP_INFRA=false

for arg in "$@"; do
  case $arg in
    --skip-image-recognition) SKIP_IMAGE_RECOGNITION=true ;;
    --skip-lint)              SKIP_LINT=true ;;
    --skip-audit)             SKIP_AUDIT=true ;;
    --skip-e2e)               SKIP_E2E=true ;;
    --skip-backend)           SKIP_BACKEND=true ;;
    --skip-integration)       SKIP_INTEGRATION=true ;;
    --skip-infra)             SKIP_INFRA=true ;;
    --help|-h)
      sed -n '2,10p' "$0" | sed 's/^# \?//'
      exit 0 ;;
    *) echo "Unknown flag: $arg (use --help)"; exit 1 ;;
  esac
done

# ── Prerequisite check ───────────────────────────────────────────────────────
echo -e "\n${BOLD}${CYAN}=== Checking prerequisites ===${RESET}"
MISSING_PREREQS=false
check_cmd() {
  if command -v "$1" &>/dev/null; then
    echo -e "  ${GREEN}✓${RESET} $1"
    return 0
  else
    echo -e "  ${YELLOW}⚠${RESET}  $1 not found — related suites will be skipped"
    MISSING_PREREQS=true
    return 1
  fi
}

check_cmd docker    && HAS_DOCKER=true    || HAS_DOCKER=false
check_cmd node      && HAS_NODE=true      || HAS_NODE=false
check_cmd java      && HAS_JAVA=true      || HAS_JAVA=false
check_cmd python3   && HAS_PYTHON=true    || HAS_PYTHON=false
check_cmd terraform && HAS_TERRAFORM=true || HAS_TERRAFORM=false
check_cmd aws       && HAS_AWS=true       || HAS_AWS=false

# ── AWS credentials check (sandbox profile) ───────────────────────────────────
echo -e "\n${BOLD}${CYAN}=== Checking AWS credentials (sandbox profile) ===${RESET}"
if [[ "${HAS_AWS}" == "true" ]]; then
  if AWS_PROFILE=sandbox aws sts get-caller-identity --output text &>/dev/null; then
    echo -e "  ${GREEN}✓${RESET} AWS sandbox profile authenticated"
    HAS_AWS_CREDS=true
  else
    echo -e "  ${RED}✗${RESET} AWS sandbox profile not authenticated — run: aws sso login --profile sandbox"
    echo -e "  ${YELLOW}⚠${RESET}  Suites requiring live AWS credentials will be skipped (Infra fmt/validate still runs)"
    HAS_AWS_CREDS=false
  fi
else
  HAS_AWS_CREDS=false
fi

# ── Suite result tracking ─────────────────────────────────────────────────────
declare -A RESULTS   # suite_name → PASS / FAIL / SKIP
declare -A DURATIONS # suite_name → seconds
OVERALL=0

# ── Helpers ──────────────────────────────────────────────────────────────────
header() { echo -e "\n${BOLD}${CYAN}=== $1 ===${RESET}"; }

record() {
  local name="$1" rc="$2" start="$3"
  local elapsed=$(( $(date +%s) - start ))
  DURATIONS["$name"]="${elapsed}s"
  if [[ $rc -eq 0 ]]; then
    RESULTS["$name"]="PASS"
    echo -e "${GREEN}✓ PASS${RESET} — $name (${elapsed}s)"
  else
    RESULTS["$name"]="FAIL"
    OVERALL=1
    echo -e "${RED}✗ FAIL${RESET} — $name (${elapsed}s)"
  fi
}

skip() {
  local name="$1" reason="$2"
  RESULTS["$name"]="SKIP"
  DURATIONS["$name"]="—"
  echo -e "${YELLOW}⏭  SKIP${RESET} — $name ($reason)"
}

# ── DynamoDB Local cleanup trap ───────────────────────────────────────────────
DYNAMO_CONTAINER="sudoku-dynamo-test-$$"
COMPOSE_STARTED=false

cleanup() {
  if docker ps -q --filter "name=${DYNAMO_CONTAINER}" 2>/dev/null | grep -q .; then
    echo -e "\n${YELLOW}Cleaning up DynamoDB Local container...${RESET}"
    docker rm -f "${DYNAMO_CONTAINER}" &>/dev/null || true
  fi
  if [[ "${COMPOSE_STARTED}" == "true" ]]; then
    echo -e "\n${YELLOW}Tearing down Docker Compose stack...${RESET}"
    docker compose -f "${REPO_ROOT}/docker-compose.test.yml" down &>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

# ─────────────────────────────────────────────────────────────────────────────
# Suite 1: Image Recognition
# ─────────────────────────────────────────────────────────────────────────────
SUITE="Image Recognition (pytest)"
header "$SUITE"

if [[ "${SKIP_IMAGE_RECOGNITION}" == "true" ]]; then
  skip "$SUITE" "--skip-image-recognition"
elif [[ "${HAS_PYTHON}" == "false" ]]; then
  skip "$SUITE" "python3 not found"
else
  IR_DIR="${REPO_ROOT}/image_recognition"
  VENV="${IR_DIR}/.venv"
  if [[ ! -f "${VENV}/bin/python" ]]; then
    echo "Setting up .venv..."
    python3 -m venv "${VENV}"
    "${VENV}/bin/pip" install -q -r "${IR_DIR}/requirements-dev.txt"
  fi

  t=$(date +%s)
  (
    cd "${IR_DIR}"
    source "${VENV}/bin/activate"
    python -m pytest --cov --cov-report=term-missing -m "not real_images and not e2e"
  )
  record "$SUITE" $? $t
fi

# ─────────────────────────────────────────────────────────────────────────────
# Suite 2: Frontend Lint
# ─────────────────────────────────────────────────────────────────────────────
SUITE="Frontend Lint (ESLint)"
header "$SUITE"

if [[ "${SKIP_LINT}" == "true" ]]; then
  skip "$SUITE" "--skip-lint"
elif [[ "${HAS_NODE}" == "false" ]]; then
  skip "$SUITE" "node not found"
else
  t=$(date +%s)
  (cd "${REPO_ROOT}/ui" && npm run lint)
  record "$SUITE" $? $t
fi

# ─────────────────────────────────────────────────────────────────────────────
# Suite 3: Frontend npm audit
# ─────────────────────────────────────────────────────────────────────────────
SUITE="Frontend Security Audit (npm audit)"
header "$SUITE"

if [[ "${SKIP_AUDIT}" == "true" ]]; then
  skip "$SUITE" "--skip-audit"
elif [[ "${HAS_NODE}" == "false" ]]; then
  skip "$SUITE" "node not found"
else
  t=$(date +%s)
  (cd "${REPO_ROOT}/ui" && npm audit --audit-level=high)
  record "$SUITE" $? $t
fi

# ─────────────────────────────────────────────────────────────────────────────
# Suite 4: Frontend E2E (Playwright)
# ─────────────────────────────────────────────────────────────────────────────
SUITE="Frontend E2E (Playwright)"
header "$SUITE"

if [[ "${SKIP_E2E}" == "true" ]]; then
  skip "$SUITE" "--skip-e2e"
elif [[ "${HAS_NODE}" == "false" ]]; then
  skip "$SUITE" "node not found"
else
  t=$(date +%s)
  (cd "${REPO_ROOT}/ui" && CI=true npm run test:e2e)
  record "$SUITE" $? $t
fi

# ─────────────────────────────────────────────────────────────────────────────
# Suite 5: Backend (Maven + DynamoDB Local)
# ─────────────────────────────────────────────────────────────────────────────
SUITE="Backend (Maven verify + JaCoCo)"
header "$SUITE"

if [[ "${SKIP_BACKEND}" == "true" ]]; then
  skip "$SUITE" "--skip-backend"
elif [[ "${HAS_JAVA}" == "false" ]]; then
  skip "$SUITE" "java not found"
elif [[ "${HAS_DOCKER}" == "false" ]]; then
  skip "$SUITE" "docker not found (needed for DynamoDB Local)"
elif [[ "${HAS_AWS}" == "false" ]]; then
  skip "$SUITE" "aws CLI not found (needed to create DynamoDB tables)"
else
  # Start LocalStack (mirrors CI: localstack/localstack on port 4566)
  echo "Starting LocalStack..."
  docker run -d --name "${DYNAMO_CONTAINER}" -p 4566:4566 localstack/localstack:latest

  echo "Waiting for LocalStack to be ready..."
  for i in $(seq 1 20); do
    if curl -sf http://localhost:4566/_localstack/health &>/dev/null; then
      echo "LocalStack is ready"
      break
    fi
    echo "  Waiting... ($i/20)"
    sleep 2
  done

  # Create tables (mirrors .github/actions/create-localstack-dynamodb/action.yml)
  echo "Creating DynamoDB tables..."
  AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
  aws --endpoint-url=http://localhost:4566 dynamodb create-table \
    --table-name SudokuGames \
    --attribute-definitions AttributeName=userId,AttributeType=S AttributeName=gameId,AttributeType=S \
    --key-schema AttributeName=userId,KeyType=HASH AttributeName=gameId,KeyType=RANGE \
    --billing-mode PAY_PER_REQUEST \
    --region us-east-1

  AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
  aws --endpoint-url=http://localhost:4566 dynamodb create-table \
    --table-name SudokuPlayers \
    --attribute-definitions AttributeName=userId,AttributeType=S \
    --key-schema AttributeName=userId,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --region us-east-1

  AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
  aws --endpoint-url=http://localhost:4566 dynamodb create-table \
    --table-name SudokuCoachRateLimits \
    --attribute-definitions AttributeName=userId,AttributeType=S AttributeName=window,AttributeType=S \
    --key-schema AttributeName=userId,KeyType=HASH AttributeName=window,KeyType=RANGE \
    --billing-mode PAY_PER_REQUEST \
    --region us-east-1

  t=$(date +%s)
  (
    cd "${REPO_ROOT}/backend"
    export AWS_ACCESS_KEY_ID=test
    export AWS_SECRET_ACCESS_KEY=test
    export AWS_DEFAULT_REGION=us-east-1
    ./mvnw verify -DskipITs=false
  )
  BACKEND_RC=$?

  # Tear down DynamoDB Local immediately
  docker rm -f "${DYNAMO_CONTAINER}" &>/dev/null || true

  record "$SUITE" $BACKEND_RC $t
fi

# ─────────────────────────────────────────────────────────────────────────────
# Suite 6: Integration (Docker Compose + Playwright)
# ─────────────────────────────────────────────────────────────────────────────
SUITE="Integration (Docker Compose + Playwright)"
header "$SUITE"

if [[ "${SKIP_INTEGRATION}" == "true" ]]; then
  skip "$SUITE" "--skip-integration"
elif [[ "${HAS_DOCKER}" == "false" ]]; then
  skip "$SUITE" "docker not found"
elif [[ "${HAS_NODE}" == "false" ]]; then
  skip "$SUITE" "node not found"
else
t=$(date +%s)
  INT_RC=0

  echo "Building and starting services in parallel..."
  # --build ensures images are updated if source code changed
  # --wait will block until all healthchecks pass (Docker 20.10.13+)
  # Explicitly enable BuildKit for the cache mounts to work
  DOCKER_BUILDKIT=1 docker compose -f "${REPO_ROOT}/docker-compose.test.yml" up -d --build --wait
  COMPOSE_STARTED=true

  # Optional: Keep your manual loop if you want more granular console output,
  # otherwise the '--wait' flag above handles the synchronization.
  echo "Services are up and healthy."

  (cd "${REPO_ROOT}/ui" && CI=true npx playwright test --config playwright.integration.config.js)
  INT_RC=$?

  if [[ $INT_RC -eq 0 ]]; then
    echo "Running hint demo tests (VITE_DEV_TOOLS=true stack)..."
    (cd "${REPO_ROOT}/ui" && CI=true npm run test:hint-demos)
    INT_RC=$?
  fi

  echo "Tearing down services..."
  docker compose -f "${REPO_ROOT}/docker-compose.test.yml" down
  COMPOSE_STARTED=false

  record "$SUITE" $INT_RC $t
fi

# ─────────────────────────────────────────────────────────────────────────────
# Suite 7: Infra (Terraform)
# ─────────────────────────────────────────────────────────────────────────────
SUITE="Infra (Terraform fmt + validate)"
header "$SUITE"

if [[ "${SKIP_INFRA}" == "true" ]]; then
  # Warn if .tf files have been modified — skipping infra when Terraform changed is almost always wrong
  if git -C "${REPO_ROOT}" diff --name-only HEAD 2>/dev/null | grep -q '\.tf$'; then
    echo -e "${RED}ERROR: --skip-infra passed but .tf files are modified. Run without --skip-infra.${RESET}" >&2
    RESULTS["$SUITE"]="FAIL"
    DURATIONS["$SUITE"]="—"
    OVERALL=1
  else
    skip "$SUITE" "--skip-infra"
  fi
elif [[ "${HAS_TERRAFORM}" == "false" ]]; then
  skip "$SUITE" "terraform not found"
else
  # fmt + validate use -backend=false and do not require AWS credentials
  t=$(date +%s)
  (
    cd "${REPO_ROOT}/infra"
    terraform fmt -check -recursive
    terraform init -upgrade -backend=false -input=false -no-color
    terraform validate
  )
  record "$SUITE" $? $t
fi

# ─────────────────────────────────────────────────────────────────────────────
# Summary
# ─────────────────────────────────────────────────────────────────────────────
echo -e "\n${BOLD}${CYAN}════════════════════════════════════════${RESET}"
echo -e "${BOLD}  Test Summary${RESET}"
echo -e "${BOLD}${CYAN}════════════════════════════════════════${RESET}"

SUITE_ORDER=(
  "Image Recognition (pytest)"
  "Frontend Lint (ESLint)"
  "Frontend Security Audit (npm audit)"
  "Frontend E2E (Playwright)"
  "Backend (Maven verify + JaCoCo)"
  "Integration (Docker Compose + Playwright)"
  "Infra (Terraform fmt + validate)"
)

for suite in "${SUITE_ORDER[@]}"; do
  result="${RESULTS[$suite]:-SKIP}"
  dur="${DURATIONS[$suite]:-—}"
  if [[ "$result" == "PASS" ]]; then
    icon="${GREEN}✓ PASS${RESET}"
  elif [[ "$result" == "FAIL" ]]; then
    icon="${RED}✗ FAIL${RESET}"
  else
    icon="${YELLOW}⏭ SKIP${RESET}"
  fi
  printf "  %-42s %b  (%s)\n" "$suite" "$icon" "$dur"
done

echo -e "${BOLD}${CYAN}════════════════════════════════════════${RESET}"

if [[ $OVERALL -eq 0 ]]; then
  echo -e "\n${BOLD}${GREEN}✓ All suites passed.${RESET}\n"
else
  echo -e "\n${BOLD}${RED}✗ One or more suites failed. See output above.${RESET}\n"
fi

exit $OVERALL
