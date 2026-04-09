#!/usr/bin/env bash
# resolve-environment.sh
#
# Derives Terraform workspace, environment name, and is_main flag from the
# current git branch. Writes outputs to $GITHUB_OUTPUT when running in CI,
# or prints them to stdout when run locally.
#
# Usage (CI):
#   bash scripts/github/resolve-environment.sh
#
# Usage (local):
#   bash scripts/github/resolve-environment.sh
#   # Outputs are printed as KEY=VALUE pairs.
#
# Outputs:
#   workspace   — Terraform workspace name ('default' for main, sanitized branch for rc-*)
#   environment — Terraform environment var ('prod' for main, sanitized branch for rc-*)
#   is_main     — 'true' if branch is main, 'false' otherwise
#   branch      — the full branch name

set -euo pipefail

BRANCH="${GITHUB_REF_NAME:-$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")}"

if [ "${BRANCH}" = "main" ]; then
  WORKSPACE="default"
  ENVIRONMENT="prod"
  IS_MAIN="true"
  AMPLIFY_BRANCH="main"
else
  SANITIZED=$(echo "${BRANCH}" | tr '/' '-' | tr '.' '-' | cut -c1-32)
  WORKSPACE="${SANITIZED}"
  ENVIRONMENT="${SANITIZED}"
  IS_MAIN="false"
  AMPLIFY_BRANCH="${SANITIZED}"
fi

emit() {
  local key="$1" value="$2"
  if [ -n "${GITHUB_OUTPUT:-}" ]; then
    echo "${key}=${value}" >> "$GITHUB_OUTPUT"
  else
    echo "${key}=${value}"
  fi
}

emit "workspace"      "${WORKSPACE}"
emit "environment"    "${ENVIRONMENT}"
emit "is_main"        "${IS_MAIN}"
emit "branch"         "${BRANCH}"
emit "amplify_branch" "${AMPLIFY_BRANCH}"
