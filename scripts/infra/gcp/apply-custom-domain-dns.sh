#!/usr/bin/env bash
# gcp/apply-custom-domain-dns.sh — write Firebase's required A/AAAA/TXT records INTO the Cloud DNS zone.
#
# The Firebase custom-domain resource computes the records it needs inside the sudoku-gcp Cloud DNS
# zone to prove ownership and route the domain to Hosting. Terraform surfaces them as the output
# `custom_domain_required_dns_records`; this script reads that output and UPSERTs each record set via
# gcloud so the domain verifies + resolves. It is the in-zone counterpart to `gcp/delegate-dns.sh`
# (which does the parent-zone NS delegation in Route53).
#
# Firebase computes the records asynchronously, so the output can be empty right after the first
# apply — this script exits cleanly in that case; just re-run once the output populates
# (`terraform apply`/`refresh` in infra/gcp, then re-run). Idempotent (describe → update, else create).
#
# Needs an authenticated gcloud (dns.admin or Owner/Editor) and terraform initialised in infra/gcp
# (the GCS backend) so `terraform output` can read state.
#
# Usage: [PROJECT_ID=<id>] [ZONE_NAME=sudoku-gcp] [TTL=300] scripts/infra/gcp/apply-custom-domain-dns.sh
#
# @spec CP-GCP-014

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "${HERE}/../../.." && pwd)"
PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}"
ZONE_NAME="${ZONE_NAME:-sudoku-gcp}"
TTL="${TTL:-300}"

[[ -n "${PROJECT_ID}" && "${PROJECT_ID}" != "(unset)" ]] || { echo "ERROR: no GCP project set." >&2; exit 1; }
command -v gcloud >/dev/null || { echo "ERROR: gcloud CLI not found." >&2; exit 1; }
command -v terraform >/dev/null || { echo "ERROR: terraform not found." >&2; exit 1; }
command -v jq >/dev/null || { echo "ERROR: jq required." >&2; exit 1; }

echo "==> Reading required DNS records from terraform output (infra/gcp)"
RECORDS_JSON="$(cd "${ROOT}/infra/gcp" && terraform output -json custom_domain_required_dns_records 2>/dev/null || echo '[]')"
COUNT="$(echo "${RECORDS_JSON}" | jq 'length')"
if [[ "${COUNT}" -eq 0 ]]; then
  echo "    No records yet — Firebase computes them asynchronously."
  echo "    Re-run once 'terraform output custom_domain_required_dns_records' is non-empty"
  echo "    (apply/refresh infra/gcp with enable_custom_domain=true first)."
  exit 0
fi

# Group by (name, type) and build a comma-separated rrdatas list. TXT values must be double-quoted
# for Cloud DNS; A/AAAA/CNAME are passed as-is. jq emits one TSV row per record set.
echo "==> UPSERTing records into Cloud DNS zone '${ZONE_NAME}' (project ${PROJECT_ID})"
echo "${RECORDS_JSON}" | jq -r '
  group_by(.domain_name + "|" + .type)[] | {
    name:    (.[0].domain_name | if endswith(".") then . else . + "." end),
    type:    .[0].type,
    rrdatas: ([.[] | if .type == "TXT" then "\"" + .rdata + "\"" else .rdata end] | join(","))
  } | [.name, .type, .rrdatas] | @tsv
' | while IFS=$'\t' read -r NAME TYPE RRDATAS; do
  echo "    ${TYPE} ${NAME} -> ${RRDATAS}"
  if gcloud dns record-sets describe "${NAME}" --type="${TYPE}" --zone="${ZONE_NAME}" \
      --project="${PROJECT_ID}" >/dev/null 2>&1; then
    gcloud dns record-sets update "${NAME}" --type="${TYPE}" --zone="${ZONE_NAME}" \
      --project="${PROJECT_ID}" --ttl="${TTL}" --rrdatas="${RRDATAS}" >/dev/null
  else
    gcloud dns record-sets create "${NAME}" --type="${TYPE}" --zone="${ZONE_NAME}" \
      --project="${PROJECT_ID}" --ttl="${TTL}" --rrdatas="${RRDATAS}" >/dev/null
  fi
done

echo "==> Done. Firebase will verify and issue the managed TLS cert out of band."
echo "    Check:  dig +short sudoku-gcp.edoatley.co.uk"
echo "            and the Firebase console (custom domain shows 'Connected')."
