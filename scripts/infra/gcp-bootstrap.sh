#!/usr/bin/env bash
# GCP bootstrap — run once with an authenticated gcloud (Owner/Editor) before the
# first Terraform apply of infra/gcp.
#
# Creates ONLY the one-time, non-identity prerequisites:
#   1. GCS Terraform state bucket
#   2. Required GCP API enablement
#   3. Artifact Registry repositories (backend + image-recognition)
#
# It deliberately does NOT create service accounts, IAM bindings, Workload
# Identity Federation, or Identity Platform — those are provisioned by hand from
# docs/runbooks/gcp-manual-setup.md (the deliberate learning surface).
#
# Safe to re-run (idempotent).

set -euo pipefail

REGION="${REGION:-us-central1}"
STATE_BUCKET="${STATE_BUCKET:-sudoku-tf-state-gcp}"
PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}"

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

# ── 1. GCS Terraform state bucket ──────────────────────────────────────────────
echo "==> [1/3] GCS state bucket: gs://${STATE_BUCKET}"

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

# ── 2. Enable required APIs ────────────────────────────────────────────────────
echo "==> [2/3] Enabling required GCP APIs"

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
)

gcloud services enable "${APIS[@]}" --project "${PROJECT_ID}"
echo "    Enabled: ${APIS[*]}"

# ── 3. Artifact Registry repositories ──────────────────────────────────────────
# Two Docker repos, shared across all workspaces. Branch-prefixed tags distinguish
# images (<branch>-<sha>, <branch>-latest), mirroring the AWS ECR convention.
echo "==> [3/3] Artifact Registry repositories"

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
      --description "Sudoku ${REPO} container images" \
      --cleanup-policy-dry-run=false
    gcloud artifacts repositories set-cleanup-policies "${REPO}" \
      --project "${PROJECT_ID}" --location "${REGION}" \
      --policy "${CLEANUP_POLICY_FILE}" --no-dry-run
    echo "    Created ${REPO} (cleanup: keep 10 most recent, delete untagged >30d)."
  fi
done

# ── Summary ─────────────────────────────────────────────────────────────────────
echo ""
echo "========================================================================"
echo "  GCP bootstrap complete."
echo "========================================================================"
echo ""
echo "  Next steps:"
echo "  1. Run the MANUAL identity/network setup — this is the learning surface"
echo "     and is intentionally NOT automated here:"
echo "       docs/runbooks/gcp-manual-setup.md"
echo "     It creates: service accounts, IAM role bindings, Workload Identity"
echo "     Federation (GitHub → GCP), and the Identity Platform + Google IdP config."
echo ""
echo "  2. Add the GitHub Actions secrets produced by that runbook:"
echo "       GCP_PROJECT_ID       = ${PROJECT_ID}"
echo "       GCP_WIF_PROVIDER     = <workload-identity-provider resource name>"
echo "       GCP_DEPLOY_SA_EMAIL  = <deploy service account email>"
echo ""
echo "  3. The GCS backend block is already in infra/gcp/terraform.tf. Run:"
echo "       cd infra/gcp && terraform init"
echo "========================================================================"
