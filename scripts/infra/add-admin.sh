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
# Resolves the Cognito user pool via the AWS CLI only (no `terraform output` —
# works even without a local Terraform state/workspace). By default it infers
# the pool name from the current git branch, mirroring resolve-environment.sh:
#   main branch      -> "sudoku"     (owned pool, default workspace)
#   rc-* branch      -> "sudoku-rc"  (shared pool, owned by the rc-shared workspace)
#   other branch     -> "sudoku-<sanitized branch, max 32 chars>" (owned pool)
#
# Usage:
#   AWS_PROFILE=sandbox bash scripts/infra/add-admin.sh <email>
#   AWS_PROFILE=sandbox bash scripts/infra/add-admin.sh <email> --pool-name sudoku-rc
#   AWS_PROFILE=sandbox bash scripts/infra/add-admin.sh <email> --pool-id eu-west-2_AbCdEfGhI

set -euo pipefail

EMAIL="${1:-}"
if [[ -z "${EMAIL}" ]]; then
  echo "ERROR: email is required."
  echo "  Usage: AWS_PROFILE=sandbox bash scripts/infra/add-admin.sh <email> [--pool-name <name> | --pool-id <id>]"
  exit 1
fi
shift

POOL_NAME=""
POOL_ID=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --pool-name) POOL_NAME="$2"; shift 2 ;;
    --pool-id) POOL_ID="$2"; shift 2 ;;
    *) echo "ERROR: unknown argument: $1"; exit 1 ;;
  esac
done

# Derive the default pool name from the current branch (same convention as
# scripts/github/resolve-environment.sh) when neither flag was given.
if [[ -z "${POOL_ID}" && -z "${POOL_NAME}" ]]; then
  BRANCH="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")"
  if [[ "${BRANCH}" == "main" ]]; then
    POOL_NAME="sudoku"
  elif [[ "${BRANCH}" == rc-* ]]; then
    POOL_NAME="sudoku-rc"
  else
    SANITIZED=$(echo "${BRANCH}" | tr '/' '-' | tr '.' '-' | cut -c1-32)
    POOL_NAME="sudoku-${SANITIZED}"
  fi
  echo "No --pool-name/--pool-id given; inferred pool name '${POOL_NAME}' from branch '${BRANCH}'."
fi

if [[ -z "${POOL_ID}" ]]; then
  echo "Looking up user pool '${POOL_NAME}' via AWS CLI..."
  POOL_ID=$(aws cognito-idp list-user-pools --max-results 60 \
    --query "UserPools[?Name=='${POOL_NAME}'].Id | [0]" --output text)

  if [[ -z "${POOL_ID}" || "${POOL_ID}" == "None" ]]; then
    echo "ERROR: no Cognito user pool named '${POOL_NAME}' found."
    echo "  Available pools:"
    aws cognito-idp list-user-pools --max-results 60 --query 'UserPools[].Name' --output text | tr '\t' '\n' | sed 's/^/    /'
    echo "  Pass the correct one explicitly: --pool-name <name> or --pool-id <id>"
    exit 1
  fi
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
