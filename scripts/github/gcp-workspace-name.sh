#!/usr/bin/env bash
# Derive a Terraform workspace name from a branch name for the GCP facet, used by both
# deploy-gcp.yml and teardown-gcp.yml so the two never drift (a mismatch would leave a
# teardown unable to find the deployed workspace).
#
# The Firebase Hosting site_id is "<project_id>-<workspace>" and must be <= 30 chars, so the
# workspace length is capped at 30 - len(project_id) - 1. Sanitises to Firestore/Firebase-safe
# characters (lowercase, [a-z0-9-]) and strips any trailing hyphen. Prints the workspace to stdout.
#
# Usage: PROJECT_ID=<gcp-project-id> gcp-workspace-name.sh <branch>
set -euo pipefail

BRANCH="${1:?usage: PROJECT_ID=<id> gcp-workspace-name.sh <branch>}"
: "${PROJECT_ID:?PROJECT_ID env var required}"

# 30 (Firebase site_id limit) - project_id length - 1 (the hyphen separator).
max=$(( 30 - ${#PROJECT_ID} - 1 ))
if [ "$max" -lt 1 ]; then
  echo "PROJECT_ID '${PROJECT_ID}' too long to derive a Firebase-safe workspace suffix" >&2
  exit 1
fi

sanitised=$(printf '%s' "$BRANCH" \
  | tr '[:upper:]' '[:lower:]' \
  | tr '/.' '--' \
  | tr -cd 'a-z0-9-')

workspace="${sanitised:0:$max}"
workspace="${workspace%-}" # site_id may not end with a hyphen

printf '%s' "$workspace"
