#!/usr/bin/env bash
# add-admin.sh
#
# Adds a user to the "administrators" Cognito group so they can reach /admin/*
# endpoints (see docs/llds/user-management.md — Admin Authorization).
#
# The pool only knows the user's *federated* username (e.g. google_1234567890),
# not their email — that username only exists after the user has signed in at
# least once via the Google identity provider. Run this AFTER the admin's first
# login. This cannot be done in Terraform (aws_cognito_user_in_group needs the
# username at apply time, before it exists).
#
# Usage:
#   AWS_PROFILE=sandbox bash scripts/infra/add-admin.sh <email> [user-pool-id]
#
# If user-pool-id is omitted, it is read from `terraform output cognito_user_pool_id`
# in infra/ (requires the workspace to already be selected).

set -euo pipefail

EMAIL="${1:-}"
if [[ -z "${EMAIL}" ]]; then
  echo "ERROR: email is required."
  echo "  Usage: AWS_PROFILE=sandbox bash scripts/infra/add-admin.sh <email> [user-pool-id]"
  exit 1
fi

POOL_ID="${2:-}"
if [[ -z "${POOL_ID}" ]]; then
  POOL_ID=$(cd "$(dirname "$0")/../../infra" && terraform output -raw cognito_user_pool_id 2>/dev/null || true)
fi

if [[ -z "${POOL_ID}" ]]; then
  echo "ERROR: user-pool-id not provided and could not be read from Terraform output."
  echo "  Pass it explicitly: AWS_PROFILE=sandbox bash scripts/infra/add-admin.sh <email> <pool-id>"
  exit 1
fi

echo "Looking up federated username for ${EMAIL} in pool ${POOL_ID}..."
USERNAME=$(aws cognito-idp list-users \
  --user-pool-id "${POOL_ID}" \
  --filter "email = \"${EMAIL}\"" \
  --query 'Users[0].Username' --output text)

if [[ -z "${USERNAME}" || "${USERNAME}" == "None" ]]; then
  echo "ERROR: no user found with email ${EMAIL} in pool ${POOL_ID}."
  echo "  The user must sign in at least once before they can be added to the group."
  exit 1
fi

echo "Adding ${USERNAME} to the administrators group..."
aws cognito-idp admin-add-user-to-group \
  --user-pool-id "${POOL_ID}" \
  --username "${USERNAME}" \
  --group-name administrators

echo "Done. ${EMAIL} (${USERNAME}) is now a member of administrators."
