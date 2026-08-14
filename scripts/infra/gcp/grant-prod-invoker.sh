#!/usr/bin/env bash
# grant-prod-invoker.sh — grant public invocation on the PROD (default-workspace) Cloud Run
# services.
#
# The app enforces auth in-app (Firebase JWT validation), so allUsers roles/run.invoker only lets
# requests *reach* Cloud Run — it is not "public access" to data. Terraform grants this binding for
# non-default (rcg-*) workspaces (CP-GCP-014), but PROD is deliberately kept out of Terraform (gap
# G1): granting it in TF would let a plan/apply toggle prod's public reachability, and the binding
# must be applied only AFTER the services first exist. This script is that elevated, post-apply step.
#
# Idempotent — add-iam-policy-binding is a no-op if the member already has the role. Run once after
# the first prod `terraform apply` (workspace=default), and again only if the services are recreated.
#
# Needs an authenticated gcloud session with run.admin (or Owner/Editor) on the project.
#
# Usage:
#   [PROJECT_ID=<id>] [REGION=us-central1] scripts/infra/gcp/grant-prod-invoker.sh
#
# @spec CP-GCP-014

set -euo pipefail

PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}"
REGION="${REGION:-us-central1}"

[[ -n "${PROJECT_ID}" && "${PROJECT_ID}" != "(unset)" ]] || {
  echo "ERROR: no GCP project set. Pass PROJECT_ID=<id> or run 'gcloud config set project <id>'." >&2
  exit 1
}
command -v gcloud >/dev/null || { echo "ERROR: gcloud CLI not found." >&2; exit 1; }

# PROD service names carry no workspace suffix (non-default workspaces use sudoku-<workspace> and are
# handled in Terraform). The image-recognition service only exists when it was deployed
# (deploy_image_recognition=true), so a missing service is skipped, not an error.
SERVICES=(sudoku sudoku-image-recognition)

echo "==> Granting allUsers roles/run.invoker on PROD services in ${PROJECT_ID} (${REGION})"

for SVC in "${SERVICES[@]}"; do
  if ! gcloud run services describe "${SVC}" --region="${REGION}" --project="${PROJECT_ID}" \
      --format='value(metadata.name)' >/dev/null 2>&1; then
    echo "    - ${SVC}: not deployed — skipping."
    continue
  fi
  gcloud run services add-iam-policy-binding "${SVC}" \
    --region="${REGION}" --project="${PROJECT_ID}" \
    --member="allUsers" --role="roles/run.invoker" >/dev/null
  echo "    - ${SVC}: allUsers can invoke (auth still enforced in-app)."
done

echo "==> Done. Prod services are reachable; the app enforces auth via Firebase JWT validation."
