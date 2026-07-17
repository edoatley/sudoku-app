#!/usr/bin/env bash
# terraform-plan.sh
#
# Runs a parameterised `terraform plan` for either the main (prod) or rc-*
# environment. Optionally includes an rc-specific var-file.
#
# Must be run from the repo root (not from infra/aws/).
# Terraform must already be initialised and the correct workspace selected.
#
# Usage:
#   bash scripts/github/terraform-plan.sh \
#     --is-main      true|false           \
#     --out          <tfplan-file>        \
#     [--rc-vars-file <path-to.tfvars>]
#
# Environment variables (all passed via TF_VAR_* by caller):
#   TF_VAR_github_token, TF_VAR_google_client_id, TF_VAR_google_client_secret,
#   TF_VAR_smoke_test_user_email, TF_VAR_smoke_test_user_password,
#   TF_VAR_image_recognition_image_uri, TF_VAR_git_branch

set -euo pipefail

IS_MAIN=""
PLAN_OUT=""
RC_VARS_FILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --is-main)      IS_MAIN="$2";      shift 2 ;;
    --out)          PLAN_OUT="$2";     shift 2 ;;
    --rc-vars-file) RC_VARS_FILE="$2"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done

for var in IS_MAIN PLAN_OUT; do
  [ -n "${!var}" ] || { echo "Missing required argument: --${var,,}" >&2; exit 1; }
done

ARGS=(
  -out="${PLAN_OUT}"
  -input=false
)

TARGETED_ARGS=(
  -auto-approve
  -input=false
  -target=aws_cloudwatch_log_group.lambda
)

if [ -n "${RC_VARS_FILE}" ] && [ "${IS_MAIN}" = "false" ]; then
  ARGS+=("-var-file=${RC_VARS_FILE}")
  TARGETED_ARGS+=("-var-file=${RC_VARS_FILE}")
fi

cd infra/aws

# Reconcile the standalone log group before the main plan runs. module.lambda's
# use_existing_cloudwatch_log_group=true does a `data` lookup that fails outright on a
# brand-new workspace's first-ever apply, since there is nothing yet to adopt — data
# sources are resolved during plan, before this same run could create the resource.
# Unconditional and idempotent: a no-op once the group already exists (every workspace
# after its first deploy).
terraform apply "${TARGETED_ARGS[@]}"

terraform plan "${ARGS[@]}"
