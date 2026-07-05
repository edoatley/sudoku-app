locals {
  # Only create budget resources in the default (production) workspace when an
  # alert email is configured. RC workspaces are ephemeral and generate no real spend.
  create_budget = local.is_default && var.budget_alert_email != ""
}

# ── Bedrock deny policy ────────────────────────────────────────────────────────
# Applied automatically by the budget action when monthly Bedrock spend hits the cap.
# A deny in any attached policy overrides the allow in sudoku_coach_bedrock,
# so the coach falls back to nudge text without any code change.
resource "aws_iam_policy" "bedrock_deny" {
  count = local.create_budget ? 1 : 0

  name        = "SudokuBedrockDeny"
  description = "Denies Bedrock invocations — attached automatically when monthly spend reaches the budget cap."

  # checkov:skip=CKV_AWS_355: Deny policy — wildcard Resource is intentional to block all Bedrock calls
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid      = "DenyBedrockOnBudgetExceeded"
      Effect   = "Deny"
      Action   = ["bedrock:InvokeModel", "bedrock:InvokeModelWithResponseStream"]
      Resource = "*"
    }]
  })
}

# ── Budgets execution role ─────────────────────────────────────────────────────
# AWS Budgets needs its own IAM role to attach/detach the deny policy.
resource "aws_iam_role" "budgets_execution" {
  count = local.create_budget ? 1 : 0

  name = "SudokuBudgetsExecutionRole"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "budgets.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "budgets_execution" {
  count = local.create_budget ? 1 : 0

  name = "SudokuBudgetsExecutionPolicy"
  role = aws_iam_role.budgets_execution[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["iam:AttachRolePolicy", "iam:DetachRolePolicy"]
        Resource = aws_iam_role.lambda_exec.arn
      },
      {
        Effect   = "Allow"
        Action   = ["iam:GetPolicy"]
        Resource = aws_iam_policy.bedrock_deny[0].arn
      }
    ]
  })
}

resource "aws_budgets_budget" "bedrock_monthly" {
  count = local.create_budget ? 1 : 0

  name         = "SudokuBedrockMonthly"
  budget_type  = "COST"
  limit_amount = var.bedrock_monthly_budget_usd
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  cost_filter {
    name   = "Service"
    values = ["Amazon Bedrock"]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.budget_alert_email]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.budget_alert_email]
  }
}

# ── Budget action: deny Bedrock when spend hits the cap ───────────────────────
# Fires when actual spend reaches 100% of the budget limit (default $25).
# Attaches the deny policy to the Lambda execution role — the coach then falls
# back to the hint nudge text via the existing exception handler in BedrockCoachClient.
resource "aws_budgets_budget_action" "deny_bedrock_on_limit" {
  count = local.create_budget ? 1 : 0

  budget_name        = aws_budgets_budget.bedrock_monthly[0].name
  action_type        = "APPLY_IAM_POLICY"
  approval_model     = "AUTOMATIC"
  notification_type  = "ACTUAL"
  execution_role_arn = aws_iam_role.budgets_execution[0].arn

  action_threshold {
    action_threshold_type  = "PERCENTAGE"
    action_threshold_value = 100
  }

  definition {
    iam_action_definition {
      policy_arn = aws_iam_policy.bedrock_deny[0].arn
      roles      = [aws_iam_role.lambda_exec.name]
    }
  }

  subscriber {
    address           = var.budget_alert_email
    subscription_type = "EMAIL"
  }
}

# SERVICE-dimensional monitor lets AWS detect anomalies per-service independently.
resource "aws_ce_anomaly_monitor" "bedrock" {
  count = local.create_budget ? 1 : 0

  name              = "SudokuBedrockAnomalyMonitor"
  monitor_type      = "DIMENSIONAL"
  monitor_dimension = "SERVICE"
}

# Alert when Bedrock anomalous spend exceeds $5 in a day.
resource "aws_ce_anomaly_subscription" "bedrock" {
  count = local.create_budget ? 1 : 0

  name      = "SudokuBedrockAnomalyAlert"
  frequency = "DAILY"

  monitor_arn_list = [aws_ce_anomaly_monitor.bedrock[0].arn]

  subscriber {
    type    = "EMAIL"
    address = var.budget_alert_email
  }

  threshold_expression {
    dimension {
      key           = "ANOMALY_TOTAL_IMPACT_ABSOLUTE"
      values        = ["5"]
      match_options = ["GREATER_THAN_OR_EQUAL"]
    }
  }
}
