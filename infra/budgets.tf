locals {
  # Only create budget resources in the default (production) workspace when an
  # alert email is configured. RC workspaces are ephemeral and generate no real spend.
  create_budget = local.is_default && var.budget_alert_email != ""
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
