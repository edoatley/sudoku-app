#!/usr/bin/env bash
# Run a Terraform plan + apply locally, mirroring what deploy.yml does.
# Skips Amplify build trigger and smoke tests — infra only.
#
# Usage:
#   AMPLIFY_GITHUB_TOKEN=<token> \
#   GOOGLE_CLIENT_ID=<id> \
#   GOOGLE_CLIENT_SECRET=<secret> \
#   SMOKE_TEST_USER_EMAIL=<email> \
#   SMOKE_TEST_USER_PASSWORD=<password> \
#   bash scripts/deploy-local.sh [branch]
#
# branch defaults to the current git branch.
# main → default workspace (prod); rc-* → named workspace.

set -euo pipefail

BRANCH="${1:-$(git rev-parse --abbrev-ref HEAD)}"

# ── Load secrets from .env.local if not already in environment ─────────────────
ENV_FILE="$(dirname "$0")/.env.local"
if [ -f "${ENV_FILE}" ]; then
  # shellcheck source=/dev/null
  set -o allexport; source "${ENV_FILE}"; set +o allexport
else
  echo "Hint: run 'bash scripts/setup-local-secrets.sh' once to avoid passing secrets on the command line."
fi

: "${AMPLIFY_GITHUB_TOKEN:?AMPLIFY_GITHUB_TOKEN must be set (run setup-local-secrets.sh)}"
: "${GOOGLE_CLIENT_ID:?GOOGLE_CLIENT_ID must be set (run setup-local-secrets.sh)}"
: "${GOOGLE_CLIENT_SECRET:?GOOGLE_CLIENT_SECRET must be set (run setup-local-secrets.sh)}"
: "${SMOKE_TEST_USER_EMAIL:?SMOKE_TEST_USER_EMAIL must be set (run setup-local-secrets.sh)}"
: "${SMOKE_TEST_USER_PASSWORD:?SMOKE_TEST_USER_PASSWORD must be set (run setup-local-secrets.sh)}"

# ── Resolve workspace (mirrors deploy.yml logic) ───────────────────────────────
SANITIZED=$(echo "${BRANCH}" | tr '/' '-' | tr '.' '-' | cut -c1-32)
if [ "${BRANCH}" = "main" ]; then
  WORKSPACE="default"
  ENV="prod"
else
  WORKSPACE="${SANITIZED}"
  ENV="${SANITIZED}"
fi

echo "==> Local deploy"
echo "    Branch:    ${BRANCH}"
echo "    Workspace: ${WORKSPACE}"
echo "    Env label: ${ENV}"
echo ""

REPO_ROOT="$(dirname "$0")/.."
cd "${REPO_ROOT}/infra"

# ── Locate Lambda zip ──────────────────────────────────────────────────────────
ACCOUNT_ID=$(AWS_PROFILE=sandbox aws sts get-caller-identity --query Account --output text)
BUCKET="sudoku-lambda-zip-${ACCOUNT_ID}"
ZIP_PATH="${REPO_ROOT}/backend/target/function.zip"

if [ ! -f "${ZIP_PATH}" ]; then
  echo "==> No local zip found — trying s3://${BUCKET}/${WORKSPACE}/function.zip"
  mkdir -p "${REPO_ROOT}/backend/target"
  if AWS_PROFILE=sandbox aws s3 ls "s3://${BUCKET}/${WORKSPACE}/function.zip" > /dev/null 2>&1; then
    AWS_PROFILE=sandbox aws s3 cp "s3://${BUCKET}/${WORKSPACE}/function.zip" "${ZIP_PATH}"
    echo "    Downloaded from S3."
  else
    echo "    Not found in S3 — building from source (this may take a minute)..."
    (cd "${REPO_ROOT}/backend" && ./mvnw package -DskipTests -q)
    if [ ! -f "${ZIP_PATH}" ]; then
      echo "ERROR: Maven build did not produce ${ZIP_PATH}"
      exit 1
    fi
    echo "    Built successfully."
  fi
else
  echo "==> Using local zip: ${ZIP_PATH}"
fi
echo ""

# ── Terraform ─────────────────────────────────────────────────────────────────
echo "==> terraform init"
AWS_PROFILE=sandbox terraform init

echo ""
echo "==> Selecting workspace: ${WORKSPACE}"
if [ "${WORKSPACE}" = "default" ]; then
  AWS_PROFILE=sandbox terraform workspace select default
else
  AWS_PROFILE=sandbox terraform workspace select "${WORKSPACE}" \
    || AWS_PROFILE=sandbox terraform workspace new "${WORKSPACE}"
fi

echo ""
echo "==> terraform plan"
# IMAGE_RECOGNITION_IMAGE_URI: if not set, look up the currently deployed image URI
# from the Lambda function so we don't need a stale value in .env.local.
if [ -z "${IMAGE_RECOGNITION_IMAGE_URI:-}" ]; then
  FUNC_NAME="sudoku-image-recognition$([ "${WORKSPACE}" = "default" ] && echo "" || echo "-${WORKSPACE}")"
  IMAGE_RECOGNITION_IMAGE_URI=$(AWS_PROFILE=sandbox aws lambda get-function \
    --function-name "${FUNC_NAME}" \
    --query 'Code.ImageUri' --output text 2>/dev/null || echo "")
  if [ -n "${IMAGE_RECOGNITION_IMAGE_URI}" ]; then
    echo "    Using current deployed image: ${IMAGE_RECOGNITION_IMAGE_URI}"
  else
    echo "    No deployed image found — image recognition Lambda will not be updated."
  fi
fi

# RC_COGNITO_WEB_CLIENT_ID / RC_COGNITO_SMOKE_CLIENT_ID: required for rc-* workspaces.
# These come from the long-lived rc-shared workspace and rarely change.
# Get them from:
#   cd infra && AWS_PROFILE=sandbox terraform workspace select rc-shared
#   AWS_PROFILE=sandbox terraform output rc_shared_cognito_web_client_id
#   AWS_PROFILE=sandbox terraform output rc_shared_cognito_smoke_client_id
RC_COGNITO_WEB_CLIENT_ID="${RC_COGNITO_WEB_CLIENT_ID:-}"
RC_COGNITO_SMOKE_CLIENT_ID="${RC_COGNITO_SMOKE_CLIENT_ID:-}"

RC_COGNITO_VARS=()
if [ "${WORKSPACE}" != "default" ]; then
  if [ -z "${RC_COGNITO_WEB_CLIENT_ID}" ] || [ -z "${RC_COGNITO_SMOKE_CLIENT_ID}" ]; then
    echo "ERROR: RC_COGNITO_WEB_CLIENT_ID and RC_COGNITO_SMOKE_CLIENT_ID must be set for rc-* workspaces."
    echo "  Get them from:"
    echo "    cd infra"
    echo "    AWS_PROFILE=sandbox terraform workspace select rc-shared"
    echo "    AWS_PROFILE=sandbox terraform output rc_shared_cognito_web_client_id"
    echo "    AWS_PROFILE=sandbox terraform output rc_shared_cognito_smoke_client_id"
    exit 1
  fi
  RC_COGNITO_VARS=(
    -var "rc_cognito_web_client_id=${RC_COGNITO_WEB_CLIENT_ID}"
    -var "rc_cognito_smoke_client_id=${RC_COGNITO_SMOKE_CLIENT_ID}"
  )
fi

AWS_PROFILE=sandbox terraform plan -out=tfplan \
  -input=false \
  -var "github_token=${AMPLIFY_GITHUB_TOKEN}" \
  -var "environment=${ENV}" \
  -var "lambda_zip_path=${ZIP_PATH}" \
  -var "google_client_id=${GOOGLE_CLIENT_ID}" \
  -var "google_client_secret=${GOOGLE_CLIENT_SECRET}" \
  -var "smoke_test_user_email=${SMOKE_TEST_USER_EMAIL}" \
  -var "smoke_test_user_password=${SMOKE_TEST_USER_PASSWORD}" \
  -var "image_recognition_image_uri=${IMAGE_RECOGNITION_IMAGE_URI}" \
  "${RC_COGNITO_VARS[@]}"

echo ""
read -r -p "==> Apply the plan? [y/N] " CONFIRM
if [[ "${CONFIRM}" != "y" && "${CONFIRM}" != "Y" ]]; then
  echo "Aborted."
  exit 0
fi

echo ""
echo "==> terraform apply"
AWS_PROFILE=sandbox terraform apply -auto-approve -input=false tfplan

# Print NS records after apply (default workspace only).
# Note: first apply on default workspace may block for up to 40 min while ACM
# provisions the certificate for the domain association.
if [ "${WORKSPACE}" = "default" ]; then
  NS=$(AWS_PROFILE=sandbox terraform output -json subdomain_nameservers 2>/dev/null)
  if echo "${NS}" | jq -e 'length > 0' > /dev/null 2>&1; then
    echo ""
    echo "========================================================"
    echo "  Route53 NS records for sudoku.edoatley.co.uk"
    echo "  Run scripts/delegate-dns.sh with these values:"
    echo "========================================================"
    echo "${NS}" | jq -r '.[]'
    echo "========================================================"
  fi

  BETA_NS=$(AWS_PROFILE=sandbox terraform output -json sudoku_beta_nameservers 2>/dev/null)
  if echo "${BETA_NS}" | jq -e 'length > 0' > /dev/null 2>&1; then
    echo ""
    echo "========================================================"
    echo "  Route53 NS records for sudoku-beta.edoatley.co.uk"
    echo "  Update NS delegation in parent account with these:"
    echo "========================================================"
    echo "${BETA_NS}" | jq -r '.[]'
    echo "========================================================"
  fi
fi

# ── Capture outputs ────────────────────────────────────────────────────────────
echo ""
echo "==> Capturing outputs"
API_ID=$(AWS_PROFILE=sandbox terraform output -raw api_gateway_api_id)
AMPLIFY_URL=$(AWS_PROFILE=sandbox terraform output -raw amplify_app_url)
AMPLIFY_DEFAULT_URL=$(AWS_PROFILE=sandbox terraform output -raw amplify_default_url)
USER_POOL_ID=$(AWS_PROFILE=sandbox terraform output -raw cognito_user_pool_id)
CLIENT_ID=$(AWS_PROFILE=sandbox terraform output -raw cognito_client_id)

# ── Tighten CORS ───────────────────────────────────────────────────────────────
echo ""
echo "==> Tightening CORS to ${AMPLIFY_URL} + ${AMPLIFY_DEFAULT_URL}"
CORS_CONFIG=$(printf \
  '{"AllowMethods":["GET","POST","PATCH","OPTIONS"],"AllowOrigins":["%s","%s","http://localhost:5173"],"AllowHeaders":["Content-Type","Authorization"],"MaxAge":300}' \
  "${AMPLIFY_URL}" "${AMPLIFY_DEFAULT_URL}")
AWS_PROFILE=sandbox aws apigatewayv2 update-api \
  --api-id "${API_ID}" \
  --cors-configuration "${CORS_CONFIG}"

# ── Tighten Cognito callback URLs ──────────────────────────────────────────────
echo ""
echo "==> Tightening Cognito callback URLs to ${AMPLIFY_URL} + ${AMPLIFY_DEFAULT_URL}"
AWS_PROFILE=sandbox aws cognito-idp update-user-pool-client \
  --user-pool-id "${USER_POOL_ID}" \
  --client-id "${CLIENT_ID}" \
  --callback-urls "${AMPLIFY_URL}/" "${AMPLIFY_DEFAULT_URL}/" "http://localhost:5173/" \
  --logout-urls "${AMPLIFY_URL}/" "${AMPLIFY_DEFAULT_URL}/" "http://localhost:5173/" \
  --supported-identity-providers "Google" \
  --allowed-o-auth-flows "code" \
  --allowed-o-auth-scopes "openid" "email" "profile" \
  --allowed-o-auth-flows-user-pool-client

# ── Summary ────────────────────────────────────────────────────────────────────
echo ""
echo "========================================================================"
echo "  Deploy complete"
echo "========================================================================"
echo ""
AWS_PROFILE=sandbox terraform output
