#!/usr/bin/env bash
# gcp/delegate-dns.sh — delegate the GCP frontend subdomain from the edoatley.co.uk parent zone
# (AWS Route53) to the GCP Cloud DNS zone's nameservers.
#
# The parent zone edoatley.co.uk lives in AWS Route53; the GCP frontend's subdomain
# (sudoku-gcp.edoatley.co.uk) is served by a Cloud DNS managed zone that Terraform creates in the
# default workspace when enable_custom_domain=true. This one-time, cross-cloud step points the parent
# at the GCP zone by reading the Cloud DNS nameservers (gcloud) and UPSERTing an NS record in Route53
# (via the existing shared/delegate-dns.sh). Analogous to bedrock-cross-cloud.sh spanning both clouds.
#
# Run ONCE, AFTER the prod `terraform apply` (workspace=default, enable_custom_domain=true) has
# created the Cloud DNS zone. Idempotent (delegate-dns.sh UPSERTs). The Firebase-required A/AAAA/TXT
# records go IN the Cloud DNS zone and are managed by Terraform, not here.
#
# Needs BOTH an authenticated gcloud (to read the zone) and aws (to write the Route53 NS record)
# session, plus PARENT_ZONE_ID (the edoatley.co.uk Route53 hosted-zone id — env or scripts/.env.local).
#
# Usage:
#   [PROJECT_ID=<id>] [ZONE_NAME=sudoku-gcp] [SUBDOMAIN=sudoku-gcp.edoatley.co.uk] \
#   [PARENT_ZONE_ID=Z...] scripts/infra/gcp/delegate-dns.sh
#
# @spec CP-GCP-014

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}"
ZONE_NAME="${ZONE_NAME:-sudoku-gcp}"                        # Cloud DNS managed-zone name (infra/gcp/dns.tf)
SUBDOMAIN="${SUBDOMAIN:-sudoku-gcp.edoatley.co.uk}"         # matches infra/gcp var custom_domain

[[ -n "${PROJECT_ID}" && "${PROJECT_ID}" != "(unset)" ]] || {
  echo "ERROR: no GCP project set. Pass PROJECT_ID=<id> or run 'gcloud config set project <id>'." >&2
  exit 1
}
command -v gcloud >/dev/null || { echo "ERROR: gcloud CLI not found." >&2; exit 1; }

echo "==> Reading Cloud DNS nameservers for zone '${ZONE_NAME}' (${SUBDOMAIN})"
NS_RAW="$(gcloud dns managed-zones describe "${ZONE_NAME}" --project="${PROJECT_ID}" \
  --format='value(nameServers)' 2>/dev/null || true)"
if [[ -z "${NS_RAW}" ]]; then
  echo "ERROR: Cloud DNS zone '${ZONE_NAME}' not found in ${PROJECT_ID}." >&2
  echo "       Apply prod first: workspace=default with -var enable_custom_domain=true." >&2
  exit 1
fi

# gcloud joins list fields with ';'. Cloud DNS returns exactly four nameservers.
IFS=';' read -r -a NS <<<"${NS_RAW}"
NS=("${NS[@]// /}")                                         # strip any stray spaces
if [[ "${#NS[@]}" -ne 4 ]]; then
  echo "ERROR: expected 4 nameservers, got ${#NS[@]}: ${NS_RAW}" >&2
  exit 1
fi

echo "    ${NS[0]}  ${NS[1]}  ${NS[2]}  ${NS[3]}"
echo "==> Delegating ${SUBDOMAIN} in the edoatley.co.uk Route53 parent zone"
# Reuse the generic Route53 NS-delegation tool (reads PARENT_ZONE_ID + AWS creds from env/.env.local).
exec "${HERE}/../shared/delegate-dns.sh" --subdomain "${SUBDOMAIN}" "${NS[0]}" "${NS[1]}" "${NS[2]}" "${NS[3]}"
