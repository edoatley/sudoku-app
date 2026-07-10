#!/usr/bin/env bash
# @spec CP-INFRA-023
# amplify-remove-rc-urls.sh
#
# Removes one or more exact URLs from the shared rc-* Cognito client's callback/logout
# URL lists. Section A of amplify-post-deploy.sh only ever adds a branch's URL on deploy;
# nothing removes it on teardown, so the list grows forever as rc-* branches come and go.
# Called by teardown-rc.yml for the deleted branch's URLs; also runnable manually to purge
# a backlog of already-stale entries (pass one --url per URL to remove).
#
# Idempotent: a URL that is already absent is silently skipped, not an error.
# Refuses to apply a change that would leave zero callback URLs (safety guard against a
# bad input list wiping the client's access entirely).
#
# Usage:
#   bash scripts/github/amplify-remove-rc-urls.sh \
#     --user-pool-id <pool-id> --client-id <client-id> \
#     --url <https://branch.amplifyapp.com/> [--url <https://...> ...]
#
# Example — manual one-off cleanup of known-stale branches:
#   AWS_PROFILE=sandbox bash scripts/github/amplify-remove-rc-urls.sh \
#     --user-pool-id us-east-1_xxxxx --client-id xxxxxxxxxxxx \
#     --url https://rc-league.d1234abcd.amplifyapp.com/ \
#     --url https://rc-user-fixes.d1234abcd.amplifyapp.com/

set -euo pipefail

USER_POOL_ID=""
CLIENT_ID=""
URLS_TO_REMOVE=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --user-pool-id) USER_POOL_ID="$2"; shift 2 ;;
    --client-id)    CLIENT_ID="$2";    shift 2 ;;
    --url)          URLS_TO_REMOVE+=("$2"); shift 2 ;;
    *) echo "ERROR: Unknown argument: $1" >&2; exit 1 ;;
  esac
done

MISSING=()
[[ -z "${USER_POOL_ID}" ]] && MISSING+=(--user-pool-id)
[[ -z "${CLIENT_ID}"    ]] && MISSING+=(--client-id)
if [[ ${#MISSING[@]} -gt 0 ]]; then
  echo "ERROR: Missing required arguments: ${MISSING[*]}" >&2
  exit 1
fi
if [[ ${#URLS_TO_REMOVE[@]} -eq 0 ]]; then
  echo "No --url given — nothing to remove."
  exit 0
fi

EXISTING=$(aws cognito-idp describe-user-pool-client \
  --user-pool-id "${USER_POOL_ID}" \
  --client-id "${CLIENT_ID}" \
  --query 'UserPoolClient.CallbackURLs' \
  --output json)

REMOVE_JSON=$(printf '%s\n' "${URLS_TO_REMOVE[@]}" | jq -R . | jq -s .)

mapfile -t REMAINING < <(echo "${EXISTING}" | jq -r \
  --argjson remove "${REMOVE_JSON}" \
  '[.[] | select(. as $u | $remove | index($u) | not)] | .[]')

if [[ ${#REMAINING[@]} -eq 0 ]]; then
  echo "ERROR: Removing the given URL(s) would leave zero callback URLs — refusing." >&2
  exit 1
fi

REMOVED_COUNT=$(( $(echo "${EXISTING}" | jq 'length') - ${#REMAINING[@]} ))
if [[ "${REMOVED_COUNT}" -eq 0 ]]; then
  echo "None of the given URL(s) were present — nothing to do."
  exit 0
fi

echo "Removing ${REMOVED_COUNT} URL(s):"
for url in "${URLS_TO_REMOVE[@]}"; do echo "    ${url}"; done
echo "Remaining callback/logout URLs:"
for url in "${REMAINING[@]}"; do echo "    ${url}"; done

# Full replace, not a patch — mirrors amplify-post-deploy.sh Section A exactly, so the
# client's other settings are never accidentally reset by an update focused only on URLs.
aws cognito-idp update-user-pool-client \
  --user-pool-id "${USER_POOL_ID}" \
  --client-id "${CLIENT_ID}" \
  --callback-urls "${REMAINING[@]}" \
  --logout-urls "${REMAINING[@]}" \
  --supported-identity-providers "Google" \
  --allowed-o-auth-flows "code" \
  --allowed-o-auth-scopes "openid" "email" "profile" \
  --allowed-o-auth-flows-user-pool-client \
  --explicit-auth-flows ALLOW_REFRESH_TOKEN_AUTH

echo "Cognito callback/logout URLs updated."
