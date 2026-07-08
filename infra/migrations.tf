# =============================================================================
# migrations.tf — Terraform moved{}/import{} blocks for zero-destroy migrations
# =============================================================================
# Each block tells Terraform to treat an existing state entry at its old
# address as if it now lives at the new address (or, for import{}, brings an
# untracked pre-existing AWS resource under management).
#
# IMPORTANT: Confirm the exact internal module resource addresses before
# applying. After `terraform init`, run:
#   terraform state list | grep <resource_type>
# If addresses differ from the targets below, update them before applying.
#
# Once a migration has been applied to ALL live workspaces, its blocks can be
# removed. Phases 1-4 (bespoke → module refactor, commit c22b98f) were
# verified applied to every live workspace (default, rc-shared,
# rc-terraform-review) on 2026-07-08 and removed. In the process, a 4th
# workspace, rc-test-cicd, was found still on the old bespoke addresses with a
# state lock stale since 2026-04-02 (orphaned: its git branch no longer
# existed) — it was force-unlocked, destroyed, and its workspace deleted.
# =============================================================================

# ── Phase 5: main Lambda log group moved out of the module (M3 infra-review fix) ─
# RC workspaces previously had the module create/manage this log group directly
# (use_existing_cloudwatch_log_group = false); it now lives at a standalone
# address so retention can be set uniformly. No-ops on the default workspace,
# which never had this resource under the module (see import{} below instead).
moved {
  from = module.lambda.aws_cloudwatch_log_group.lambda[0]
  to   = aws_cloudwatch_log_group.lambda
}

# The default (production) workspace's log group was auto-created by Lambda
# before Terraform managed it, so it needs a one-time import instead of a move.
# for_each is empty on every other workspace, so this only ever runs for default.
import {
  for_each = local.is_default ? toset(["lambda"]) : toset([])
  to       = aws_cloudwatch_log_group.lambda
  id       = "/aws/lambda/sudoku"
}

# ── Phase 6: anomaly monitor/subscription renamed to reflect account-wide scope (M5 infra-review fix) ─
moved {
  from = aws_ce_anomaly_monitor.bedrock
  to   = aws_ce_anomaly_monitor.account_wide
}

moved {
  from = aws_ce_anomaly_subscription.bedrock
  to   = aws_ce_anomaly_subscription.account_wide
}
