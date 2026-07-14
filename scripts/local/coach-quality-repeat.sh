#!/usr/bin/env bash
# coach-quality-repeat.sh — Run the AI coach quality suite RUNS times against one
# already-built stack, then aggregate the resulting reports into a single summary.
#
# coach-quality-test.sh brings the stack up and down around a single run — looping it
# directly would rebuild/teardown the backend container every iteration. This script brings
# the stack up once (real Bedrock enabled), loops `npm run test:coach-quality` RUNS times
# against it, tears down once at the end, moves the new reports into a labelled subdirectory
# under ui/tests/coach-quality/reports/, and prints/saves an aggregate summary via
# ui/tests/coach-quality/lib/aggregate.js (fallback rate, latency, token totals).
#
# A scenario assertion failure fails that Playwright run's exit code (see
# ui/tests/coach-quality/coach-quality.spec.js) — that's expected and part of what's being
# measured here, not a reason to abort the remaining runs, so failures are tolerated in the
# loop and only reflected in the final summary.
#
# Usage:
#   AWS_PROFILE=sandbox bash scripts/local/coach-quality-repeat.sh
#   AWS_PROFILE=sandbox RUNS=10 LABEL=haiku-4-5-baseline bash scripts/local/coach-quality-repeat.sh
#   AWS_PROFILE=sandbox COACH_BEDROCK_MODEL_ID=eu.anthropic.claude-sonnet-4-6-... \
#     RUNS=10 LABEL=sonnet-4-6 bash scripts/local/coach-quality-repeat.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_ARGS=(-f "${REPO_ROOT}/docker-compose.test.yml" -f "${REPO_ROOT}/docker-compose.coach-quality.yml")
SERVICES=(dynamodb-local dynamodb-setup dynamodb-setup-coach-quality backend)
REPORTS_DIR="${REPO_ROOT}/ui/tests/coach-quality/reports"

RUNS="${RUNS:-10}"
LABEL="${LABEL:-repeat-$(date -u +%Y-%m-%dT%H-%M-%SZ)}"
LABEL_DIR="${REPORTS_DIR}/${LABEL}"

if [[ -e "${LABEL_DIR}" ]]; then
  echo "ERROR: ${LABEL_DIR} already exists — pick a different LABEL." >&2
  exit 1
fi

echo "Checking AWS credentials (profile: ${AWS_PROFILE:-default})..."
if ! aws sts get-caller-identity --output text &>/dev/null; then
  echo "ERROR: AWS credentials not authenticated. Run: aws sso login --profile ${AWS_PROFILE:-sandbox}" >&2
  exit 1
fi

# Forward short-lived credentials into the backend container's environment — the compose
# overlay requires AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY to be set or it refuses to start.
eval "$(aws configure export-credentials --format env)"
export AWS_REGION="${AWS_REGION:-eu-west-2}"

COMPOSE_STARTED=false
cleanup() {
  if [[ "${COMPOSE_STARTED}" == "true" ]]; then
    echo "Tearing down services..."
    docker compose "${COMPOSE_ARGS[@]}" down
  fi
}
trap cleanup EXIT

echo "Building and starting services (real Bedrock enabled, model: ${COACH_BEDROCK_MODEL_ID:-default})..."
DOCKER_BUILDKIT=1 docker compose "${COMPOSE_ARGS[@]}" up -d --build --wait "${SERVICES[@]}"
COMPOSE_STARTED=true

# Snapshot existing report filenames so only this session's new reports get moved into the
# labelled subdirectory below — reports/ accumulates output from every past run too.
BEFORE_LIST="$(mktemp)"
find "${REPORTS_DIR}" -maxdepth 1 -type f -name '*.json' 2>/dev/null | sort > "${BEFORE_LIST}"

FAILED_RUNS=0
for i in $(seq 1 "${RUNS}"); do
  echo ""
  echo "=== Run ${i}/${RUNS} ==="
  if ! (cd "${REPO_ROOT}/ui" && CI=true npm run test:coach-quality); then
    FAILED_RUNS=$((FAILED_RUNS + 1))
    echo "Run ${i} had scenario failures — continuing (still counted in the summary)."
  fi
done

AFTER_LIST="$(mktemp)"
find "${REPORTS_DIR}" -maxdepth 1 -type f -name '*.json' 2>/dev/null | sort > "${AFTER_LIST}"
NEW_REPORTS="$(comm -13 "${BEFORE_LIST}" "${AFTER_LIST}")"
rm -f "${BEFORE_LIST}" "${AFTER_LIST}"

mkdir -p "${LABEL_DIR}"
while IFS= read -r report; do
  [[ -z "${report}" ]] && continue
  mv "${report}" "${LABEL_DIR}/"
  md="${report%.json}.md"
  [[ -f "${md}" ]] && mv "${md}" "${LABEL_DIR}/"
done <<< "${NEW_REPORTS}"

echo ""
echo "=== Summary (${RUNS} runs, ${FAILED_RUNS} with scenario failures, label: ${LABEL}) ==="
(cd "${REPO_ROOT}/ui" && node tests/coach-quality/lib/aggregate.js "${LABEL}" | tee "${LABEL_DIR}/summary.txt")

echo ""
echo "Reports and summary saved to: ${LABEL_DIR}"
