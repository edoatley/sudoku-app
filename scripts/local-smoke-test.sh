#!/usr/bin/env bash
# Run the image recognition smoke test locally against the deployed environment.
#
# Resolves all required values from Terraform outputs and .env.local — no
# manual token wrangling needed. Requires only a signed-in AWS sandbox profile.
#
# Usage:
#   AWS_PROFILE=sandbox bash scripts/local-smoke-test.sh [branch]
#
# branch defaults to the current git branch.
# main → default workspace (prod); rc-* → named workspace.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="${SCRIPT_DIR}/.."

# ── Load secrets from .env.local ───────────────────────────────────────────────
ENV_FILE="${SCRIPT_DIR}/.env.local"
if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck source=/dev/null
  set -o allexport; source "${ENV_FILE}"; set +o allexport
else
  echo "Hint: run 'bash scripts/setup-local-secrets.sh' to create .env.local" >&2
fi

: "${SMOKE_TEST_USER_EMAIL:?SMOKE_TEST_USER_EMAIL must be set (run setup-local-secrets.sh)}"
: "${SMOKE_TEST_USER_PASSWORD:?SMOKE_TEST_USER_PASSWORD must be set (run setup-local-secrets.sh)}"

# ── Resolve workspace from branch ─────────────────────────────────────────────
BRANCH="${1:-$(git rev-parse --abbrev-ref HEAD)}"
if [[ "${BRANCH}" == "main" ]]; then
  WORKSPACE="default"
  ENV_TYPE="main"
else
  WORKSPACE="$(echo "${BRANCH}" | tr '/' '-' | tr '.' '-' | cut -c1-32)"
  ENV_TYPE="rc"
fi

echo "==> Local image recognition smoke test"
echo "    Branch:    ${BRANCH}"
echo "    Workspace: ${WORKSPACE}"
echo "    Env type:  ${ENV_TYPE}"
echo ""

# ── Read Terraform outputs for this workspace ──────────────────────────────────
echo "==> Reading Terraform outputs from workspace '${WORKSPACE}'..."
cd "${REPO_ROOT}/infra"
if ! AWS_PROFILE=sandbox terraform workspace select "${WORKSPACE}" -no-color > /dev/null 2>&1; then
  echo "    Workspace '${WORKSPACE}' not found — falling back to 'default' (main/prod)" >&2
  WORKSPACE="default"
  ENV_TYPE="main"
  AWS_PROFILE=sandbox terraform workspace select default -no-color > /dev/null
fi

TF_OUTPUTS=$(AWS_PROFILE=sandbox terraform output -json)

API_URL=$(echo "${TF_OUTPUTS}"      | jq -r '.api_gateway_url.value')
USER_POOL_ID=$(echo "${TF_OUTPUTS}" | jq -r '.cognito_user_pool_id.value')
CLIENT_ID=$(echo "${TF_OUTPUTS}"    | jq -r '.cognito_client_id.value')

echo "    API URL:      ${API_URL}"
echo "    User Pool:    ${USER_POOL_ID}"
echo "    Client ID:    ${CLIENT_ID}"
echo ""

# ── Authenticate with Cognito (web client — no secret) ────────────────────────
echo "==> Acquiring Cognito ID token for ${SMOKE_TEST_USER_EMAIL}..."
AUTH_PARAMS=$(jq -n \
  --arg u "${SMOKE_TEST_USER_EMAIL}" \
  --arg p "${SMOKE_TEST_USER_PASSWORD}" \
  '{"USERNAME": $u, "PASSWORD": $p}')

RESPONSE=$(AWS_PROFILE=sandbox aws cognito-idp initiate-auth \
  --auth-flow USER_PASSWORD_AUTH \
  --client-id "${CLIENT_ID}" \
  --auth-parameters "${AUTH_PARAMS}" \
  --output json)

ID_TOKEN=$(echo "${RESPONSE}" | jq -r '.AuthenticationResult.IdToken')
echo "    Token acquired (${#ID_TOKEN} chars)"
echo ""

# ── Pick fixture based on environment type ────────────────────────────────────
if [[ "${ENV_TYPE}" == "rc" ]]; then
  FIXTURE="${REPO_ROOT}/image_recognition/tests/fixtures/CI_Puzzle.jpg"
else
  FIXTURE="${REPO_ROOT}/image_recognition/tests/fixtures/puzzle_1.jpeg"
fi

# ── Run the smoke test ────────────────────────────────────────────────────────
cd "${REPO_ROOT}"
bash scripts/github/image-smoke-test.sh "${API_URL}" "${FIXTURE}" "${ID_TOKEN}"
