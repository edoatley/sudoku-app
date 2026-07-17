#!/usr/bin/env bash
# Run the full smoke test suite locally against the deployed environment.
#
# Mirrors the CI smoke-tests.yml workflow: API smoke tests, image recognition,
# and Playwright integration tests (hint-demos skipped for main/prod).
#
# Resolves all required values from Terraform outputs and .env.local — no
# manual token wrangling needed. Requires only a signed-in AWS sandbox profile.
#
# Usage:
#   AWS_PROFILE=sandbox bash scripts/local/local-smoke-test.sh [branch] [--skip-playwright] [--skip-image] [--skip-api]
#
# branch defaults to the current git branch.
# main → default workspace (prod); rc-* → named workspace.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="${SCRIPT_DIR}/../.."

# ── Parse args ────────────────────────────────────────────────────────────────
BRANCH=""
SKIP_PLAYWRIGHT=false
SKIP_IMAGE=false
SKIP_API=false

for arg in "$@"; do
  case "${arg}" in
    --skip-playwright) SKIP_PLAYWRIGHT=true ;;
    --skip-image)      SKIP_IMAGE=true ;;
    --skip-api)        SKIP_API=true ;;
    --*)               echo "Unknown flag: ${arg}" >&2; exit 1 ;;
    *)                 BRANCH="${arg}" ;;
  esac
done

# ── Load secrets from .env.local ───────────────────────────────────────────────
ENV_FILE="${SCRIPT_DIR}/../.env.local"
if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck source=/dev/null
  set -o allexport; source "${ENV_FILE}"; set +o allexport
else
  echo "Hint: run 'bash scripts/infra/setup-local-secrets.sh' to create .env.local" >&2
fi

: "${SMOKE_TEST_USER_EMAIL:?SMOKE_TEST_USER_EMAIL must be set (run setup-local-secrets.sh)}"
: "${SMOKE_TEST_USER_PASSWORD:?SMOKE_TEST_USER_PASSWORD must be set (run setup-local-secrets.sh)}"

# ── Resolve workspace from branch ─────────────────────────────────────────────
BRANCH="${BRANCH:-$(git rev-parse --abbrev-ref HEAD)}"
if [[ "${BRANCH}" == "main" ]]; then
  WORKSPACE="default"
  ENV_TYPE="main"
else
  WORKSPACE="$(echo "${BRANCH}" | tr '/' '-' | tr '.' '-' | cut -c1-32)"
  ENV_TYPE="rc"
fi

echo "==> Local smoke test"
echo "    Branch:    ${BRANCH}"
echo "    Workspace: ${WORKSPACE}"
echo "    Env type:  ${ENV_TYPE}"
echo ""

# ── Read Terraform outputs for this workspace ──────────────────────────────────
echo "==> Reading Terraform outputs from workspace '${WORKSPACE}'..."
cd "${REPO_ROOT}/infra/aws"
if ! AWS_PROFILE=sandbox terraform workspace select "${WORKSPACE}" -no-color > /dev/null 2>&1; then
  echo "    Workspace '${WORKSPACE}' not found — falling back to 'default' (main/prod)" >&2
  WORKSPACE="default"
  ENV_TYPE="main"
  AWS_PROFILE=sandbox terraform workspace select default -no-color > /dev/null
fi

TF_OUTPUTS=$(AWS_PROFILE=sandbox terraform output -json)

API_URL=$(echo "${TF_OUTPUTS}"         | jq -r '.api_gateway_url.value')
USER_POOL_ID=$(echo "${TF_OUTPUTS}"    | jq -r '.cognito_user_pool_id.value')
AMPLIFY_APP_URL=$(echo "${TF_OUTPUTS}" | jq -r '.amplify_app_url.value')

echo "    API URL:         ${API_URL}"
echo "    User Pool:       ${USER_POOL_ID}"
echo "    Amplify URL:     ${AMPLIFY_APP_URL}"
echo ""

# ── Resolve Cognito web client ID ─────────────────────────────────────────────
echo "==> Resolving Cognito web client ID..."
if [[ "${ENV_TYPE}" == "rc" ]]; then
  CLIENT_ID=$(AWS_PROFILE=sandbox aws cognito-idp list-user-pool-clients \
    --user-pool-id "${USER_POOL_ID}" \
    --query "UserPoolClients[?ClientName=='sudoku-web-rc'].ClientId | [0]" \
    --output text)
  if [[ -z "${CLIENT_ID}" || "${CLIENT_ID}" == "None" ]]; then
    echo "ERROR: Could not resolve web client ID for pool ${USER_POOL_ID}" >&2
    exit 1
  fi
else
  CLIENT_ID=$(echo "${TF_OUTPUTS}" | jq -r '.cognito_client_id.value')
fi
echo "    Client ID: ${CLIENT_ID}"
echo ""

# ── Authenticate with Cognito (web client — no secret) ────────────────────────
echo "==> Acquiring Cognito tokens for ${SMOKE_TEST_USER_EMAIL}..."
AUTH_PARAMS=$(jq -n \
  --arg u "${SMOKE_TEST_USER_EMAIL}" \
  --arg p "${SMOKE_TEST_USER_PASSWORD}" \
  '{"USERNAME": $u, "PASSWORD": $p}')

RESPONSE=$(AWS_PROFILE=sandbox aws cognito-idp initiate-auth \
  --auth-flow USER_PASSWORD_AUTH \
  --client-id "${CLIENT_ID}" \
  --auth-parameters "${AUTH_PARAMS}" \
  --output json)

ID_TOKEN=$(echo "${RESPONSE}"     | jq -r '.AuthenticationResult.IdToken')
ACCESS_TOKEN=$(echo "${RESPONSE}" | jq -r '.AuthenticationResult.AccessToken')
echo "    ID token acquired (${#ID_TOKEN} chars)"
echo ""

cd "${REPO_ROOT}"

# ── API smoke tests ───────────────────────────────────────────────────────────
if [[ "${SKIP_API}" == "true" ]]; then
  echo "==> Skipping API smoke tests (--skip-api)"
else
  echo "==> Running API smoke tests..."
  bash scripts/github/api-smoke-tests.sh "${API_URL}"
  echo ""
fi

# ── Image recognition smoke test ─────────────────────────────────────────────
if [[ "${SKIP_IMAGE}" == "true" ]]; then
  echo "==> Skipping image recognition smoke test (--skip-image)"
else
  echo "==> Running image recognition smoke test..."
  if [[ "${ENV_TYPE}" == "rc" ]]; then
    FIXTURE="${REPO_ROOT}/image_recognition/tests/fixtures/CI_Puzzle.jpg"
  else
    FIXTURE="${REPO_ROOT}/image_recognition/tests/fixtures/puzzle_1.jpeg"
  fi
  bash scripts/github/image-smoke-test.sh "${API_URL}" "${FIXTURE}" "${ID_TOKEN}"
  echo ""
fi

# ── Playwright integration tests ──────────────────────────────────────────────
if [[ "${SKIP_PLAYWRIGHT}" == "true" ]]; then
  echo "==> Skipping Playwright tests (--skip-playwright)"
else
  echo "==> Running Playwright integration tests against ${AMPLIFY_APP_URL}..."
  echo "    (hint-demos skipped for main — VITE_DEV_TOOLS=false in prod)"
  echo ""

  cd "${REPO_ROOT}/ui"

  PLAYWRIGHT_CMD="npx playwright test --config playwright.integration.config.js"
  if [[ "${ENV_TYPE}" == "main" ]]; then
    PLAYWRIGHT_CMD="${PLAYWRIGHT_CMD} --grep-invert 'hint demo'"
  fi

  INTEGRATION_BASE_URL="${AMPLIFY_APP_URL}" \
  COGNITO_USER_POOL_ID="${USER_POOL_ID}" \
  COGNITO_CLIENT_ID="${CLIENT_ID}" \
  SMOKE_ID_TOKEN="${ID_TOKEN}" \
  SMOKE_ACCESS_TOKEN="${ACCESS_TOKEN}" \
    eval "${PLAYWRIGHT_CMD}"
fi
