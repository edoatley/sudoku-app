#!/usr/bin/env bash
# delegate-dns.sh
#
# Creates an NS delegation record in the edoatley.co.uk hosted zone
# (default AWS account) pointing to a subdomain zone managed in the
# sandbox account.
#
# Run this ONCE per subdomain after `terraform apply` outputs the nameservers.
#
# Usage:
#   PARENT_ZONE_ID=Z0123456789ABCDEF ./delegate-dns.sh [--subdomain <name>] ns1 ns2 ns3 ns4
#
# Options:
#   --subdomain <name>   Subdomain to delegate (default: sudoku.edoatley.co.uk)
#                        e.g. --subdomain sudoku-beta.edoatley.co.uk
#
# Or pass the zone ID as the first argument:
#   ./delegate-dns.sh Z0123456789ABCDEF [--subdomain <name>] ns1 ns2 ns3 ns4
#
# You can get the nameservers from Terraform (they are the same for all subdomains):
#   cd infra/aws && AWS_PROFILE=sandbox terraform output subdomain_nameservers

set -euo pipefail

SUBDOMAIN="sudoku.edoatley.co.uk"

# ── Load secrets from .env.local if not already in environment ─────────────────
ENV_FILE="$(dirname "$0")/../../.env.local"
if [ -f "${ENV_FILE}" ]; then
  # shellcheck source=/dev/null
  set -o allexport; source "${ENV_FILE}"; set +o allexport
else
  echo "Hint: run 'bash scripts/infra/shared/setup-local-secrets.sh' once to avoid passing secrets on the command line."
fi


# ---- Resolve arguments -------------------------------------------------------
# If the first arg looks like a hosted zone ID (starts with Z and is uppercase
# alphanumeric), treat it as the zone ID; otherwise expect PARENT_ZONE_ID env var.
if [[ $# -ge 1 && "$1" =~ ^Z[A-Z0-9]+$ ]]; then
  PARENT_ZONE_ID="$1"
  shift
fi

# Optional --subdomain flag overrides the default.
if [[ $# -ge 2 && "$1" == "--subdomain" ]]; then
  SUBDOMAIN="$2"
  shift 2
fi

if [[ -z "${PARENT_ZONE_ID:-}" ]]; then
  echo "ERROR: PARENT_ZONE_ID is not set."
  echo "  Set it as an environment variable: PARENT_ZONE_ID=ZXXXXX ./delegate-dns.sh ..."
  echo "  Or pass it as the first argument: ./delegate-dns.sh ZXXXXX ns1 ns2 ns3 ns4"
  exit 1
fi

if [[ $# -ne 4 ]]; then
  echo "ERROR: Expected exactly 4 nameserver arguments, got $#."
  echo "  Usage: ./delegate-dns.sh [ZONE_ID] [--subdomain <name>] ns1 ns2 ns3 ns4"
  echo ""
  echo "  Get the nameservers from Terraform:"
  echo "    cd infra/aws && AWS_PROFILE=sandbox terraform output subdomain_nameservers"
  exit 1
fi

NS1="$1"
NS2="$2"
NS3="$3"
NS4="$4"

# Ensure each nameserver ends with a trailing dot (required by Route53)
for var in NS1 NS2 NS3 NS4; do
  val="${!var}"
  if [[ "$val" != *. ]]; then
    declare "$var"="${val}."
  fi
done

# ---- Create/update the NS delegation record ----------------------------------
echo "Creating NS delegation record for ${SUBDOMAIN} in zone ${PARENT_ZONE_ID}..."
echo "  ${NS1}"
echo "  ${NS2}"
echo "  ${NS3}"
echo "  ${NS4}"
echo ""

CHANGE_BATCH=$(cat <<JSON
{
  "Comment": "Delegating ${SUBDOMAIN} to the Sudoku sandbox account for Amplify",
  "Changes": [
    {
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "${SUBDOMAIN}",
        "Type": "NS",
        "TTL": 300,
        "ResourceRecords": [
          {"Value": "${NS1}"},
          {"Value": "${NS2}"},
          {"Value": "${NS3}"},
          {"Value": "${NS4}"}
        ]
      }
    }
  ]
}
JSON
)

aws route53 change-resource-record-sets \
  --hosted-zone-id "${PARENT_ZONE_ID}" \
  --change-batch "${CHANGE_BATCH}"

echo ""
echo "Delegation record created successfully."
echo ""
echo "Next steps:"
echo "  1. Wait for DNS propagation (usually 2-5 minutes, up to 48h)."
echo "     Check progress: dig NS ${SUBDOMAIN}"
echo "  2. Amplify will automatically validate the ACM certificate (~5-10 minutes)."
echo "  3. Once validated, ${SUBDOMAIN} will be live."
