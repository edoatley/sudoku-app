#!/usr/bin/env bash
# Destroy a RC Terraform workspace locally.
# Usage: AMPLIFY_GITHUB_TOKEN=<token> bash scripts/infra/destroy-rc.sh <branch-name>
# Example: AMPLIFY_GITHUB_TOKEN=ghp_xxx bash scripts/infra/destroy-rc.sh rc-auth
#
# Requires: terraform, aws CLI, AWS_PROFILE=sandbox (or valid ambient credentials)

set -euo pipefail

BRANCH="${1:-}"
if [ -z "${BRANCH}" ]; then
  echo "ERROR: branch name required" >&2
  echo "Usage: AMPLIFY_GITHUB_TOKEN=<token> bash scripts/infra/destroy-rc.sh <branch-name>" >&2
  exit 1
fi

# ── Load secrets from .env.local if not already in environment ─────────────────
ENV_FILE="$(dirname "$0")/../.env.local"
if [ -f "${ENV_FILE}" ]; then
  # shellcheck source=/dev/null
  set -o allexport; source "${ENV_FILE}"; set +o allexport
fi

if [ -z "${AMPLIFY_GITHUB_TOKEN:-}" ]; then
  echo "ERROR: AMPLIFY_GITHUB_TOKEN must be set (run setup-local-secrets.sh)" >&2
  exit 1
fi

# Derive workspace name the same way the CI workflow does
WORKSPACE=$(echo "${BRANCH}" | tr '/' '-' | tr '.' '-' | cut -c1-32)

if [ "${WORKSPACE}" = "default" ]; then
  echo "ERROR: Resolved to default workspace — refusing to destroy production." >&2
  exit 1
fi

echo "==> Destroying RC environment"
echo "    Branch:    ${BRANCH}"
echo "    Workspace: ${WORKSPACE}"
echo ""

cd "$(dirname "$0")/../../infra"

echo "==> terraform init"
AWS_PROFILE=sandbox terraform init

echo ""
echo "==> Selecting workspace: ${WORKSPACE}"
if ! AWS_PROFILE=sandbox terraform workspace select "${WORKSPACE}" 2>/dev/null; then
  echo "Workspace '${WORKSPACE}' does not exist — nothing to destroy."
  exit 0
fi

echo ""
echo "==> terraform destroy"
AWS_PROFILE=sandbox terraform destroy \
  -auto-approve \
  -input=false \
  -var "github_token=${AMPLIFY_GITHUB_TOKEN}" \
  -var "lambda_zip_path=/dev/null" \
  -var "google_client_id=placeholder" \
  -var "google_client_secret=placeholder" \
  -var "smoke_test_user_email=placeholder@example.com" \
  -var "smoke_test_user_password=Placeholder1!" \
  -var "image_recognition_image_uri=000000000000.dkr.ecr.eu-west-2.amazonaws.com/placeholder:latest"

echo ""
echo "==> Deleting workspace: ${WORKSPACE}"
AWS_PROFILE=sandbox terraform workspace select default
AWS_PROFILE=sandbox terraform workspace delete "${WORKSPACE}"

echo ""
echo "==> Done. RC environment '${WORKSPACE}' has been destroyed."
