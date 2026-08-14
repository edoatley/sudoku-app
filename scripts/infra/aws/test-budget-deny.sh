#!/usr/bin/env bash
# test-budget-deny.sh
#
# Verifies that the AWS Budgets hard-cap mechanism works: manually attaches the
# SudokuBedrockDeny IAM policy to SudokuLambdaExecRole, confirms Bedrock is blocked
# via IAM policy simulation, then detaches the policy and confirms Bedrock is
# allowed again.
#
# This simulates exactly what happens when monthly Bedrock spend hits $25 — no real
# Bedrock calls are made and no spend is incurred.
#
# Usage:
#   AWS_PROFILE=sandbox bash scripts/infra/aws/test-budget-deny.sh
#
# Prerequisites:
#   - AWS CLI v2 with a profile that has IAM read/write access
#   - Terraform default workspace applied (SudokuBedrockDeny policy must exist)
#
# Exit code: 0 if all checks pass, 1 if any fail.

set -euo pipefail

REGION="${AWS_DEFAULT_REGION:-eu-west-2}"
ROLE_NAME="SudokuLambdaExecRole"
DENY_POLICY_NAME="SudokuBedrockDeny"
BEDROCK_ACTION="bedrock:InvokeModel"
BEDROCK_RESOURCE="arn:aws:bedrock:${REGION}::foundation-model/anthropic.claude-haiku-4-5-20251001-v1:0"

PASS=0
FAIL=1
RESULT=0

info()  { echo "  [info]  $*"; }
ok()    { echo "  ✅  $*"; }
fail()  { echo "  ❌  $*"; RESULT=1; }

# ── Resolve ARNs ───────────────────────────────────────────────────────────────

echo ""
echo "==> Resolving ARNs"

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/${ROLE_NAME}"
info "Role:        ${ROLE_ARN}"

DENY_POLICY_ARN=$(aws iam list-policies --scope Local \
    --query "Policies[?PolicyName=='${DENY_POLICY_NAME}'].Arn" \
    --output text 2>/dev/null || true)

if [ -z "${DENY_POLICY_ARN}" ]; then
    echo ""
    echo "  ❌  Policy '${DENY_POLICY_NAME}' not found in account ${ACCOUNT_ID}."
    echo "      Has the Terraform default workspace been applied with budget_alert_email set?"
    exit 1
fi
info "Deny policy: ${DENY_POLICY_ARN}"

# ── Helper: simulate bedrock:InvokeModel for the role ─────────────────────────

simulate() {
    aws iam simulate-principal-policy \
        --policy-source-arn "${ROLE_ARN}" \
        --action-names "${BEDROCK_ACTION}" \
        --resource-arns "${BEDROCK_RESOURCE}" \
        --query "EvaluationResults[0].EvalDecision" \
        --output text 2>/dev/null
}

# ── Verify policy is NOT attached before we start ─────────────────────────────

echo ""
echo "==> Pre-flight: confirming deny policy is not already attached"

ATTACHED=$(aws iam list-attached-role-policies --role-name "${ROLE_NAME}" \
    --query "AttachedPolicies[?PolicyArn=='${DENY_POLICY_ARN}'].PolicyArn" \
    --output text 2>/dev/null || true)

if [ -n "${ATTACHED}" ]; then
    info "Deny policy already attached — detaching before test"
    aws iam detach-role-policy --role-name "${ROLE_NAME}" --policy-arn "${DENY_POLICY_ARN}"
    sleep 5
fi

# ── Step 1: baseline — Bedrock should be ALLOWED ──────────────────────────────

echo ""
echo "==> Step 1: baseline check (deny policy NOT attached)"

DECISION=$(simulate)
info "${BEDROCK_ACTION} decision: ${DECISION}"

if [ "${DECISION}" = "allowed" ]; then
    ok "Bedrock is ALLOWED — baseline confirmed"
else
    fail "Expected 'allowed' but got '${DECISION}' — check role policies before proceeding"
    exit 1
fi

# ── Step 2: attach the deny policy ────────────────────────────────────────────

echo ""
echo "==> Step 2: attaching SudokuBedrockDeny to ${ROLE_NAME}"

aws iam attach-role-policy --role-name "${ROLE_NAME}" --policy-arn "${DENY_POLICY_ARN}"
ok "Policy attached"

# IAM policy evaluation is eventually consistent; give it a moment.
info "Waiting 10 s for IAM propagation..."
sleep 10

# ── Step 3: Bedrock should now be DENIED ──────────────────────────────────────

echo ""
echo "==> Step 3: confirming Bedrock is BLOCKED with deny policy attached"

DECISION=$(simulate)
info "${BEDROCK_ACTION} decision: ${DECISION}"

if [ "${DECISION}" = "explicitDeny" ]; then
    ok "Bedrock is EXPLICITLY DENIED — hard cap mechanism works correctly"
else
    fail "Expected 'explicitDeny' but got '${DECISION}'"
fi

# ── Step 4: detach the deny policy ────────────────────────────────────────────

echo ""
echo "==> Step 4: detaching SudokuBedrockDeny (restoring normal access)"

aws iam detach-role-policy --role-name "${ROLE_NAME}" --policy-arn "${DENY_POLICY_ARN}"
ok "Policy detached"

info "Waiting 10 s for IAM propagation..."
sleep 10

# ── Step 5: Bedrock should be ALLOWED again ───────────────────────────────────

echo ""
echo "==> Step 5: confirming Bedrock is ALLOWED again after detach"

DECISION=$(simulate)
info "${BEDROCK_ACTION} decision: ${DECISION}"

if [ "${DECISION}" = "allowed" ]; then
    ok "Bedrock is ALLOWED — access restored"
else
    fail "Expected 'allowed' but got '${DECISION}' — manual detach may be needed"
    echo ""
    echo "  Run to restore manually:"
    echo "    aws iam detach-role-policy --role-name ${ROLE_NAME} --policy-arn ${DENY_POLICY_ARN}"
fi

# ── Summary ───────────────────────────────────────────────────────────────────

echo ""
if [ "${RESULT}" = "0" ]; then
    echo "✅  Budget deny mechanism verified: attaches cleanly, blocks Bedrock, detaches cleanly."
else
    echo "❌  One or more checks failed — see output above."
fi
echo ""

exit "${RESULT}"
