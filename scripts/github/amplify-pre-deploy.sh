#!/usr/bin/env bash
# amplify-pre-deploy.sh
#
# Pre-Terraform deployment helper for RC branches — releases the shared
# sudoku-beta.edoatley.co.uk domain from whichever Amplify app currently
# holds it, so the current workspace can claim it in the single terraform apply.
#
# Three scenarios handled:
#   1. Domain held by a different RC app  → delete the association
#   2. Domain held by the current app     → no action (Terraform state tracks it)
#   3. Domain not associated with any app → no action
#
# Must be run after `terraform init` and workspace select (needs CURRENT_APP_ID
# from terraform output if available, otherwise scans all apps).
#
# Required env: AWS credentials (AWS_ACCESS_KEY_ID / AWS_SESSION_TOKEN / etc.)

set -euo pipefail

BETA_DOMAIN="sudoku-beta.edoatley.co.uk"

echo "========================================================"
echo "  Pre-deploy: releasing beta domain from previous RC app"
echo "  Domain: ${BETA_DOMAIN}"
echo "========================================================"

ALL_APPS=$(aws amplify list-apps --query "apps[].appId" --output text)

OWNER_APP_ID=""
for APP in ${ALL_APPS}; do
  ASSOC=$(aws amplify list-domain-associations \
    --app-id "${APP}" \
    --query "domainAssociations[?domainName=='${BETA_DOMAIN}'].domainName" \
    --output text 2>/dev/null || true)
  if [[ -n "${ASSOC}" && "${ASSOC}" != "None" ]]; then
    OWNER_APP_ID="${APP}"
    break
  fi
done

if [[ -z "${OWNER_APP_ID}" ]]; then
  echo "  Beta domain not currently associated with any app — nothing to do."
else
  # Check if the current workspace's app already holds it (re-deploy case).
  # We detect this by checking if there's a terraform state entry for the association.
  CURRENT_APP_ID=$(cd infra/aws && terraform output -raw amplify_app_id 2>/dev/null || true)

  if [[ -n "${CURRENT_APP_ID}" && "${OWNER_APP_ID}" == "${CURRENT_APP_ID}" ]]; then
    echo "  Beta domain is already on the current app (${CURRENT_APP_ID}) — no action needed."
  else
    echo "  Beta domain is on app ${OWNER_APP_ID}; deleting association so current app can claim it."
    aws amplify delete-domain-association \
      --app-id "${OWNER_APP_ID}" \
      --domain-name "${BETA_DOMAIN}"
    echo "  Association deleted."
  fi
fi

echo "========================================================"
echo "  Pre-deploy complete."
echo "========================================================"
