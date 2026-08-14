#!/usr/bin/env bash
# bedrock-quota-report.sh — Report AWS Bedrock service quotas and CloudWatch usage
# for the models used by the Sudoku image recognition service (eu-west-2).
#
# Usage:
#   bash bedrock-quota-report.sh
#   bash bedrock-quota-report.sh --request-increase          # print increase commands for all
#   bash bedrock-quota-report.sh --request-increase L-XXXX  # print increase command for one quota
#
# Requires: aws CLI v2, jq
# AWS profile: sandbox (set via AWS_PROFILE=sandbox)

set -euo pipefail

export AWS_PROFILE=sandbox
export AWS_PAGER=""

# ── Configuration ─────────────────────────────────────────────────────────────
REGION="eu-west-2"
SERVICE_CODE="bedrock"
CW_LOOKBACK_MINUTES=60

# ── Colours ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; DIM='\033[2m'; BOLD='\033[1m'; RESET='\033[0m'

# ── Model definitions ─────────────────────────────────────────────────────────
MODEL_IDS=(
  "eu.amazon.nova-pro-v1:0"
  "eu.amazon.nova-lite-v1:0"
)

declare -A MODEL_SHORT=(
  ["eu.amazon.nova-pro-v1:0"]="Nova Pro"
  ["eu.amazon.nova-lite-v1:0"]="Nova Lite"
)

# Substrings used to match quota names in the service-quotas API
declare -A MODEL_KEYWORD=(
  ["eu.amazon.nova-pro-v1:0"]="Amazon Nova Pro"
  ["eu.amazon.nova-lite-v1:0"]="Amazon Nova Lite"
)

# ── Argument parsing ──────────────────────────────────────────────────────────
REQUEST_INCREASE=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --request-increase)
      REQUEST_INCREASE="${2:-all}"
      # Consume the optional second arg only if it doesn't look like a flag
      if [[ $# -ge 2 && "${2}" != --* ]]; then shift 2; else shift 1; fi
      ;;
    --help|-h)
      sed -n '2,12p' "$0" | sed 's/^# \?//'
      exit 0
      ;;
    *)
      echo "Unknown flag: $1 (use --help)" >&2
      exit 1
      ;;
  esac
done

# ── Prerequisite check ────────────────────────────────────────────────────────
echo -e "\n${BOLD}${CYAN}=== Checking prerequisites ===${RESET}"
MISSING=false
check_cmd() {
  if command -v "$1" &>/dev/null; then
    echo -e "  ${GREEN}✓${RESET} $1"
  else
    echo -e "  ${RED}✗${RESET} $1  (not found)"
    MISSING=true
  fi
}
check_cmd aws
check_cmd jq
if [[ "${MISSING}" == "true" ]]; then
  echo -e "${RED}ERROR: missing prerequisites above — please install them.${RESET}" >&2
  exit 1
fi

# ── Credentials check ─────────────────────────────────────────────────────────
echo -e "\n${BOLD}${CYAN}=== Verifying AWS credentials (profile=sandbox, region=${REGION}) ===${RESET}"
ACCOUNT_ID=$(aws sts get-caller-identity \
  --region "${REGION}" --query Account --output text 2>/dev/null) || {
  echo -e "${RED}ERROR: AWS credentials not valid or profile 'sandbox' not configured.${RESET}" >&2
  echo "Try: aws sso login --profile sandbox" >&2
  exit 1
}
echo -e "  ${GREEN}✓${RESET} Account: ${ACCOUNT_ID}  Region: ${REGION}"

# ── Phase 2: Fetch all Bedrock service quotas (paginated) ─────────────────────
echo -e "\n${BOLD}${CYAN}=== Fetching Bedrock service quotas... ===${RESET}"

fetch_all_quotas() {
  local result="[]"
  local page next_token=""
  while true; do
    if [[ -n "${next_token}" ]]; then
      page=$(aws service-quotas list-service-quotas \
        --service-code "${SERVICE_CODE}" \
        --region "${REGION}" \
        --starting-token "${next_token}" \
        --output json)
    else
      page=$(aws service-quotas list-service-quotas \
        --service-code "${SERVICE_CODE}" \
        --region "${REGION}" \
        --output json)
    fi
    result=$(printf '%s' "${result}" | jq \
      --argjson new "$(printf '%s' "${page}" | jq '.Quotas')" \
      '. + $new')
    next_token=$(printf '%s' "${page}" | jq -r '.NextToken // empty')
    [[ -z "${next_token}" ]] && break
  done
  printf '%s' "${result}"
}

ALL_QUOTAS=$(fetch_all_quotas)
QUOTA_COUNT=$(printf '%s' "${ALL_QUOTAS}" | jq 'length')
echo -e "  ${GREEN}✓${RESET} Retrieved ${QUOTA_COUNT} quota entries"

# ── Phase 3: CloudWatch usage ─────────────────────────────────────────────────
echo -e "\n${BOLD}${CYAN}=== Fetching CloudWatch metrics (last ${CW_LOOKBACK_MINUTES} min)... ===${RESET}"

# macOS-compatible date arithmetic
if date -u -v-1M +"%Y-%m-%dT%H:%M:%SZ" &>/dev/null 2>&1; then
  START_TIME=$(date -u -v-${CW_LOOKBACK_MINUTES}M +"%Y-%m-%dT%H:%M:%SZ")
else
  START_TIME=$(date -u --date="${CW_LOOKBACK_MINUTES} minutes ago" +"%Y-%m-%dT%H:%M:%SZ")
fi
END_TIME=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
CW_PERIOD=$(( CW_LOOKBACK_MINUTES * 60 ))

cw_stat() {
  local metric_name="$1" model_id="$2" statistic="$3"
  aws cloudwatch get-metric-statistics \
    --namespace "AWS/Bedrock" \
    --metric-name "${metric_name}" \
    --dimensions "Name=ModelId,Value=${model_id}" \
    --start-time "${START_TIME}" \
    --end-time "${END_TIME}" \
    --period "${CW_PERIOD}" \
    --statistics "${statistic}" \
    --region "${REGION}" \
    --output json 2>/dev/null || echo '{"Datapoints":[]}'
}

declare -A CW_TPM_USAGE CW_THROTTLES CW_INVOCATIONS
for mid in "${MODEL_IDS[@]}"; do
  dp_tpm=$(cw_stat "EstimatedTPMQuotaUsage" "${mid}" "Maximum")
  dp_throttle=$(cw_stat "InvocationThrottles" "${mid}" "Sum")
  dp_invoke=$(cw_stat "Invocations" "${mid}" "Sum")
  CW_TPM_USAGE["${mid}"]=$(printf '%s' "${dp_tpm}" | jq -r 'if (.Datapoints | length) > 0 then .Datapoints[0].Maximum else "—" end')
  CW_THROTTLES["${mid}"]=$(printf '%s' "${dp_throttle}" | jq -r 'if (.Datapoints | length) > 0 then (.Datapoints[0].Sum | floor) else "0" end')
  CW_INVOCATIONS["${mid}"]=$(printf '%s' "${dp_invoke}" | jq -r 'if (.Datapoints | length) > 0 then (.Datapoints[0].Sum | floor) else "0" end')
done
echo -e "  ${GREEN}✓${RESET} CloudWatch metrics fetched (window: ${START_TIME} → ${END_TIME})"

# ── Helper: extract quota field for a model ───────────────────────────────────
# Usage: get_quota_field <ALL_QUOTAS_JSON> <keyword> <type: rpm|tpm> <field: code|value|adjustable>
get_quota_field() {
  local json="$1" keyword="$2" type="$3" field="$4"
  local type_filter
  if [[ "${type}" == "rpm" ]]; then
    type_filter="requests per minute"
  else
    type_filter="tokens per minute"
  fi
  printf '%s' "${json}" | jq -r \
    --arg kw "${keyword}" \
    --arg tf "${type_filter}" \
    '[.[] | select(
        (.QuotaName | ascii_downcase | contains($kw | ascii_downcase)) and
        (.QuotaName | ascii_downcase | contains($tf)) and
        (.QuotaName | ascii_downcase | contains("on-demand"))
      )] | first |
      if . == null then "—"
      elif "'"${field}"'" == "code" then .QuotaCode
      elif "'"${field}"'" == "value" then (.Value | floor | tostring)
      else (.Adjustable | tostring)
      end'
}

# ── Phase 4: Render report table ──────────────────────────────────────────────
REPORT_TIME=$(date -u +"%Y-%m-%d %H:%M:%S UTC")
DIV="$(printf '═%.0s' {1..90})"

echo ""
echo -e "${BOLD}${DIV}${RESET}"
printf "${BOLD}  Bedrock Quota Report — %-12s — Account: %s${RESET}\n" "${REGION}" "${ACCOUNT_ID}"
echo -e "  Generated: ${REPORT_TIME}  |  CloudWatch lookback: last ${CW_LOOKBACK_MINUTES} min"
echo -e "${BOLD}${DIV}${RESET}"
echo ""

printf "${BOLD}%-22s  %-5s  %-12s  %-12s  %-10s  %-12s  %-5s  %s${RESET}\n" \
  "MODEL" "TYPE" "LIMIT" "TPM USAGE%" "THROTTLES" "INVOCATIONS" "ADJ" "QUOTA CODE"
printf "%-22s  %-5s  %-12s  %-12s  %-10s  %-12s  %-5s  %s\n" \
  "----------------------" "-----" "------------" "------------" "----------" "------------" "-----" "--------------"

for mid in "${MODEL_IDS[@]}"; do
  kw="${MODEL_KEYWORD[$mid]}"
  short="${MODEL_SHORT[$mid]}"
  tpm_pct="${CW_TPM_USAGE[$mid]}"
  throttles="${CW_THROTTLES[$mid]}"
  invocations="${CW_INVOCATIONS[$mid]}"

  rpm_code=$(get_quota_field "${ALL_QUOTAS}" "${kw}" "rpm" "code")
  rpm_val=$(get_quota_field "${ALL_QUOTAS}" "${kw}" "rpm" "value")
  rpm_adj=$(get_quota_field "${ALL_QUOTAS}" "${kw}" "rpm" "adjustable")

  tpm_code=$(get_quota_field "${ALL_QUOTAS}" "${kw}" "tpm" "code")
  tpm_val=$(get_quota_field "${ALL_QUOTAS}" "${kw}" "tpm" "value")
  tpm_adj=$(get_quota_field "${ALL_QUOTAS}" "${kw}" "tpm" "adjustable")

  # Format numbers with thousands separators using printf + awk
  fmt_num() { [[ "$1" == "—" ]] && echo "—" || printf "%'.0f" "$1" 2>/dev/null || echo "$1"; }
  rpm_display=$(fmt_num "${rpm_val}")
  tpm_display=$(fmt_num "${tpm_val}")

  # Colour TPM usage percentage
  if [[ "${tpm_pct}" == "—" ]]; then
    tpm_colour="${DIM}"
    tpm_display_pct="—"
  else
    tpm_int=$(printf "%.0f" "${tpm_pct}")
    if (( tpm_int >= 80 )); then
      tpm_colour="${RED}"
    elif (( tpm_int >= 50 )); then
      tpm_colour="${YELLOW}"
    else
      tpm_colour="${GREEN}"
    fi
    tpm_display_pct="${tpm_pct}%"
  fi

  # RPM row — throttles and invocations apply at the request level
  printf "%-22s  %-5s  %-12s  %-12s  %-10s  %-12s  %-5s  %s\n" \
    "${short}" "RPM" "${rpm_display}" "—" "${throttles}" "${invocations}" \
    "${rpm_adj}" "${rpm_code}"

  # TPM row — usage % is the CloudWatch EstimatedTPMQuotaUsage metric
  printf "%-22s  %-5s  %-12s  ${tpm_colour}%-12s${RESET}  %-10s  %-12s  %-5s  %s\n" \
    "" "TPM" "${tpm_display}" "${tpm_display_pct}" "—" "—" \
    "${tpm_adj}" "${tpm_code}"
done

echo ""
echo -e "${DIM}Notes:"
echo -e "  TPM USAGE% = EstimatedTPMQuotaUsage from CloudWatch (peak % in last ${CW_LOOKBACK_MINUTES} min)"
echo -e "  '—' in TPM USAGE% = no CloudWatch data (no recent traffic or metric not yet published)"
echo -e "  Throttles / Invocations = totals for the lookback window (from InvocationThrottles / Invocations metrics)"
echo -e "  RPM usage % is not available as a CloudWatch metric; monitor via Throttles count instead${RESET}"
echo -e "${BOLD}${DIV}${RESET}"

# ── Phase 5: Quota increase instructions ─────────────────────────────────────
if [[ -n "${REQUEST_INCREASE}" ]]; then
  echo ""
  echo -e "${BOLD}${CYAN}======== Quota Increase CLI Commands ========${RESET}"
  echo ""
  echo -e "Requesting an increase on a model's ${BOLD}TPM quota${RESET} causes AWS support to offer"
  echo -e "increases across all 5 related quotas: on-demand RPM & TPM, cross-region"
  echo -e "RPM & TPM, and model invocation max tokens per day."
  echo ""

  FOUND=false
  for mid in "${MODEL_IDS[@]}"; do
    kw="${MODEL_KEYWORD[$mid]}"
    short="${MODEL_SHORT[$mid]}"

    tpm_code=$(get_quota_field "${ALL_QUOTAS}" "${kw}" "tpm" "code")
    tpm_val=$(get_quota_field "${ALL_QUOTAS}" "${kw}" "tpm" "value")
    tpm_adj=$(get_quota_field "${ALL_QUOTAS}" "${kw}" "tpm" "adjustable")

    # Filter: show all, or only the requested quota code
    if [[ "${REQUEST_INCREASE}" != "all" && "${REQUEST_INCREASE}" != "${tpm_code}" ]]; then
      continue
    fi

    FOUND=true
    echo -e "${BOLD}# ${short} — On-demand TPM quota increase${RESET}"
    echo -e "# Current limit: ${tpm_val}  |  Quota code: ${tpm_code}  |  Adjustable: ${tpm_adj}"
    if [[ "${tpm_code}" == "—" ]]; then
      echo -e "${YELLOW}# WARNING: quota code not found — run without --request-increase to see available codes${RESET}"
    else
      echo ""
      echo "aws service-quotas request-service-quota-increase \\"
      echo "  --service-code bedrock \\"
      echo "  --quota-code ${tpm_code} \\"
      echo "  --desired-value <YOUR_DESIRED_VALUE> \\"
      echo "  --region ${REGION}"
    fi
    echo ""
  done

  if [[ "${FOUND}" == "false" ]]; then
    echo -e "${YELLOW}No matching quota found for '${REQUEST_INCREASE}'.${RESET}"
    echo "Run without --request-increase to see quota codes in the table above."
  fi

  echo -e "${DIM}To check request status:${RESET}"
  echo "aws service-quotas list-requested-service-quota-changes-by-service \\"
  echo "  --service-code bedrock --region ${REGION} --output table"
fi
