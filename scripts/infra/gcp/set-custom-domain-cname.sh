#!/usr/bin/env bash
# gcp/set-custom-domain-cname.sh — point the GCP frontend custom domain at Firebase Hosting.
#
# Firebase Hosting serves a custom SUBDOMAIN via a single CNAME to the default site. This UPSERTs
# `CNAME <custom_domain> -> <project>.web.app` in the PARENT edoatley.co.uk zone (AWS Route53), which
# is all Firebase needs to verify ownership, route the domain, and issue a managed TLS cert.
#
# No Cloud DNS zone, NS delegation, or A/AAAA/TXT records: GCP Cloud DNS has no apex-alias record
# (unlike Route53 ALIAS), so the AWS-style delegated-subzone approach doesn't apply to a Firebase
# subdomain. This replaces the earlier gcp/delegate-dns.sh + gcp/apply-custom-domain-dns.sh.
#
# Cross-cloud: reads the project (gcloud) and writes the record (aws route53). Needs an authenticated
# aws session for the account that owns edoatley.co.uk, plus PARENT_ZONE_ID.
#
# Usage:
#   PARENT_ZONE_ID=Z... [AWS_PROFILE=...] [PROJECT_ID=<id>] [CUSTOM_DOMAIN=sudoku-gcp.edoatley.co.uk] \
#     scripts/infra/gcp/set-custom-domain-cname.sh
#
# @spec CP-GCP-014

set -euo pipefail

PROJECT_ID="${PROJECT_ID:-$(gcloud config get-value project 2>/dev/null || true)}"
CUSTOM_DOMAIN="${CUSTOM_DOMAIN:-sudoku-gcp.edoatley.co.uk}"
TARGET="${TARGET:-${PROJECT_ID}.web.app}" # default Firebase Hosting site (site_id = project id)
TTL="${TTL:-300}"

[[ -n "${PROJECT_ID}" && "${PROJECT_ID}" != "(unset)" ]] || { echo "ERROR: no GCP project set (PROJECT_ID / gcloud config)." >&2; exit 1; }
[[ -n "${PARENT_ZONE_ID:-}" ]] || { echo "ERROR: PARENT_ZONE_ID (the edoatley.co.uk Route53 hosted-zone id) is required." >&2; exit 1; }
command -v aws >/dev/null || { echo "ERROR: aws CLI not found." >&2; exit 1; }

echo "==> Setting CNAME ${CUSTOM_DOMAIN} -> ${TARGET} in Route53 zone ${PARENT_ZONE_ID}"
BATCH="$(cat <<JSON
{
  "Comment": "GCP Firebase Hosting custom domain (subdomain CNAME method)",
  "Changes": [
    {"Action":"UPSERT","ResourceRecordSet":{"Name":"${CUSTOM_DOMAIN}.","Type":"CNAME","TTL":${TTL},"ResourceRecords":[{"Value":"${TARGET}"}]}}
  ]
}
JSON
)"
aws route53 change-resource-record-sets --hosted-zone-id "${PARENT_ZONE_ID}" \
  --change-batch "${BATCH}" --query 'ChangeInfo.[Id,Status]' --output text

echo "==> Done. Firebase verifies the CNAME and issues the managed TLS cert out of band (minutes)."
echo "    Check:  dig +short ${CUSTOM_DOMAIN}        (expect ${TARGET})"
echo "            curl -sI https://${CUSTOM_DOMAIN}  (HTTP 200 with a valid cert once it lands)"
