#!/usr/bin/env bash
# Cross-cloud Bedrock credential bootstrap — run once (per project) with BOTH an authenticated
# `aws` (IAM-capable) and `gcloud` (Owner/Editor) session, before deploying the AI coach or the
# image-recognition service on GCP with `enable_coach = true`.
#
# It spans both clouds, so it is deliberately its own script rather than folded into
# gcp/bootstrap.sh (GCP-only) or aws/bootstrap.sh (AWS-only):
#   AWS  — create a least-privilege IAM user (bedrock:InvokeModel only) + an access key.
#   GCP  — store that key as two Secret Manager secrets and grant the Cloud Run runtime service
#          accounts (and the CI deploy SA) read access at the secret level.
#
# Terraform then only *mounts* those secrets as AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY env vars
# on the backend + image-recognition Cloud Run services (coach.tf / cloud_run.tf / image_recognition.tf);
# it does NOT manage the secret IAM (kept here, mirroring how gcp/bootstrap.sh owns runtime IAM).
#
# Idempotent. Re-run with ROTATE=y to mint a fresh AWS key and add a new secret version.
#
# Security note: this is a long-lived AWS key — the exact thing WIF avoids — and the main reason the
# Vertex AI migration (CP-GCP-090) exists. It is scoped to Bedrock InvokeModel only. Rotate it.
#
# @spec CP-GCP-085

set -euo pipefail

# ── Config ──────────────────────────────────────────────────────────────────────
PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}"
AWS_REGION="${AWS_REGION:-eu-west-2}"
IAM_USER="${IAM_USER:-sudoku-bedrock-cross-cloud}"
POLICY_NAME="SudokuBedrockCrossCloudInvoke"

ACCESS_KEY_SECRET="${ACCESS_KEY_SECRET:-bedrock-aws-access-key-id}"
SECRET_KEY_SECRET="${SECRET_KEY_SECRET:-bedrock-aws-secret-access-key}"

# Runtime SAs that read the secrets at container start, plus the CI deploy SA (harmless read; avoids
# any deploy-time secret validation surprise). Names match gcp/bootstrap.sh.
SECRET_READERS=(
  "sudoku-run"
  "sudoku-image-recognition-run"
  "sudoku-github-deploy"
)

# Bedrock inference profiles the services invoke — single source of truth for the IAM policy
# (mirrors local.bedrock_models in infra/aws/main.tf and infra/gcp/main.tf).
BEDROCK_MODELS=(
  "eu.anthropic.claude-haiku-4-5-20251001-v1:0"
)

if [[ -z "${PROJECT_ID}" || "${PROJECT_ID}" == "(unset)" ]]; then
  echo "ERROR: no GCP project set. Pass PROJECT_ID=<id> or run 'gcloud config set project <id>'." >&2
  exit 1
fi

command -v aws >/dev/null || { echo "ERROR: aws CLI not found." >&2; exit 1; }
command -v gcloud >/dev/null || { echo "ERROR: gcloud CLI not found." >&2; exit 1; }

AWS_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"

echo "==> Cross-cloud Bedrock credential bootstrap"
echo "    GCP project:  ${PROJECT_ID}"
echo "    AWS account:  ${AWS_ACCOUNT_ID}  (region ${AWS_REGION})"
echo "    IAM user:     ${IAM_USER}"
echo "    Secrets:      ${ACCESS_KEY_SECRET}, ${SECRET_KEY_SECRET}"
echo ""

# ── 1. AWS IAM user + Bedrock-only policy ───────────────────────────────────────
echo "==> [1/4] AWS IAM user + Bedrock InvokeModel policy"

if aws iam get-user --user-name "${IAM_USER}" >/dev/null 2>&1; then
  echo "    User ${IAM_USER} already exists — skipping creation."
else
  aws iam create-user --user-name "${IAM_USER}" >/dev/null
  echo "    Created user ${IAM_USER}."
fi

# Build the Resource list: inference-profile ARNs (what the code calls) + foundation-model ARNs
# (required by Bedrock when the profile routes to a regional endpoint). Strips the eu./us./ap. prefix.
resources=""
for model in "${BEDROCK_MODELS[@]}"; do
  fm="$(printf '%s' "${model}" | sed -E 's/^(eu|us|ap)\.//')"
  resources+="\"arn:aws:bedrock:*:*:inference-profile/${model}\","
  resources+="\"arn:aws:bedrock:*::foundation-model/${fm}\","
done
resources="${resources%,}" # trim trailing comma

POLICY_DOC="$(cat <<JSON
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Action": ["bedrock:InvokeModel"], "Resource": [${resources}] }
  ]
}
JSON
)"

aws iam put-user-policy \
  --user-name "${IAM_USER}" \
  --policy-name "${POLICY_NAME}" \
  --policy-document "${POLICY_DOC}"
echo "    Attached inline policy ${POLICY_NAME} (bedrock:InvokeModel, scoped to the model list)."

# ── 2. AWS access key ───────────────────────────────────────────────────────────
# The secret access key is only retrievable at creation, so we mint one when the GCP secrets are
# missing or when ROTATE=y. On rotate, delete existing keys first (AWS caps a user at 2 keys).
echo "==> [2/4] AWS access key"

secrets_exist=true
gcloud secrets describe "${ACCESS_KEY_SECRET}" --project "${PROJECT_ID}" >/dev/null 2>&1 || secrets_exist=false
gcloud secrets describe "${SECRET_KEY_SECRET}" --project "${PROJECT_ID}" >/dev/null 2>&1 || secrets_exist=false

NEW_KEY_ID=""
NEW_KEY_SECRET=""
if [[ "${secrets_exist}" == "true" && "${ROTATE:-}" != "y" ]]; then
  echo "    Secrets already present and ROTATE!=y — leaving the existing AWS key in place."
else
  if [[ "${ROTATE:-}" == "y" ]]; then
    for existing in $(aws iam list-access-keys --user-name "${IAM_USER}" \
        --query 'AccessKeyMetadata[].AccessKeyId' --output text); do
      aws iam delete-access-key --user-name "${IAM_USER}" --access-key-id "${existing}"
      echo "    Deleted old access key ${existing}."
    done
  fi
  read -r NEW_KEY_ID NEW_KEY_SECRET < <(aws iam create-access-key --user-name "${IAM_USER}" \
    --query 'AccessKey.[AccessKeyId,SecretAccessKey]' --output text)
  echo "    Created access key ${NEW_KEY_ID}."
fi

# ── 3. GCP Secret Manager secrets ───────────────────────────────────────────────
echo "==> [3/4] GCP Secret Manager secrets"

put_secret() {
  local name="$1" value="$2"
  if gcloud secrets describe "${name}" --project "${PROJECT_ID}" >/dev/null 2>&1; then
    if [[ -n "${value}" ]]; then
      printf '%s' "${value}" | gcloud secrets versions add "${name}" --data-file=- --project "${PROJECT_ID}" >/dev/null
      echo "    Added new version to ${name}."
    else
      echo "    ${name} exists — no fresh value, leaving as-is."
    fi
  else
    printf '%s' "${value}" | gcloud secrets create "${name}" \
      --data-file=- --replication-policy=automatic --project "${PROJECT_ID}" >/dev/null
    echo "    Created ${name}."
  fi
}

put_secret "${ACCESS_KEY_SECRET}" "${NEW_KEY_ID}"
put_secret "${SECRET_KEY_SECRET}" "${NEW_KEY_SECRET}"

# ── 4. Grant runtime + deploy SAs read access (secret level) ─────────────────────
echo "==> [4/4] Secret Manager secretAccessor grants"

for SA_NAME in "${SECRET_READERS[@]}"; do
  SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
  for SECRET in "${ACCESS_KEY_SECRET}" "${SECRET_KEY_SECRET}"; do
    gcloud secrets add-iam-policy-binding "${SECRET}" \
      --project "${PROJECT_ID}" \
      --member="serviceAccount:${SA_EMAIL}" \
      --role="roles/secretmanager.secretAccessor" >/dev/null
  done
  echo "    Bound secretAccessor on both secrets to ${SA_NAME}."
done

echo ""
echo "========================================================================"
echo "  Cross-cloud Bedrock credential bootstrap complete."
echo "========================================================================"
echo "  Deploy with -var enable_coach=true (rcg-* pushes set this automatically)."
echo "  Rotate later with:  ROTATE=y PROJECT_ID=${PROJECT_ID} $0"
echo "========================================================================"
