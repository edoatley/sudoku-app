#!/usr/bin/env bash
# terraform-plan.sh
#
# Runs a parameterised `terraform plan` for either the main (prod) or rc-*
# environment. Handles the domain-exclusion flag for two-phase applies and
# optionally includes an rc-specific var-file.
#
# Must be run from the repo root (not from infra/).
# Terraform must already be initialised and the correct workspace selected.
#
# Usage:
#   bash scripts/github/terraform-plan.sh \
#     --environment  prod|<rc-workspace>  \
#     --is-main      true|false           \
#     --phase        1|2                  \
#     --out          <tfplan-file>        \
#     [--rc-vars-file <path-to.tfvars>]   \
#     [--image-uri    <ecr-image-uri>]
#
# Environment variables (all passed via TF_VAR_* by caller):
#   TF_VAR_github_token, TF_VAR_google_client_id, TF_VAR_google_client_secret,
#   TF_VAR_smoke_test_user_email, TF_VAR_smoke_test_user_password,
#   TF_VAR_image_recognition_image_uri
#
# Phase 1: excludes domain association resource (slow ACM cert)
# Phase 2: full plan including domain association

set -euo pipefail

ENVIRONMENT=""
IS_MAIN=""
PHASE=""
PLAN_OUT=""
RC_VARS_FILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --environment)  ENVIRONMENT="$2";  shift 2 ;;
    --is-main)      IS_MAIN="$2";      shift 2 ;;
    --phase)        PHASE="$2";        shift 2 ;;
    --out)          PLAN_OUT="$2";     shift 2 ;;
    --rc-vars-file) RC_VARS_FILE="$2"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 1 ;;
  esac
done

for var in ENVIRONMENT IS_MAIN PHASE PLAN_OUT; do
  [ -n "${!var}" ] || { echo "Missing required argument: --${var,,}" >&2; exit 1; }
done

# Build argument array to avoid word-splitting on var values
ARGS=(
  -out="${PLAN_OUT}"
  -input=false
  -var "environment=${ENVIRONMENT}"
)

# Phase 1: exclude domain association to avoid stale plan on first apply
if [ "${PHASE}" = "1" ]; then
  if [ "${IS_MAIN}" = "true" ]; then
    ARGS+=("-var=exclude_amplify_domain=true")
  else
    ARGS+=("-var=exclude_amplify_beta_domain=true")
  fi
fi

# RC-only var file
if [ -n "${RC_VARS_FILE}" ] && [ "${IS_MAIN}" = "false" ]; then
  ARGS+=("-var-file=${RC_VARS_FILE}")
fi

cd infra
terraform plan "${ARGS[@]}"
