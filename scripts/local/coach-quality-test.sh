#!/usr/bin/env bash
# coach-quality-test.sh — Opt-in AI coach quality suite.
#
# Brings up dynamodb-local + backend (real Bedrock credentials forwarded, see
# docker-compose.coach-quality.yml), runs ui/tests/coach-quality/, tears the stack down.
# The `ui` service is deliberately never started — the suite talks to the backend REST API
# directly (see ui/tests/coach-quality/lib/apiClient.js), so there's no browser involved.
#
# NOT part of local-alltests.sh: these tests call a real LLM (non-deterministic prose,
# real — small — token cost) and need AWS credentials with bedrock:InvokeModel.
#
# Usage:
#   AWS_PROFILE=sandbox bash scripts/local/coach-quality-test.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_ARGS=(-f "${REPO_ROOT}/docker-compose.test.yml" -f "${REPO_ROOT}/docker-compose.coach-quality.yml")
# Explicit list, omitting `ui` — an override file can't remove a base-file service, only
# merge into one that does get started, so this is the only way to skip it.
SERVICES=(dynamodb-local dynamodb-setup dynamodb-setup-coach-quality backend)

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

echo "Building and starting services (real Bedrock enabled)..."
DOCKER_BUILDKIT=1 docker compose "${COMPOSE_ARGS[@]}" up -d --build --wait "${SERVICES[@]}"
COMPOSE_STARTED=true

echo "Running AI coach quality suite..."
(cd "${REPO_ROOT}/ui" && CI=true npm run test:coach-quality)
