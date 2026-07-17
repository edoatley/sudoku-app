#!/usr/bin/env bash
# gcp-github-bootstrap.sh — connect GitHub Actions to GCP via Workload Identity
# Federation (WIF) and grant the CI deploy service account the roles it needs to
# run `terraform apply` on infra/gcp.
#
# Run once per project, AFTER scripts/infra/gcp-bootstrap.sh (which creates the
# sudoku-github-deploy service account this script wires up). Idempotent — safe to
# re-run; re-running reconciles the provider config and re-asserts bindings/secrets.
#
# ── WHY WIF instead of a service-account key? ──────────────────────────────────
# GitHub Actions must act as a GCP identity to deploy. The legacy approach exported
# a long-lived JSON key for the deploy SA and stored it as a GitHub secret — a
# standing credential that never expires and is catastrophic if it leaks. Workload
# Identity Federation removes the key entirely: for each run GitHub mints a
# short-lived OIDC token, GCP verifies it against a trust policy, and returns a
# short-lived token that impersonates the deploy SA. Nothing durable at rest. This
# is the GCP counterpart to the AWS "GitHub OIDC provider + assume-role" setup.

set -euo pipefail

PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}"
GITHUB_ORG="${GITHUB_ORG:-edoatley}"
GITHUB_REPO="${GITHUB_REPO:-sudoku-app}"
POOL_ID="${POOL_ID:-github-pool}"
PROVIDER_ID="${PROVIDER_ID:-github-provider}"
STATE_BUCKET="${STATE_BUCKET:-sudoku-tf-state-gcp}"
DEPLOY_SA_NAME="sudoku-github-deploy"
DEPLOY_SA="${DEPLOY_SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"

if [[ -z "${PROJECT_ID}" || "${PROJECT_ID}" == "(unset)" ]]; then
  echo "ERROR: no GCP project set. Pass PROJECT_ID=<id> or run 'gcloud config set project <id>'." >&2
  exit 1
fi

# The deploy SA is created by gcp-bootstrap.sh — fail loudly if it is missing
# rather than silently creating a half-configured identity here.
if ! gcloud iam service-accounts describe "${DEPLOY_SA}" --project "${PROJECT_ID}" >/dev/null 2>&1; then
  echo "ERROR: deploy SA ${DEPLOY_SA} not found. Run scripts/infra/gcp-bootstrap.sh first." >&2
  exit 1
fi

echo "==> Wiring GitHub Actions -> GCP (Workload Identity Federation)"
echo "    Project:  ${PROJECT_ID}"
echo "    Repo:     ${GITHUB_ORG}/${GITHUB_REPO}"
echo "    Deploy SA: ${DEPLOY_SA}"
echo ""

# ── 1. Workload Identity Pool ──────────────────────────────────────────────────
# A pool is a namespace/container for EXTERNAL (non-Google) identities. It is what
# lets GCP recognise principals that live outside Google — here, GitHub Actions
# runs — so they can be referenced in IAM without a key.
echo "==> [1/5] Workload Identity Pool: ${POOL_ID}"
if gcloud iam workload-identity-pools describe "${POOL_ID}" \
    --project "${PROJECT_ID}" --location=global >/dev/null 2>&1; then
  echo "    Already exists — skipping."
else
  gcloud iam workload-identity-pools create "${POOL_ID}" \
    --project "${PROJECT_ID}" --location=global \
    --display-name="GitHub Actions pool"
  echo "    Created."
fi

WIF_POOL_ID="$(gcloud iam workload-identity-pools describe "${POOL_ID}" \
  --project "${PROJECT_ID}" --location=global --format='value(name)')"

# ── 2. OIDC provider ───────────────────────────────────────────────────────────
# The provider tells the pool HOW to trust GitHub:
#   --issuer-uri        GitHub's OIDC token issuer; GCP fetches its public keys to
#                       verify the signature on every incoming token.
#   --attribute-mapping copies claims out of the GitHub token into Google
#                       attributes (google.subject <- sub, attribute.repository <-
#                       repository) so they can be used in IAM conditions/members.
#   --attribute-condition  THE security boundary. Only tokens whose `repository`
#                       claim is exactly edoatley/sudoku-app are accepted. Without
#                       it, ANY GitHub repository (including a fork) could request a
#                       token and impersonate the deploy SA — the classic "confused
#                       deputy". Google requires a condition here for this reason.
echo "==> [2/5] OIDC provider: ${PROVIDER_ID}"
ATTR_MAPPING="google.subject=assertion.sub,attribute.repository=assertion.repository"
ATTR_CONDITION="assertion.repository == '${GITHUB_ORG}/${GITHUB_REPO}'"
if gcloud iam workload-identity-pools providers describe "${PROVIDER_ID}" \
    --project "${PROJECT_ID}" --location=global --workload-identity-pool="${POOL_ID}" >/dev/null 2>&1; then
  echo "    Exists — reconciling mapping/condition."
  gcloud iam workload-identity-pools providers update-oidc "${PROVIDER_ID}" \
    --project "${PROJECT_ID}" --location=global --workload-identity-pool="${POOL_ID}" \
    --attribute-mapping="${ATTR_MAPPING}" \
    --attribute-condition="${ATTR_CONDITION}"
else
  gcloud iam workload-identity-pools providers create-oidc "${PROVIDER_ID}" \
    --project "${PROJECT_ID}" --location=global --workload-identity-pool="${POOL_ID}" \
    --display-name="GitHub OIDC" \
    --issuer-uri="https://token.actions.githubusercontent.com" \
    --attribute-mapping="${ATTR_MAPPING}" \
    --attribute-condition="${ATTR_CONDITION}"
  echo "    Created."
fi

# ── 3. Impersonation binding ───────────────────────────────────────────────────
# Allow identities from THIS repo to impersonate the deploy SA. The principalSet
# member selects "any GitHub run whose repository attribute == our repo".
# roles/iam.workloadIdentityUser is the role that permits the token exchange
# (external OIDC token -> short-lived SA credentials). add-iam-policy-binding is
# idempotent, so re-runs are a no-op.
echo "==> [3/5] Allow the repo to impersonate ${DEPLOY_SA_NAME}"
gcloud iam service-accounts add-iam-policy-binding "${DEPLOY_SA}" \
  --project "${PROJECT_ID}" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/${WIF_POOL_ID}/attribute.repository/${GITHUB_ORG}/${GITHUB_REPO}" \
  >/dev/null
echo "    Bound."

# ── 4. Deploy-SA project roles ─────────────────────────────────────────────────
# The least-privilege set the CI needs to `terraform apply` infra/gcp. Each role
# maps to a resource the Terraform manages:
#   datastore.owner                  create/manage the Firestore database + fields
#   firebase.managementAdmin         create the Firebase project resource + site
#   firebasehosting.admin            manage Firebase Hosting sites / custom domains
#   dns.admin                        create the Cloud DNS managed zone + records
#   serviceusage.serviceUsageConsumer  use project quota / read enabled services
#   run.admin                        create/update Cloud Run (when deploy_cloud_run=true)
#   iam.serviceAccountUser           set the runtime SA on Cloud Run (actAs)
# Widen a specific role only if an apply fails on a missing permission — and record
# why (that reconciliation is the point of keeping this least-privilege).
echo "==> [4/5] Deploy-SA project roles"
DEPLOY_ROLES=(
  roles/datastore.owner
  roles/firebase.managementAdmin
  roles/firebasehosting.admin
  roles/dns.admin
  roles/serviceusage.serviceUsageConsumer
  roles/run.admin
  roles/iam.serviceAccountUser
)
for ROLE in "${DEPLOY_ROLES[@]}"; do
  gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
    --member="serviceAccount:${DEPLOY_SA}" --role="${ROLE}" --condition=None >/dev/null
  echo "    Granted ${ROLE}."
done

# Terraform state lives in the GCS bucket — grant read/write there specifically
# (bucket-scoped, not a project-wide storage role) so the deploy SA can read/lock/
# write state without broad storage access.
gcloud storage buckets add-iam-policy-binding "gs://${STATE_BUCKET}" \
  --member="serviceAccount:${DEPLOY_SA}" --role="roles/storage.objectAdmin" >/dev/null
echo "    Granted roles/storage.objectAdmin on gs://${STATE_BUCKET}."

# ── 5. GitHub Actions secrets ──────────────────────────────────────────────────
# The deploy-gcp workflow reads these three to authenticate (WIF provider + SA) and
# target the project. Set via gh if available; otherwise print them to set by hand.
echo "==> [5/5] GitHub Actions secrets"
PROVIDER_NAME="$(gcloud iam workload-identity-pools providers describe "${PROVIDER_ID}" \
  --project "${PROJECT_ID}" --location=global --workload-identity-pool="${POOL_ID}" \
  --format='value(name)')"

if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
  gh secret set GCP_PROJECT_ID      --repo "${GITHUB_ORG}/${GITHUB_REPO}" --body "${PROJECT_ID}"
  gh secret set GCP_WIF_PROVIDER    --repo "${GITHUB_ORG}/${GITHUB_REPO}" --body "${PROVIDER_NAME}"
  gh secret set GCP_DEPLOY_SA_EMAIL --repo "${GITHUB_ORG}/${GITHUB_REPO}" --body "${DEPLOY_SA}"
  echo "    Set GCP_PROJECT_ID, GCP_WIF_PROVIDER, GCP_DEPLOY_SA_EMAIL via gh."
else
  echo "    gh not available/authenticated — set these secrets manually:"
  echo "      GCP_PROJECT_ID      = ${PROJECT_ID}"
  echo "      GCP_WIF_PROVIDER    = ${PROVIDER_NAME}"
  echo "      GCP_DEPLOY_SA_EMAIL = ${DEPLOY_SA}"
fi

echo ""
echo "========================================================================"
echo "  GitHub -> GCP wiring complete."
echo "========================================================================"
echo "  Trigger the infra apply:"
echo "    gh workflow run \"Deploy GCP (infra)\" --ref <branch>"
echo "========================================================================"
