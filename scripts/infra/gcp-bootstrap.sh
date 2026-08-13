#!/usr/bin/env bash
# GCP bootstrap — run once with an authenticated gcloud (Owner/Editor) before the
# first Terraform apply of infra/gcp.
#
# Creates the one-time prerequisites:
#   1. Project (created if missing) + billing link
#   2. GCS Terraform state bucket
#   3. Required GCP API enablement
#   4. Artifact Registry repositories (backend + image-recognition)
#   5. Service accounts + Firestore (datastore.user) IAM for the runtime SAs
#   6. Identity Platform sign-in (optional — only when GOOGLE_CLIENT_ID/SECRET are
#      set; delegates to gcp-identity-platform-bootstrap.sh)
#
# It deliberately does NOT create: the deploy SA's broader project roles, public
# Cloud Run invoker, or Workload Identity Federation — those remain hand-run from
# docs/runbooks/gcp-manual-setup.md (the learning surface).
#
# Safe to re-run (idempotent).

set -euo pipefail

REGION="${REGION:-us-central1}"
STATE_BUCKET="${STATE_BUCKET:-sudoku-tf-state-gcp}"
PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}"
PROJECT_NAME="${PROJECT_NAME:-Sudoku}"

# Billing account for the project. If empty and billing is missing, the sole open
# billing account is auto-selected. Linking always requires interactive confirmation
# (unless AUTO_CONFIRM_BILLING=y), and only when billing is not already enabled.
BILLING_ACCOUNT="${BILLING_ACCOUNT:-}"

BACKEND_REPO="sudoku-backend"
IMAGE_RECOGNITION_REPO="sudoku-image-recognition"

if [[ -z "${PROJECT_ID}" || "${PROJECT_ID}" == "(unset)" ]]; then
  echo "ERROR: no GCP project set. Pass PROJECT_ID=<id> or run 'gcloud config set project <id>'." >&2
  exit 1
fi

echo "==> Bootstrapping Sudoku GCP infrastructure prerequisites"
echo "    Project:      ${PROJECT_ID}"
echo "    Region:       ${REGION}"
echo "    State bucket: gs://${STATE_BUCKET}"
echo ""

# ── 1. Project + billing ───────────────────────────────────────────────────────
echo "==> [1/5] Project + billing: ${PROJECT_ID}"

if gcloud projects describe "${PROJECT_ID}" >/dev/null 2>&1; then
  echo "    Project already exists — skipping creation."
else
  echo "    Project not found — creating (name: ${PROJECT_NAME})."
  gcloud projects create "${PROJECT_ID}" --name="${PROJECT_NAME}"
fi

gcloud config set project "${PROJECT_ID}" >/dev/null

# Link billing only when it is not already enabled. An idempotent re-run with
# billing already linked never prompts; confirmation is required only when the
# link is missing.
BILLING_ENABLED="$(gcloud billing projects describe "${PROJECT_ID}" --format='value(billingEnabled)' 2>/dev/null || echo False)"
if [[ "${BILLING_ENABLED}" == "True" ]]; then
  echo "    Billing already linked — skipping."
else
  if [[ -z "${BILLING_ACCOUNT}" ]]; then
    BILLING_ACCOUNT="$(gcloud billing accounts list --filter='open=true' --format='value(name)' 2>/dev/null | head -1)"
  fi
  if [[ -z "${BILLING_ACCOUNT}" ]]; then
    echo "ERROR: billing is not linked and no open billing account was found." >&2
    echo "       Set BILLING_ACCOUNT=<XXXXXX-XXXXXX-XXXXXX> and re-run." >&2
    exit 1
  fi

  echo "    Billing is NOT linked. This will enable spend on the project:"
  echo "        project: ${PROJECT_ID}"
  echo "        billing: ${BILLING_ACCOUNT}"
  if [[ "${AUTO_CONFIRM_BILLING:-}" =~ ^[Yy]$ ]]; then
    REPLY="y"
  elif [[ -t 0 ]]; then
    read -r -p "    Link this billing account? [y/N] " REPLY
  else
    echo "ERROR: billing link needs confirmation but stdin is not a terminal." >&2
    echo "       Re-run interactively, or set AUTO_CONFIRM_BILLING=y." >&2
    exit 1
  fi
  if [[ "${REPLY}" =~ ^[Yy]$ ]]; then
    gcloud billing projects link "${PROJECT_ID}" --billing-account="${BILLING_ACCOUNT}"
    echo "    Linked."
  else
    echo "ERROR: billing link declined — aborting (Cloud Run/Firestore require billing)." >&2
    exit 1
  fi
fi

# ── 2. GCS Terraform state bucket ──────────────────────────────────────────────
echo "==> [2/5] GCS state bucket: gs://${STATE_BUCKET}"

if gcloud storage buckets describe "gs://${STATE_BUCKET}" --project "${PROJECT_ID}" >/dev/null 2>&1; then
  echo "    Already exists — skipping creation."
else
  gcloud storage buckets create "gs://${STATE_BUCKET}" \
    --project "${PROJECT_ID}" \
    --location "${REGION}" \
    --uniform-bucket-level-access \
    --public-access-prevention
  echo "    Created."
fi

# Versioning protects state against accidental corruption/overwrite (parity with
# the AWS S3 backend's versioning).
gcloud storage buckets update "gs://${STATE_BUCKET}" --versioning
echo "    Configured: versioning on, uniform bucket-level access, public access prevented."

# ── 3. Enable required APIs ────────────────────────────────────────────────────
echo "==> [3/5] Enabling required GCP APIs"

APIS=(
  run.googleapis.com                 # Cloud Run
  artifactregistry.googleapis.com    # Artifact Registry (container images)
  firestore.googleapis.com           # Firestore (Native)
  firebase.googleapis.com            # Firebase (Hosting parent API)
  firebasehosting.googleapis.com     # Firebase Hosting
  identitytoolkit.googleapis.com     # Identity Platform
  dns.googleapis.com                 # Cloud DNS
  billingbudgets.googleapis.com      # Cloud Billing Budgets
  pubsub.googleapis.com              # Budget alert topic
  secretmanager.googleapis.com       # Bedrock cross-cloud credential (interim)
  iamcredentials.googleapis.com      # Workload Identity Federation
  sts.googleapis.com                 # Workload Identity Federation
  cloudresourcemanager.googleapis.com
  policytroubleshooter.googleapis.com # IAM policy troubleshooter (diagnostics)
)

gcloud services enable "${APIS[@]}" --project "${PROJECT_ID}"
echo "    Enabled: ${APIS[*]}"

# ── 4. Artifact Registry repositories ──────────────────────────────────────────
# Two Docker repos, shared across all workspaces. Branch-prefixed tags distinguish
# images (<branch>-<sha>, <branch>-latest), mirroring the AWS ECR convention.
echo "==> [4/5] Artifact Registry repositories"

CLEANUP_POLICY_FILE="$(mktemp)"
trap 'rm -f "${CLEANUP_POLICY_FILE}"' EXIT
cat >"${CLEANUP_POLICY_FILE}" <<'JSON'
[
  {
    "name": "keep-10-recent",
    "action": {"type": "Keep"},
    "mostRecentVersions": {"keepCount": 10}
  },
  {
    "name": "delete-older-untagged",
    "action": {"type": "Delete"},
    "condition": {"tagState": "UNTAGGED", "olderThan": "2592000s"}
  }
]
JSON

for REPO in "${BACKEND_REPO}" "${IMAGE_RECOGNITION_REPO}"; do
  if gcloud artifacts repositories describe "${REPO}" \
      --project "${PROJECT_ID}" --location "${REGION}" >/dev/null 2>&1; then
    echo "    Repository ${REPO} already exists — updating cleanup policy."
    gcloud artifacts repositories set-cleanup-policies "${REPO}" \
      --project "${PROJECT_ID}" --location "${REGION}" \
      --policy "${CLEANUP_POLICY_FILE}" --no-dry-run
  else
    gcloud artifacts repositories create "${REPO}" \
      --project "${PROJECT_ID}" --location "${REGION}" \
      --repository-format docker \
      --description "Sudoku ${REPO} container images"
    gcloud artifacts repositories set-cleanup-policies "${REPO}" \
      --project "${PROJECT_ID}" --location "${REGION}" \
      --policy "${CLEANUP_POLICY_FILE}" --no-dry-run
    echo "    Created ${REPO} (cleanup: keep 10 most recent, delete untagged >30d)."
  fi
done

# ── 5. Service accounts + Firestore IAM ────────────────────────────────────────
# The two Cloud Run runtime SAs, the CI deploy SA, and roles/datastore.user on the
# runtime SAs. The deploy SA's broader project roles, public run.invoker, Workload
# Identity Federation, and Identity Platform stay manual — see the runbook.
echo "==> [5/5] Service accounts + Firestore IAM"

SERVICE_ACCOUNTS=(
  "sudoku-run:Sudoku backend Cloud Run runtime"
  "sudoku-image-recognition-run:Sudoku image-recognition Cloud Run runtime"
  "sudoku-github-deploy:Sudoku GitHub Actions deploy"
)
for ENTRY in "${SERVICE_ACCOUNTS[@]}"; do
  SA_NAME="${ENTRY%%:*}"
  SA_DISPLAY="${ENTRY#*:}"
  SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
  if gcloud iam service-accounts describe "${SA_EMAIL}" --project "${PROJECT_ID}" >/dev/null 2>&1; then
    echo "    SA ${SA_NAME} already exists — skipping."
  else
    gcloud iam service-accounts create "${SA_NAME}" \
      --project "${PROJECT_ID}" --display-name="${SA_DISPLAY}"
    echo "    Created SA ${SA_NAME}."
  fi
done

# roles/datastore.user on the runtime SAs (add-iam-policy-binding is idempotent).
for SA_NAME in sudoku-run sudoku-image-recognition-run; do
  gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
    --member="serviceAccount:${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com" \
    --role="roles/datastore.user" --condition=None >/dev/null
  echo "    Bound roles/datastore.user to ${SA_NAME}."
done

# ── 6. Identity Platform (optional; needs the reused Google OAuth client) ────────
# Configures Google + Email/Password sign-in via the dedicated script when the OAuth client
# credentials are supplied. Skipped otherwise so the core bootstrap stays credential-free.
# (Still leaves the one-time "Enable Identity Platform" click + OAuth redirect-URI paste — the
# script prints those.)
echo "==> [6/6] Identity Platform sign-in (optional)"
IDP_SCRIPT="$(dirname "$0")/gcp-identity-platform-bootstrap.sh"
# The identity script self-loads GOOGLE_CLIENT_ID/SECRET from scripts/.env.local; run it when those
# creds are available (in the environment or that file).
if [[ -n "${GOOGLE_CLIENT_ID:-}" && -n "${GOOGLE_CLIENT_SECRET:-}" ]] || [[ -f "$(dirname "$0")/../.env.local" ]]; then
  PROJECT_ID="${PROJECT_ID}" "${IDP_SCRIPT}" ||
    echo "    Identity Platform step reported an issue (see above) — re-run: ${IDP_SCRIPT}"
else
  echo "    Skipped — no OAuth creds. Populate them once with scripts/infra/setup-local-secrets.sh,"
  echo "    then re-run this bootstrap (or run ${IDP_SCRIPT} directly)."
fi

# ── Summary ─────────────────────────────────────────────────────────────────────
echo ""
echo "========================================================================"
echo "  GCP bootstrap complete."
echo "========================================================================"
echo ""
echo "  Next steps:"
echo "  1. Run the remaining MANUAL identity setup (still the learning surface):"
echo "       docs/runbooks/gcp-manual-setup.md"
echo "     Still hand-run: the deploy SA's project roles, public Cloud Run invoker,"
echo "     and Workload Identity Federation (GitHub → GCP)."
echo "     (Service accounts + runtime Firestore IAM come from step [5/6]; Identity"
echo "      Platform sign-in from step [6/6] when the OAuth client creds are supplied —"
echo "      it still needs the one-time 'Enable Identity Platform' click + OAuth redirect URI.)"
echo ""
echo "  2. Add the GitHub Actions secrets produced by that runbook:"
echo "       GCP_PROJECT_ID       = ${PROJECT_ID}"
echo "       GCP_WIF_PROVIDER     = <workload-identity-provider resource name>"
echo "       GCP_DEPLOY_SA_EMAIL  = <deploy service account email>"
echo ""
echo "  3. The GCS backend block is already in infra/gcp/terraform.tf. Run:"
echo "       cd infra/gcp && terraform init"
echo "========================================================================"
