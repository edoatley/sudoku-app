# ---------------------------------------------------------------------------
# Cost guardrail — Cloud Billing budget + Pub/Sub alert topic.
# Alert-only: unlike the AWS budget action that auto-attaches a Bedrock-deny
# policy, GCP budgets cannot enforce a hard cap. Automated enforcement (a
# Pub/Sub-triggered Cloud Function) is deferred — see CP-GCP-061.
# Only created in the default workspace when a billing account is supplied.
# ---------------------------------------------------------------------------

locals {
  create_budget = local.is_default && var.budget_billing_account != ""
}

resource "google_pubsub_topic" "budget_alerts" {
  count = local.create_budget ? 1 : 0
  name  = "sudoku-budget-alerts"

  # checkov:skip=CKV_GCP_83: CMEK/CSEK adds cost/ops; Google-managed encryption is sufficient for budget alert notifications, which carry no sensitive data
}

resource "google_monitoring_notification_channel" "budget_email" {
  count        = local.create_budget && var.budget_alert_email != "" ? 1 : 0
  display_name = "Sudoku budget alert email"
  type         = "email"

  labels = {
    email_address = var.budget_alert_email
  }
}

# @spec CP-GCP-060
resource "google_billing_budget" "monthly" {
  count           = local.create_budget ? 1 : 0
  billing_account = var.budget_billing_account
  display_name    = "SudokuMonthly"

  budget_filter {
    projects = ["projects/${data.google_project.current.number}"]
  }

  amount {
    specified_amount {
      currency_code = "USD"
      units         = var.monthly_budget_usd
    }
  }

  threshold_rules {
    threshold_percent = 0.8
    spend_basis       = "CURRENT_SPEND"
  }

  threshold_rules {
    threshold_percent = 1.0
    spend_basis       = "FORECASTED_SPEND"
  }

  all_updates_rule {
    pubsub_topic                     = google_pubsub_topic.budget_alerts[0].id
    monitoring_notification_channels = var.budget_alert_email != "" ? [google_monitoring_notification_channel.budget_email[0].id] : []
    disable_default_iam_recipients   = false
  }
}
