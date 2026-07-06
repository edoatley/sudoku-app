#!/usr/bin/env bash
# Image recognition smoke test — POST a base64-encoded puzzle image to /api/v1/ai/scan.
#
# Usage (CI):   bash scripts/github/image-smoke-test.sh <api_base_url> <fixture_path>
# Usage (local): AWS_PROFILE=sandbox bash scripts/github/image-smoke-test.sh \
#                  https://... image_recognition/tests/fixtures/puzzle_1.jpeg <id_token>
#
# Arguments:
#   $1  API base URL  (trailing slash stripped automatically)
#   $2  Path to the image fixture file
#   $3  (optional) Bearer token — falls back to $SMOKE_ID_TOKEN env var
#
# Exits 0 on success, 1 on failure.
set -euo pipefail

API_BASE="${1:?Usage: $0 <api_base_url> <fixture_path> [id_token]}"
API_BASE="${API_BASE%/}"
FIXTURE="${2:?Usage: $0 <api_base_url> <fixture_path> [id_token]}"
ID_TOKEN="${3:-${SMOKE_ID_TOKEN:-}}"

if [[ -z "${ID_TOKEN}" ]]; then
  echo "ERROR: ID token required — pass as \$3 or set SMOKE_ID_TOKEN" >&2
  exit 1
fi

if [[ ! -f "${FIXTURE}" ]]; then
  echo "ERROR: fixture not found: ${FIXTURE}" >&2
  exit 1
fi

# Encode image and build JSON body — write to file to avoid ARG_MAX limits
# -w 0 (GNU) disables line-wrapping; macOS base64 wraps by default so use tr to strip newlines
base64 < "${FIXTURE}" | tr -d '\n' > /tmp/image_b64.txt
jq -n --rawfile img /tmp/image_b64.txt '{"image": ($img | rtrimstr("\n"))}' > /tmp/import_body.json

# Write auth header to file — JWT tokens are too large for shell arguments
printf 'Authorization: Bearer %s\n' "${ID_TOKEN}" > /tmp/auth-header.txt

STATUS=$(curl -s -o /tmp/import.json -w "%{http_code}" \
  -X POST "${API_BASE}/api/v1/ai/scan" \
  -H "Content-Type: application/json" \
  -H @/tmp/auth-header.txt \
  --data-binary @/tmp/import_body.json)

echo "POST /api/v1/ai/scan → HTTP ${STATUS}"
cat /tmp/import.json

if [[ "${STATUS}" != "200" ]]; then
  echo "❌ Expected 200, got ${STATUS}"
  exit 1
fi

ROWS=$(python3 -c "import json; g=json.load(open('/tmp/import.json')).get('originalGrid',[]); print(len(g))")
if [[ "${ROWS}" != "9" ]]; then
  echo "❌ Expected 9 rows in originalGrid, got ${ROWS}"
  exit 1
fi

echo "✅ Image recognition smoke test passed (${ROWS} rows)"

# Append to GitHub step summary when running in CI
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "### Image Recognition Smoke Test"
    echo ""
    echo "| Endpoint | Result |"
    echo "|----------|--------|"
    echo "| POST /api/v1/ai/scan | ✅ ${STATUS} — ${ROWS} rows |"
  } >> "${GITHUB_STEP_SUMMARY}"
fi
