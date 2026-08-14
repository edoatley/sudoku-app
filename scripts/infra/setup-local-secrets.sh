#!/usr/bin/env bash
# Collects the INFRASTRUCTURE / deploy secrets and writes them to scripts/.env.local.
# Run once; deploy-local.sh, destroy-rc.sh and gcp-identity-platform-bootstrap.sh source it.
#
# ┌─ Two different .env.local files — don't confuse them ──────────────────────────────────┐
# │  scripts/.env.local  (THIS file)  — infra/deploy secrets read by the shell scripts:     │
# │                                     GOOGLE_CLIENT_ID/SECRET, AMPLIFY_GITHUB_TOKEN,       │
# │                                     SMOKE_TEST_USER_*. Server-side only.                 │
# │  ui/.env.local                    — Vite frontend build/runtime config (VITE_* only):    │
# │                                     VITE_API_URL, VITE_FIREBASE_*, VITE_AI_COACH, …       │
# │                                     Created by hand for `npm run dev`; NOT written here.  │
# └────────────────────────────────────────────────────────────────────────────────────────┘
#
# GOOGLE_CLIENT_ID/SECRET are auto-detected from the AWS Terraform state (where the Cognito Google
# provider stores them) when `aws` + `jq` are available and the state is reachable — just press
# Enter to accept. Otherwise read them from Google Cloud Console → APIs & Services → Credentials.
#
# Usage: bash scripts/infra/setup-local-secrets.sh

set -euo pipefail

ENV_FILE="$(dirname "$0")/../.env.local"
STATE_BUCKET="${STATE_BUCKET:-sudoku-tf-state}"
STATE_KEY="${STATE_KEY:-sudoku/terraform.tfstate}"

prompt_secret() {
  local var="$1" prompt="$2" default="${3:-}" existing="" value=""
  if grep -q "^${var}=" "${ENV_FILE}" 2>/dev/null; then
    existing=$(grep "^${var}=" "${ENV_FILE}" | cut -d= -f2-)
    read -r -p "${prompt} [blank = keep existing]: " value
    [ -z "${value}" ] && { echo "${existing}"; return; }
  elif [ -n "${default}" ]; then
    read -r -p "${prompt} [blank = use auto-detected]: " value
    [ -z "${value}" ] && { echo "${default}"; return; }
  else
    read -r -p "${prompt}: " value
  fi
  echo "${value}"
}

# Best-effort: pull the Google OAuth client id + secret from the Cognito identity provider stored in
# the Terraform state (plaintext there, masked by `describe-identity-provider`; same technique the
# README uses to recover the smoke-test user). Prints "client_id<TAB>client_secret" or nothing.
derive_google_creds() {
  command -v aws >/dev/null && command -v jq >/dev/null || return 0
  aws s3 cp "s3://${STATE_BUCKET}/${STATE_KEY}" - 2>/dev/null | jq -r '
    [ .resources[]?
      | select(.type=="aws_cognito_identity_provider")
      | .instances[].attributes.provider_details
      | select(.client_id!=null and .client_secret!=null) ]
    | .[0] // {} | [.client_id, .client_secret] | @tsv' 2>/dev/null
}

echo "==> Sudoku infra deploy secrets setup"
echo "    Writing to: ${ENV_FILE}   (the INFRA secrets file, not ui/.env.local)"
echo "    Leave a prompt blank to keep an existing / auto-detected value."
echo ""

G_ID_DEFAULT=""
G_SECRET_DEFAULT=""
IFS=$'\t' read -r G_ID_DEFAULT G_SECRET_DEFAULT < <(derive_google_creds) || true
if [ -n "${G_ID_DEFAULT}" ]; then
  echo "    Auto-detected GOOGLE_CLIENT_ID/SECRET from Terraform state"
  echo "    (s3://${STATE_BUCKET}/${STATE_KEY}). Press Enter at those two prompts to use them."
  echo ""
fi

AMPLIFY_GITHUB_TOKEN=$(prompt_secret AMPLIFY_GITHUB_TOKEN   "AMPLIFY_GITHUB_TOKEN  (GitHub classic token, repo scope)")
GOOGLE_CLIENT_ID=$(prompt_secret     GOOGLE_CLIENT_ID       "GOOGLE_CLIENT_ID      (Google OAuth client ID)"      "${G_ID_DEFAULT}")
GOOGLE_CLIENT_SECRET=$(prompt_secret GOOGLE_CLIENT_SECRET   "GOOGLE_CLIENT_SECRET  (Google OAuth client secret)"  "${G_SECRET_DEFAULT}")
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
