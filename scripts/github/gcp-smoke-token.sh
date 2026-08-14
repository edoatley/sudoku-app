#!/usr/bin/env bash
# Mint a Firebase (Identity Platform) ID token for the smoke-test user via the Identity Platform
# REST endpoint — the GCP equivalent of scripts/local/smoke-token-local.sh (Cognito
# USER_PASSWORD_AUTH). Prints the raw ID token to stdout so callers can do:
#   TOKEN=$(scripts/github/gcp-smoke-token.sh); curl -H "Authorization: Bearer $TOKEN" ...
#
# No Google popup, no copy-paste — a deterministic bearer token for driving/debugging a deployed
# GCP env. The token carries firebase.sign_in_provider=password, so UserIdentityResolver maps it to
# userId=firebase:<uid>; the email must be on app.allowed.emails and email_verified=true.
#
# Inputs (env, args, or the .env.local files the other scripts use):
#   VITE_FIREBASE_API_KEY   Firebase Web API key (public) — env or ui/.env.local
#   SMOKE_TEST_USER_EMAIL   env, $1, or scripts/.env.local
#   SMOKE_TEST_USER_PASSWORD env, $2, or scripts/.env.local
#
# Usage: scripts/github/gcp-smoke-token.sh [email] [password]
#
# @spec CP-GCP-032

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

# Load smoke creds from scripts/.env.local if not already in the environment (non-clobbering).
ENV_FILE="${ROOT}/scripts/.env.local"
if [[ -f "${ENV_FILE}" ]]; then
  while IFS='=' read -r _k _v; do
    [[ "${_k}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    [[ -n "${!_k:-}" ]] && continue
    export "${_k}=${_v}"
  done <"${ENV_FILE}"
fi

# Web API key: env, else from ui/.env.local.
WEB_API_KEY="${VITE_FIREBASE_API_KEY:-}"
if [[ -z "${WEB_API_KEY}" && -f "${ROOT}/ui/.env.local" ]]; then
  WEB_API_KEY="$(grep -E '^VITE_FIREBASE_API_KEY=' "${ROOT}/ui/.env.local" | head -1 | cut -d= -f2-)"
fi

SMOKE_EMAIL="${1:-${SMOKE_TEST_USER_EMAIL:-}}"
SMOKE_PASSWORD="${2:-${SMOKE_TEST_USER_PASSWORD:-}}"

[[ -n "${WEB_API_KEY}" ]] || { echo "ERROR: VITE_FIREBASE_API_KEY not found (env or ui/.env.local)." >&2; exit 1; }
[[ -n "${SMOKE_EMAIL}" && -n "${SMOKE_PASSWORD}" ]] || {
  echo "ERROR: smoke email/password not found (args, env, or scripts/.env.local)." >&2
  echo "       Populate them with scripts/infra/shared/setup-local-secrets.sh." >&2
  exit 1
}

RESP="$(curl -s "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${WEB_API_KEY}" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${SMOKE_EMAIL}\",\"password\":\"${SMOKE_PASSWORD}\",\"returnSecureToken\":true}")"

if echo "${RESP}" | jq -e '.error' >/dev/null 2>&1; then
  echo "ERROR: signInWithPassword failed for ${SMOKE_EMAIL}:" >&2
  echo "${RESP}" | jq -c '.error | {code, message}' >&2
  exit 1
fi

echo "${RESP}" | jq -r '.idToken'
