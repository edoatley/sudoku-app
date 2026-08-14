#!/usr/bin/env bash
# Provision a password-based Identity Platform smoke-test user for CI/agent auth (CP-GCP-032).
# Idempotent: creates the user if absent, (re)sets its password, and marks emailVerified=true
# (required by AllowedUsersFilter for Firebase tokens). Writes the creds to scripts/.env.local so
# scripts/github/gcp-smoke-token.sh can mint tokens with no interactive sign-in.
#
# Needs an authenticated gcloud (Owner/Editor on the project). The email MUST be on
# app.allowed.emails (the backend authorization gate). Public sign-up may be disabled, so the
# password (re)set and emailVerified are applied via the Identity Toolkit Admin API using the
# gcloud access token.
#
# Usage: [SMOKE_EMAIL=edoatley+sudokuapp@icloud.com] scripts/infra/gcp-create-smoke-user.sh
#
# @spec CP-GCP-032

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}"
SMOKE_EMAIL="${SMOKE_EMAIL:-edoatley+sudokuapp@icloud.com}"
ENV_FILE="${ROOT}/scripts/.env.local"
WEB_API_KEY="${VITE_FIREBASE_API_KEY:-$(grep -E '^VITE_FIREBASE_API_KEY=' "${ROOT}/ui/.env.local" 2>/dev/null | head -1 | cut -d= -f2-)}"

[[ -n "${PROJECT_ID}" && "${PROJECT_ID}" != "(unset)" ]] || { echo "ERROR: no gcloud project set." >&2; exit 1; }
[[ -n "${WEB_API_KEY}" ]] || { echo "ERROR: VITE_FIREBASE_API_KEY not found." >&2; exit 1; }
command -v jq >/dev/null || { echo "ERROR: jq required." >&2; exit 1; }

TOKEN="$(gcloud auth print-access-token)"
ADMIN=(-H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" -H "X-Goog-User-Project: ${PROJECT_ID}")
IDT="https://identitytoolkit.googleapis.com/v1"

PASS="$(openssl rand -hex 24)" # hex: safe in shell/sed/.env

echo "==> Ensuring smoke user ${SMOKE_EMAIL} in project ${PROJECT_ID}"

# Look up an existing account by email (admin).
LOOKUP="$(curl -s "${IDT}/accounts:lookup" "${ADMIN[@]}" -d "{\"email\":[\"${SMOKE_EMAIL}\"]}")"
LOCALID="$(echo "${LOOKUP}" | jq -r '.users[0].localId // empty')"

if [[ -z "${LOCALID}" ]]; then
  echo "    Not found — creating via signUp."
  CREATE="$(curl -s "${IDT}/accounts:signUp?key=${WEB_API_KEY}" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"${SMOKE_EMAIL}\",\"password\":\"${PASS}\",\"returnSecureToken\":true}")"
  LOCALID="$(echo "${CREATE}" | jq -r '.localId // empty')"
  if [[ -z "${LOCALID}" ]]; then
    echo "ERROR: signUp failed:" >&2
    echo "${CREATE}" | jq -c '.error // .' >&2
    echo "  (If ADMIN_ONLY_OPERATION: public sign-up is disabled — create the user in the console" >&2
    echo "   Identity Platform > Users, then re-run to set the password + emailVerified.)" >&2
    exit 1
  fi
  echo "    Created localId=${LOCALID}."
else
  echo "    Found localId=${LOCALID} — resetting password."
fi

# Admin update: (re)set password + mark emailVerified true.
UPD="$(curl -s "${IDT}/accounts:update" "${ADMIN[@]}" \
  -d "{\"localId\":\"${LOCALID}\",\"password\":\"${PASS}\",\"emailVerified\":true}")"
echo "${UPD}" | jq -e '.error' >/dev/null 2>&1 && { echo "ERROR updating account:" >&2; echo "${UPD}" | jq -c '.error' >&2; exit 1; }
echo "    Password set; emailVerified=true."

# Persist creds to scripts/.env.local (create keys or replace existing).
touch "${ENV_FILE}"; chmod 600 "${ENV_FILE}"
set_kv() {
  local k="$1" v="$2"
  if grep -q "^${k}=" "${ENV_FILE}"; then
    # replace whole line without sed-escaping the value (hex value is safe, but be robust)
    grep -v "^${k}=" "${ENV_FILE}" >"${ENV_FILE}.tmp" && printf '%s=%s\n' "${k}" "${v}" >>"${ENV_FILE}.tmp" && mv "${ENV_FILE}.tmp" "${ENV_FILE}"
  else
    printf '%s=%s\n' "${k}" "${v}" >>"${ENV_FILE}"
  fi
}
set_kv SMOKE_TEST_USER_EMAIL "${SMOKE_EMAIL}"
set_kv SMOKE_TEST_USER_PASSWORD "${PASS}"
chmod 600 "${ENV_FILE}"

echo ""
echo "==> Done. Wrote SMOKE_TEST_USER_EMAIL/PASSWORD to ${ENV_FILE}."
echo "    Mint a token with:  scripts/github/gcp-smoke-token.sh"
