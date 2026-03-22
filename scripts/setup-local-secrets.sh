#!/usr/bin/env bash
# Prompts for deploy secrets and writes them to scripts/.env.local
# Run once; the deploy/destroy scripts source this file automatically.
#
# Usage: bash scripts/setup-local-secrets.sh

set -euo pipefail

ENV_FILE="$(dirname "$0")/.env.local"

prompt_secret() {
  local var="$1" prompt="$2" existing=""
  # Show existing value masked if already set
  if grep -q "^${var}=" "${ENV_FILE}" 2>/dev/null; then
    existing=$(grep "^${var}=" "${ENV_FILE}" | cut -d= -f2-)
    read -r -p "${prompt} [leave blank to keep existing]: " value
    if [ -z "${value}" ]; then
      echo "${existing}"
      return
    fi
  else
    read -r -p "${prompt}: " value
  fi
  echo "${value}"
}

echo "==> Sudoku local deploy secrets setup"
echo "    Writing to: ${ENV_FILE}"
echo "    (Values are not echoed. Leave blank to keep an existing value.)"
echo ""

AMPLIFY_GITHUB_TOKEN=$(prompt_secret AMPLIFY_GITHUB_TOKEN   "AMPLIFY_GITHUB_TOKEN  (GitHub classic token, repo scope)")
GOOGLE_CLIENT_ID=$(prompt_secret     GOOGLE_CLIENT_ID       "GOOGLE_CLIENT_ID      (Google OAuth client ID)")
GOOGLE_CLIENT_SECRET=$(prompt_secret GOOGLE_CLIENT_SECRET   "GOOGLE_CLIENT_SECRET  (Google OAuth client secret)")
SMOKE_TEST_USER_EMAIL=$(prompt_secret SMOKE_TEST_USER_EMAIL "SMOKE_TEST_USER_EMAIL (Cognito smoke-test user email)")
SMOKE_TEST_USER_PASSWORD=$(prompt_secret SMOKE_TEST_USER_PASSWORD "SMOKE_TEST_USER_PASSWORD (Cognito smoke-test user password)")

cat > "${ENV_FILE}" <<EOF
AMPLIFY_GITHUB_TOKEN=${AMPLIFY_GITHUB_TOKEN}
GOOGLE_CLIENT_ID=${GOOGLE_CLIENT_ID}
GOOGLE_CLIENT_SECRET=${GOOGLE_CLIENT_SECRET}
SMOKE_TEST_USER_EMAIL=${SMOKE_TEST_USER_EMAIL}
SMOKE_TEST_USER_PASSWORD=${SMOKE_TEST_USER_PASSWORD}
EOF

chmod 600 "${ENV_FILE}"
echo ""
echo "==> Written to ${ENV_FILE} (mode 600)"
