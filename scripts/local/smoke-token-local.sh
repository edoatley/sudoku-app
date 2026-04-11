#!/usr/bin/env bash
# Validate the smoke-test Cognito token acquisition locally.
# Usage: AWS_PROFILE=sandbox ./scripts/local/smoke-token-local.sh <user-pool-id> <username> <password>
# Example: AWS_PROFILE=sandbox ./scripts/local/smoke-token-local.sh eu-west-2_71X75OgH8 user@example.com MyP@ss
set -euo pipefail

USER_POOL_ID="${1:?Usage: $0 <user-pool-id> <username> <password>}"
USERNAME="${2:?Usage: $0 <user-pool-id> <username> <password>}"
PASSWORD="${3:?Usage: $0 <user-pool-id> <username> <password>}"
SMOKE_CLIENT_NAME="sudoku-smoke-test-rc"
REGION="${AWS_REGION:-eu-west-2}"

echo "Looking up client '${SMOKE_CLIENT_NAME}' in pool ${USER_POOL_ID}..."
CLIENT_ID=$(aws cognito-idp list-user-pool-clients \
  --user-pool-id "${USER_POOL_ID}" \
  --query "UserPoolClients[?ClientName=='${SMOKE_CLIENT_NAME}'].ClientId | [0]" \
  --output text --region "${REGION}")

if [[ -z "${CLIENT_ID}" || "${CLIENT_ID}" == "None" ]]; then
  echo "ERROR: Could not find client '${SMOKE_CLIENT_NAME}' in pool ${USER_POOL_ID}" >&2
  exit 1
fi
echo "CLIENT_ID=${CLIENT_ID}"

echo "Fetching client secret..."
CLIENT_SECRET=$(aws cognito-idp describe-user-pool-client \
  --user-pool-id "${USER_POOL_ID}" \
  --client-id "${CLIENT_ID}" \
  --query 'UserPoolClient.ClientSecret' \
  --output text --region "${REGION}")

SECRET_HASH=$(echo -n "${USERNAME}${CLIENT_ID}" \
  | openssl dgst -sha256 -hmac "${CLIENT_SECRET}" -binary \
  | base64)

echo "Authenticating as ${USERNAME}..."
aws cognito-idp initiate-auth \
  --auth-flow USER_PASSWORD_AUTH \
  --client-id "${CLIENT_ID}" \
  --auth-parameters "USERNAME=${USERNAME},PASSWORD=${PASSWORD},SECRET_HASH=${SECRET_HASH}" \
  --output json --region "${REGION}" \
  | jq '{IdToken: .AuthenticationResult.IdToken[:40], AccessToken: .AuthenticationResult.AccessToken[:40]}'

echo "SUCCESS: tokens acquired"
