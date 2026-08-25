#!/usr/bin/env bash
# coach-quality-remote-compare.sh — Run the AI coach quality suite RUNS times against an
# already-deployed remote backend (e.g. an rcg-* GCP workspace), then aggregate the resulting
# reports into a single labelled summary.
#
# Unlike coach-quality-repeat.sh (which brings up a local docker-compose stack with real
# Bedrock), this targets a URL you've already deployed: it points COACH_QUALITY_API_URL at it
# and mints a bearer token via scripts/github/gcp-smoke-token.sh so apiClient.js's requests
# pass the backend's real Identity Platform JWT validation — the local variant relies on
# DevUserFilter, which isn't active on a %gcp/%prod deployment.
#
# Use this to compare coach_ai_provider values (bedrock cross-cloud vs vertex) on the same
# deployed workspace: run once per provider, redeploying between runs with
#   gh workflow run deploy-gcp.yml -f workspace=<ws> -f deploy_cloud_run=true \
#     -f enable_coach=true -f coach_ai_provider=<bedrock|vertex> -f run_smoke=false
#
# Usage:
#   COACH_QUALITY_API_URL=https://sudoku-rcg-xyz-abc.a.run.app/api/v1 \
#     LABEL=vertex-gemini-2-0-flash RUNS=5 bash scripts/local/coach-quality-remote-compare.sh
#
# Inputs (env):
#   COACH_QUALITY_API_URL    required — base URL of the deployed backend's REST API
#   LABEL                    required — subdirectory name under reports/ for this run's output
#   RUNS                     default 5 — lower than coach-quality-repeat.sh's default (10):
#                             each run hits real cross-cloud infra, not a local stack
#   SLEEP_BETWEEN_RUNS       default 90 — Bedrock's 10 req/min cross-region quota applies on
#                             the bedrock leg; harmless overhead on the vertex leg
#   SMOKE_TEST_USER_EMAIL / SMOKE_TEST_USER_PASSWORD / VITE_FIREBASE_API_KEY — forwarded to
#                             scripts/github/gcp-smoke-token.sh (see its header for fallbacks)
#
# Log correlation: because COACH_QUALITY_API_URL is remote, the harness reads COACH_REQUEST/
# COACH_RESPONSE lines from GCP Cloud Logging (lib/cloudLoggingClient.js), not docker logs. This
# needs the `gcloud` CLI authenticated with logging.view on the project; the Cloud Run service +
# project are derived from the URL + `gcloud config`, or set COACH_QUALITY_GCP_SERVICE /
# COACH_QUALITY_GCP_PROJECT explicitly.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REPORTS_DIR="${REPO_ROOT}/ui/tests/coach-quality/reports"

: "${COACH_QUALITY_API_URL:?ERROR: COACH_QUALITY_API_URL must be set to the deployed backend REST API URL.}"
: "${LABEL:?ERROR: LABEL must be set (e.g. LABEL=vertex-gemini-2-0-flash) to compare labelled runs.}"

RUNS="${RUNS:-5}"
SLEEP_BETWEEN_RUNS="${SLEEP_BETWEEN_RUNS:-90}"
# Long multi-turn scenarios against a cold deployed backend blow past Playwright's 60s per-test
# default; give them room (overridable). Cloud Logging poll timeout is separate (dockerLogs.js).
export COACH_QUALITY_TEST_TIMEOUT_MS="${COACH_QUALITY_TEST_TIMEOUT_MS:-240000}"
LABEL_DIR="${REPORTS_DIR}/${LABEL}"

if [[ -e "${LABEL_DIR}" ]]; then
  echo "ERROR: ${LABEL_DIR} already exists — pick a different LABEL." >&2
  exit 1
fi

echo "Minting an Identity Platform token for ${COACH_QUALITY_API_URL}..."
export COACH_QUALITY_AUTH_TOKEN
COACH_QUALITY_AUTH_TOKEN="$(bash "${REPO_ROOT}/scripts/github/gcp-smoke-token.sh")"
export COACH_QUALITY_API_URL

# reports/ is gitignored and only created lazily by the Node reporter on its first write, so it
# may not exist yet on a fresh checkout — `find` on a missing directory exits non-zero, which
# under `set -e -o pipefail` would silently kill the whole script right here.
mkdir -p "${REPORTS_DIR}"
BEFORE_LIST="$(mktemp)"
find "${REPORTS_DIR}" -maxdepth 1 -type f -name '*.json' | sort > "${BEFORE_LIST}"

FAILED_RUNS=0
for i in $(seq 1 "${RUNS}"); do
  echo ""
  echo "=== Run ${i}/${RUNS} (${LABEL}) ==="
  if ! (cd "${REPO_ROOT}/ui" && CI=true npm run test:coach-quality); then
    FAILED_RUNS=$((FAILED_RUNS + 1))
    echo "Run ${i} had scenario failures — continuing (still counted in the summary)."
  fi
  if [[ "${i}" -lt "${RUNS}" ]]; then
    echo "Sleeping ${SLEEP_BETWEEN_RUNS}s before the next run..."
    sleep "${SLEEP_BETWEEN_RUNS}"
  fi
done

AFTER_LIST="$(mktemp)"
find "${REPORTS_DIR}" -maxdepth 1 -type f -name '*.json' | sort > "${AFTER_LIST}"
NEW_REPORTS="$(comm -13 "${BEFORE_LIST}" "${AFTER_LIST}")"
rm -f "${BEFORE_LIST}" "${AFTER_LIST}"

mkdir -p "${LABEL_DIR}"
while IFS= read -r report; do
  [[ -z "${report}" ]] && continue
  mv "${report}" "${LABEL_DIR}/"
  md="${report%.json}.md"
  [[ -f "${md}" ]] && mv "${md}" "${LABEL_DIR}/"
done <<< "${NEW_REPORTS}"

echo ""
echo "=== Summary (${RUNS} runs, ${FAILED_RUNS} with scenario failures, label: ${LABEL}) ==="
(cd "${REPO_ROOT}/ui" && node tests/coach-quality/lib/aggregate.js "${LABEL}" | tee "${LABEL_DIR}/summary.txt")

echo ""
echo "Reports and summary saved to: ${LABEL_DIR}"
