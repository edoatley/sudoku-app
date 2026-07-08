#!/usr/bin/env bash
# amplify-post-deploy.sh
#
# Post-Terraform deployment helper — runs after `terraform apply` to:
#   A) Update Cognito callback / logout URLs
#   B) Poll for Amplify domain association verification (rc mode only)
#   C) Trigger and wait for an Amplify build (when needed)
#
# CORS origins are now set directly in api_gateway.tf and no longer need
# post-deploy tightening.
#
# Usage:
#   bash scripts/github/amplify-post-deploy.sh \
#     --mode           rc|prod            \
#     --app-id         <AMPLIFY_APP_ID>   \
#     --amplify-url    <https://...>      \
#     --default-url    <https://...>      \
#     --branch         <git-branch-name>  \
#     --user-pool-id   <us-east-1_xxxxx>  \
#     --client-id      <cognito-client-id>\
#     --frontend-changed true|false

set -euo pipefail

# ── Argument parsing ──────────────────────────────────────────────────────────
MODE=""
APP_ID=""
AMPLIFY_URL=""
DEFAULT_URL=""
BRANCH=""
USER_POOL_ID=""
CLIENT_ID=""
FRONTEND_CHANGED="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode)            MODE="$2";             shift 2 ;;
    --api-id)          shift 2 ;;  # retained for backwards-compat; no longer used
    --app-id)          APP_ID="$2";           shift 2 ;;
    --amplify-url)     AMPLIFY_URL="$2";      shift 2 ;;
    --default-url)     DEFAULT_URL="$2";      shift 2 ;;
    --branch)          BRANCH="$2";           shift 2 ;;
    --user-pool-id)    USER_POOL_ID="$2";     shift 2 ;;
    --client-id)       CLIENT_ID="$2";        shift 2 ;;
    --frontend-changed) FRONTEND_CHANGED="$2"; shift 2 ;;
    *) echo "ERROR: Unknown argument: $1" >&2; exit 1 ;;
  esac
done

# ── Validation ────────────────────────────────────────────────────────────────
MISSING=()
[[ -z "${MODE}"          ]] && MISSING+=(--mode)
[[ -z "${APP_ID}"        ]] && MISSING+=(--app-id)
[[ -z "${AMPLIFY_URL}"   ]] && MISSING+=(--amplify-url)
[[ -z "${DEFAULT_URL}"   ]] && MISSING+=(--default-url)
[[ -z "${BRANCH}"        ]] && MISSING+=(--branch)
[[ -z "${USER_POOL_ID}"  ]] && MISSING+=(--user-pool-id)
[[ -z "${CLIENT_ID}"     ]] && MISSING+=(--client-id)

if [[ ${#MISSING[@]} -gt 0 ]]; then
  echo "ERROR: Missing required arguments: ${MISSING[*]}" >&2
  exit 1
fi

if [[ "${MODE}" != "prod" && "${MODE}" != "rc" ]]; then
  echo "ERROR: --mode must be 'prod' or 'rc', got '${MODE}'" >&2
  exit 1
fi

# ── Section A: Update Cognito callback / logout URLs ─────────────────────────
echo "========================================================"
echo "  A) Updating Cognito callback URLs (mode: ${MODE})"
echo "========================================================"

BRANCH_URL="${AMPLIFY_URL}/"
DEFAULT_BRANCH_URL="${DEFAULT_URL}/"

if [[ "${MODE}" == "rc" ]]; then
  # rc-* branches share one Cognito pool — accumulate URLs so deploying one
  # branch does not break other active rc-* branches that are already registered.
  # Also strip any defunct wildcard entries Cognito silently rejects.
  EXISTING=$(aws cognito-idp describe-user-pool-client \
    --user-pool-id "${USER_POOL_ID}" \
    --client-id "${CLIENT_ID}" \
    --query 'UserPoolClient.CallbackURLs' \
    --output json)
  mapfile -t CALLBACK_URLS < <(echo "${EXISTING}" | jq -r \
    --arg url "${BRANCH_URL}" \
    --arg default_url "${DEFAULT_BRANCH_URL}" \
    '[.[] | select(test("^https://[^*]"))] | . + (if any(. == $url) then [] else [$url] end) | . + (if any(. == $default_url) then [] else [$default_url] end) | . + ["https://sudoku-beta.edoatley.co.uk/"] | . + ["http://localhost:5173/"] | unique | .[]')
else
  # prod: set the exact list directly
  CALLBACK_URLS=("${BRANCH_URL}" "${DEFAULT_BRANCH_URL}" "https://sudoku.edoatley.co.uk/" "http://localhost:5173/")
fi

echo "  Callback URLs:"
for url in "${CALLBACK_URLS[@]}"; do echo "    ${url}"; done
echo ""

aws cognito-idp update-user-pool-client \
  --user-pool-id "${USER_POOL_ID}" \
  --client-id "${CLIENT_ID}" \
  --callback-urls "${CALLBACK_URLS[@]}" \
  --logout-urls "${CALLBACK_URLS[@]}" \
  --supported-identity-providers "Google" \
  --allowed-o-auth-flows "code" \
  --allowed-o-auth-scopes "openid" "email" "profile" \
  --allowed-o-auth-flows-user-pool-client \
  --explicit-auth-flows ALLOW_REFRESH_TOKEN_AUTH
echo "Cognito callback URLs updated."

# ── Section B: Poll domain association verification (rc mode only) ────────────
if [[ "${MODE}" == "rc" ]]; then
  echo ""
  echo "========================================================"
  echo "  B) Waiting for domain association verification"
  echo "     Domain: sudoku-beta.edoatley.co.uk"
  echo "     Zone:   sudoku-beta.edoatley.co.uk (sandbox account, default workspace)"
  echo "========================================================"

  DOMAIN_NAME="sudoku-beta.edoatley.co.uk"
  MAX_WAIT_SECONDS=1800   # 30 minutes
  POLL_INTERVAL=30
  ELAPSED=0

  while true; do
    DOMAIN_STATUS=$(aws amplify get-domain-association \
      --app-id "${APP_ID}" \
      --domain-name "${DOMAIN_NAME}" \
      --query 'domainAssociation.domainStatus' \
      --output text 2>&1) || {
      EXIT_CODE=$?
      if echo "${DOMAIN_STATUS}" | grep -q "ResourceNotFoundException\|NotFoundException"; then
        echo "  Domain association not found — skipping wait."
        break
      fi
      echo "  ERROR: aws amplify get-domain-association failed (exit ${EXIT_CODE}):" >&2
      echo "  ${DOMAIN_STATUS}" >&2
      exit 1
    }

    echo "  [${ELAPSED}s] Domain status: ${DOMAIN_STATUS}"

    case "${DOMAIN_STATUS}" in
      AVAILABLE|PENDING_DEPLOYMENT)
        echo "  Domain association verified (${DOMAIN_STATUS})."
        break
        ;;
      PENDING_VERIFICATION|CREATING|REQUESTING_CERTIFICATE)
        if [[ ${ELAPSED} -ge ${MAX_WAIT_SECONDS} ]]; then
          echo ""
          echo "  WARNING: Domain still in '${DOMAIN_STATUS}' after ${MAX_WAIT_SECONDS}s."
          echo "  The app is functional at: ${DEFAULT_URL}"
          echo "  The custom domain ${DOMAIN_NAME} may take a few more minutes."
          echo "  Check status with:"
          echo "    AWS_PROFILE=sandbox aws amplify get-domain-association \\"
          echo "      --app-id ${APP_ID} --domain-name ${DOMAIN_NAME}"
          break
        fi
        sleep "${POLL_INTERVAL}"
        ELAPSED=$((ELAPSED + POLL_INTERVAL))
        ;;
      FAILED)
        echo "  ERROR: Domain association FAILED. Check the Amplify console." >&2
        aws amplify get-domain-association \
          --app-id "${APP_ID}" \
          --domain-name "${DOMAIN_NAME}" \
          --query 'domainAssociation' >&2 || true
        exit 1
        ;;
      *)
        echo "  WARNING: Unexpected domain status '${DOMAIN_STATUS}' — continuing."
        break
        ;;
    esac
  done
fi

# ── Section C: Trigger Amplify build ─────────────────────────────────────────
echo ""
echo "========================================================"
echo "  C) Amplify build (branch: ${BRANCH})"
echo "========================================================"

IS_RC="false"
[[ "${MODE}" == "rc" ]] && IS_RC="true"

if [[ "${IS_RC}" == "true" ]]; then
  PRIOR_BUILDS=$(aws amplify list-jobs \
    --app-id "${APP_ID}" \
    --branch-name "${BRANCH}" \
    --query 'length(jobSummaries)' --output text 2>/dev/null || echo "0")
else
  PRIOR_BUILDS="1"
fi

if [[ "${FRONTEND_CHANGED}" != "true" && "${PRIOR_BUILDS}" != "0" ]]; then
  echo "  No frontend changes and branch already has ${PRIOR_BUILDS} build(s) — skipping."
  echo ""
  echo "Deployment complete."
  exit 0
fi

if [[ "${PRIOR_BUILDS}" == "0" ]]; then
  echo "  First deployment for branch '${BRANCH}' — triggering initial build."
else
  echo "  Frontend changed — triggering build for branch '${BRANCH}'."
fi

# Amplify branch registration can lag behind terraform apply — wait until it is visible
echo "  Waiting for Amplify branch '${BRANCH}' to be available..."
for i in $(seq 1 12); do
  BRANCH_STATUS=$(aws amplify get-branch \
    --app-id "${APP_ID}" \
    --branch-name "${BRANCH}" \
    --query 'branch.branchName' --output text 2>/dev/null || true)
  if [[ "${BRANCH_STATUS}" == "${BRANCH}" ]]; then
    echo "  Branch is available."
    break
  fi
  echo "  Attempt ${i}/12 — branch not yet visible, retrying in 10s..."
  sleep 10
  if [[ "${i}" == "12" ]]; then
    echo "  ERROR: Amplify branch '${BRANCH}' never became available after 2 minutes." >&2
    exit 1
  fi
done

JOB_ID=$(aws amplify start-job \
  --app-id "${APP_ID}" \
  --branch-name "${BRANCH}" \
  --job-type RELEASE \
  --query 'jobSummary.jobId' --output text)
echo "  Amplify job ID: ${JOB_ID}"

for i in $(seq 1 40); do
  STATUS=$(aws amplify get-job \
    --app-id "${APP_ID}" \
    --branch-name "${BRANCH}" \
    --job-id "${JOB_ID}" \
    --query 'job.summary.status' --output text)
  echo "  Attempt ${i}/40 → ${STATUS}"
  case "${STATUS}" in
    SUCCEED)
      echo "  Amplify build succeeded."
      break
      ;;
    FAILED|CANCELLED)
      echo "  ERROR: Amplify build ${STATUS}." >&2
      exit 1
      ;;
  esac
  if [[ "${i}" == "40" ]]; then
    echo "  ERROR: Amplify build timed out after 10 minutes." >&2
    exit 1
  fi
  sleep 15
done

echo ""
echo "========================================================"
echo "  Deployment complete."
echo "  App URL:     ${AMPLIFY_URL}"
echo "  Default URL: ${DEFAULT_URL}"
echo "========================================================"
