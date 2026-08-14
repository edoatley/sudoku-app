#!/usr/bin/env bash
# Identity Platform bootstrap (the §4 setup) — configures Google + Email/Password sign-in for the
# project via the Identity Toolkit Admin API, so end users can log in through the frontend.
#
# Run once per project with an authenticated gcloud (Owner/Editor) session. Nothing needs to be
# passed on the CLI: PROJECT_ID comes from the active gcloud config, and the reused Google OAuth
# client credentials (GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET — the same ones used for AWS Cognito
# federation) are loaded from scripts/.env.local (written by setup-local-secrets.sh, the same file
# deploy-local.sh sources). Any value can still be overridden via the environment.
#
#   scripts/infra/gcp/identity-platform-bootstrap.sh
#
# It is idempotent. It automates everything the Admin API exposes; TWO one-time steps have no
# reliable API, so the script GUIDES you through them interactively rather than just failing:
#   (a) the initial "Enable Identity Platform" click (a project entitlement) — if the config is
#       missing the script walks you through the click and waits, re-polling the API until it
#       appears, then continues (non-interactive shells instruct-and-exit instead);
#   (b) adding the Firebase auth handler to the Google OAuth client's Authorized redirect URIs
#       (that lives on the OAuth client under APIs & Services → Credentials, not Identity Platform)
#       — the script prints the exact URI/location and asks you to confirm.
#
# @spec CP-GCP-030, CP-GCP-031, UM-GCP-002

set -euo pipefail

# Load secrets from scripts/.env.local (the file setup-local-secrets.sh writes and deploy-local.sh
# sources) so they need not be passed on the CLI. Values already in the environment take precedence.
ENV_FILE="$(dirname "$0")/../../.env.local"
if [[ -f "${ENV_FILE}" ]]; then
  while IFS='=' read -r _k _v; do
    [[ "${_k}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue # skip blanks/comments
    [[ -n "${!_k:-}" ]] && continue                       # don't clobber an env override
    export "${_k}=${_v}"
  done <"${ENV_FILE}"
fi

PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}"
GOOGLE_CLIENT_ID="${GOOGLE_CLIENT_ID:-}"
GOOGLE_CLIENT_SECRET="${GOOGLE_CLIENT_SECRET:-}"
# Prod custom domain (Firebase Hosting): Google sign-in on the hosted host requires it in the
# authorized-domains allow-list. Harmless to add before the domain is live (it is just an allow-list
# entry). Set CUSTOM_DOMAIN="" to skip. Mirrors infra/gcp var custom_domain.
CUSTOM_DOMAIN="${CUSTOM_DOMAIN:-sudoku-gcp.edoatley.co.uk}"

API="https://identitytoolkit.googleapis.com/admin/v2/projects/${PROJECT_ID}"

if [[ -z "${PROJECT_ID}" || "${PROJECT_ID}" == "(unset)" ]]; then
  echo "ERROR: no GCP project set. Pass PROJECT_ID=<id> or run 'gcloud config set project <id>'." >&2
  exit 1
fi
if [[ -z "${GOOGLE_CLIENT_ID}" || -z "${GOOGLE_CLIENT_SECRET}" ]]; then
  echo "ERROR: GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET not found in the environment or ${ENV_FILE}." >&2
  echo "       Populate them once with:  scripts/infra/shared/setup-local-secrets.sh" >&2
  exit 1
fi
command -v gcloud >/dev/null || { echo "ERROR: gcloud CLI not found." >&2; exit 1; }
command -v python3 >/dev/null || { echo "ERROR: python3 not found." >&2; exit 1; }

TOKEN="$(gcloud auth print-access-token)"
# X-Goog-User-Project attributes the call's quota/billing to our project. identitytoolkit requires
# it when authenticating with user (gcloud) credentials — without it the API 403s with
# "requires a quota project" against gcloud's shared default project. Needs serviceusage.services.use
# on the project (Owner/Editor have it).
AUTH=(
  -H "Authorization: Bearer ${TOKEN}"
  -H "Content-Type: application/json"
  -H "X-Goog-User-Project: ${PROJECT_ID}"
)

echo "==> Identity Platform bootstrap"
echo "    Project: ${PROJECT_ID}"
echo ""

# ── 1. API enablement ───────────────────────────────────────────────────────────
echo "==> [1/4] Ensuring identitytoolkit API is enabled"
gcloud services enable identitytoolkit.googleapis.com --project "${PROJECT_ID}" >/dev/null
echo "    Enabled."

# ── 2. Confirm Identity Platform is initialised ─────────────────────────────────
# The "Enable Identity Platform" entitlement has no reliable API. If the config doesn't exist yet,
# guide the operator through the one-time click and wait — re-polling the API — so the rest of the
# script continues in the same run. Non-interactive shells (no TTY) fall back to instruct-and-exit.
echo "==> [2/4] Checking Identity Platform is enabled"
ENABLE_URL="https://console.cloud.google.com/customer-identity/providers?project=${PROJECT_ID}"
config_missing() { printf '%s' "$1" | grep -q "CONFIGURATION_NOT_FOUND"; }

CONFIG="$(curl -s "${AUTH[@]}" "${API}/config")"
if config_missing "${CONFIG}"; then
  cat <<EOF

    Identity Platform is not enabled for this project yet — a one-time, project-level
    click with no API. Do it now:
      1. Open:  ${ENABLE_URL}
      2. Click  "Enable Identity Platform".
EOF
  if [[ ! -t 0 ]]; then
    echo "    (non-interactive shell — enable it, then re-run this script.)" >&2
    exit 1
  fi
  while true; do
    read -r -p "    Press Enter once you've clicked Enable (or 'q' to quit): " ans
    [[ "${ans}" == "q" ]] && { echo "    Aborted — re-run after enabling." >&2; exit 1; }
    CONFIG="$(curl -s "${AUTH[@]}" "${API}/config")"
    if ! config_missing "${CONFIG}"; then
      echo "    Detected — Identity Platform is now enabled."
      break
    fi
    echo "    Not detected yet (can take a few seconds to propagate). Checking again..."
  done
elif printf '%s' "${CONFIG}" | grep -q '"error"'; then
  echo "ERROR reading Identity Platform config:" >&2
  printf '%s\n' "${CONFIG}" >&2
  exit 1
else
  echo "    Already enabled."
fi

# ── 3. Sign-in config: Email/Password on, merge authorized domains ──────────────
# Email/Password exists only for the CI smoke-test user (§5); the app UI offers Google only. The
# authorized-domains list is merged (never shrunk) and includes localhost + the project's Hosting
# domains (*.web.app / *.firebaseapp.com) and the prod custom domain so local, *.web.app, and the
# hosted DNS UIs can all complete sign-in.
echo "==> [3/4] Sign-in config (Email/Password + authorized domains)"
BODY="$(python3 - "${PROJECT_ID}" "${CONFIG}" "${CUSTOM_DOMAIN}" <<'PY'
import json, sys
project = sys.argv[1]
cfg = json.loads(sys.argv[2])
custom_domain = sys.argv[3] if len(sys.argv) > 3 else ""
domains = set(cfg.get("authorizedDomains") or [])
domains.update(["localhost", f"{project}.firebaseapp.com", f"{project}.web.app"])
if custom_domain:
    domains.add(custom_domain)
out = {
    "signIn": {"email": {"enabled": True, "passwordRequired": True}},
    "authorizedDomains": sorted(domains),
}
print(json.dumps(out))
PY
)"
RESP="$(curl -s -X PATCH "${AUTH[@]}" \
  "${API}/config?updateMask=signIn.email.enabled,signIn.email.passwordRequired,authorizedDomains" \
  -d "${BODY}")"
printf '%s' "${RESP}" | grep -q '"error"' && { echo "ERROR updating config:" >&2; printf '%s\n' "${RESP}" >&2; exit 1; }
echo "    Email/Password enabled; authorized domains merged${CUSTOM_DOMAIN:+ (incl. ${CUSTOM_DOMAIN})}."

# ── 4. Google IdP provider ──────────────────────────────────────────────────────
echo "==> [4/4] Google sign-in provider"
IDP_URL="${API}/defaultSupportedIdpConfigs/google.com"
EXISTING="$(curl -s "${AUTH[@]}" "${IDP_URL}")"
IDP_BODY="$(python3 - "${GOOGLE_CLIENT_ID}" "${GOOGLE_CLIENT_SECRET}" <<'PY'
import json, sys
print(json.dumps({"enabled": True, "clientId": sys.argv[1], "clientSecret": sys.argv[2]}))
PY
)"
if printf '%s' "${EXISTING}" | grep -q "CONFIGURATION_NOT_FOUND\|NOT_FOUND"; then
  RESP="$(curl -s -X POST "${AUTH[@]}" "${API}/defaultSupportedIdpConfigs?idpId=google.com" -d "${IDP_BODY}")"
  ACTION="created"
else
  RESP="$(curl -s -X PATCH "${AUTH[@]}" "${IDP_URL}?updateMask=enabled,clientId,clientSecret" -d "${IDP_BODY}")"
  ACTION="updated"
fi
printf '%s' "${RESP}" | grep -q '"error"' && { echo "ERROR configuring Google IdP:" >&2; printf '%s\n' "${RESP}" >&2; exit 1; }
echo "    Google provider ${ACTION}."

# ── 5. Guide the OAuth redirect URI (manual — no API to read/verify) ────────────
# The redirect URIs live on the Google OAuth client (APIs & Services → Credentials), which has no
# reliable read API, so we can't detect this — we guide the operator and confirm.
HANDLER="https://${PROJECT_ID}.firebaseapp.com/__/auth/handler"
CRED_URL="https://console.cloud.google.com/apis/credentials?project=${PROJECT_ID}"
cat <<EOF

==> [5/5] Google OAuth client redirect URI (manual — can't be verified via API)
    The reused OAuth client must allow the Firebase auth handler as a redirect target, or Google
    sign-in fails with redirect_uri_mismatch. Add this Authorized redirect URI:

      ${HANDLER}

    Where: ${CRED_URL}
           → open the OAuth 2.0 Client ID whose id you passed as GOOGLE_CLIENT_ID
           → Authorized redirect URIs → + Add URI → paste the above → Save.
EOF
if [[ -t 0 ]]; then
  while true; do
    read -r -p "    [y] I've added it   [s] skip for now : " ans
    case "${ans}" in
      y | Y) echo "    Good — Google sign-in should complete."; break ;;
      s | S) echo "    Skipped — Google login won't work until it's added."; break ;;
      *) echo "    Add the URI above, then confirm." ;;
    esac
  done
fi

# ── Summary ─────────────────────────────────────────────────────────────────────
cat <<EOF

========================================================================
  Identity Platform configured for ${PROJECT_ID}.
========================================================================
  Recommended hardening (optional, one-time): disable public email/password
  sign-up so the password provider can only sign in pre-provisioned users.
    Console → Identity Platform → Settings → User actions →
    turn OFF "Enable create (sign-up)".

  Then sign in from the UI (http://localhost:5173) with Google.
========================================================================
EOF
