#!/usr/bin/env bash
# cloud-target.sh — Inspect or flip the active production deploy target (aws | gcp | both).
#
# Usage:
#   bash scripts/infra/shared/cloud-target.sh              # show current cloud target
#   bash scripts/infra/shared/cloud-target.sh <aws|gcp|both>  # set DEPLOY_TARGET on GitHub
#   make cloud-target [TARGET=aws|gcp|both]
#
# Precedence (enforced by scripts/github/select_deploy_target.py in ci-deploy.yml):
#   1. workflow_dispatch input `target` (aws | gcp | both) overrides for a single run.
#   2. Branch `rc-*` always deploys to AWS; branch `rcg-*` always deploys to GCP.
#   3. Branch `main` reads the GitHub repository variable `DEPLOY_TARGET` (aws | gcp | both).
#
# @spec CP-CD-001, CP-CD-002, CP-CD-003, CP-CD-004

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
TARGET="${1:-}"

if [[ -n "${TARGET}" ]]; then
  case "${TARGET}" in
    aws|gcp|both)
      command -v gh >/dev/null || { echo "ERROR: gh CLI required to set DEPLOY_TARGET." >&2; exit 1; }
      gh variable set DEPLOY_TARGET --body "${TARGET}"
      echo "✓ Set GitHub repository variable DEPLOY_TARGET=${TARGET}"
      ;;
    *)
      echo "ERROR: Invalid target '${TARGET}'. Expected one of: aws, gcp, both" >&2
      exit 1
      ;;
  esac
fi

GH_TARGET="(unknown — gh CLI not authenticated)"
if command -v gh >/dev/null 2>&1; then
  VAL="$(gh variable get DEPLOY_TARGET 2>/dev/null || true)"
  if [[ -n "${VAL}" ]]; then
    GH_TARGET="${VAL}"
  else
    GH_TARGET="aws (default — DEPLOY_TARGET variable unset)"
  fi
fi

LOCAL_PROVIDER="(no ui/.env.local)"
if [[ -f "${ROOT}/ui/.env.local" ]]; then
  PROVIDER="$(grep -E '^VITE_AUTH_PROVIDER=' "${ROOT}/ui/.env.local" 2>/dev/null | head -1 | cut -d= -f2- || true)"
  LOCAL_PROVIDER="${PROVIDER:-cognito (default)}"
fi

echo "=== Cloud Target Status ==="
echo "  GitHub DEPLOY_TARGET (main):  ${GH_TARGET}"
echo "  Local UI (ui/.env.local):     ${LOCAL_PROVIDER}"
echo ""
echo "  Production URLs:"
echo "    AWS: https://sudoku.edoatley.co.uk      (infra/aws — Terraform)"
echo "    GCP: https://sudoku.gcp.edoatley.co.uk  (infra/gcp — Pulumi, project sudoku-eo-2026)"
echo ""
echo "  Flip production deploy target:"
echo "    make cloud-target TARGET=aws"
echo "    make cloud-target TARGET=gcp"
echo "    make cloud-target TARGET=both"
echo ""
echo "  One-off deploy without changing DEPLOY_TARGET:"
echo "    gh workflow run ci-deploy.yml -f target=gcp"
echo "    gh workflow run ci-deploy.yml -f target=aws"
echo "    gh workflow run ci-deploy.yml -f target=both"
