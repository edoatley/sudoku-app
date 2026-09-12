"""The monthly billing budget and its alert plumbing.

**Lives in the `bootstrap` stack, not `app`.** A `gcp.billing.Budget` is scoped to the *billing
account*, not the project, so granting the CI deploy identity `billing.budgets.*` would give a
repo-federated principal write access across every project on that account — a materially worse
blast radius than the Terraform model it replaces. The budget changes about once a year; it
belongs with the human-run stack.
@spec CP-GCP-060, CP-PUL-060
"""

from __future__ import annotations

import pulumi
import pulumi_gcp as gcp

ACTUAL_THRESHOLD = 0.8
FORECAST_THRESHOLD = 1.0


class CostGuardrails(pulumi.ComponentResource):
    """A Pub/Sub topic, an email channel, and a budget alerting at 80% actual / 100% forecast.

    Alert-only. GCP budgets cannot attach a deny action the way AWS Budgets can, so automated
    enforcement stays deferred (`CP-GCP-061`).
    """

    def __init__(
        self,
        name: str,
        *,
        project: pulumi.Input[str],
        project_number: pulumi.Input[str],
        billing_account: str,
        alert_email: str,
        amount_usd: str,
        opts: pulumi.ResourceOptions | None = None,
    ) -> None:
        super().__init__("sudoku:gcp:CostGuardrails", name, None, opts)
        child = pulumi.ResourceOptions.merge(opts, pulumi.ResourceOptions(parent=self))

        self.topic = gcp.pubsub.Topic(
            f"{name}-topic",
            project=project,
            name="sudoku-budget-alerts",
            opts=child,
        )

        self.channel = gcp.monitoring.NotificationChannel(
            f"{name}-email",
            project=project,
            display_name="Sudoku budget alerts",
            type="email",
            labels={"email_address": alert_email},
            opts=child,
        )

        self.budget = gcp.billing.Budget(
            f"{name}-budget",
            billing_account=billing_account,
            display_name="SudokuMonthly",
            budget_filter=gcp.billing.BudgetBudgetFilterArgs(
                projects=[pulumi.Output.concat("projects/", project_number)],
            ),
            amount=gcp.billing.BudgetAmountArgs(
                specified_amount=gcp.billing.BudgetAmountSpecifiedAmountArgs(
                    currency_code="USD",
                    units=amount_usd,
                ),
            ),
            threshold_rules=[
                gcp.billing.BudgetThresholdRuleArgs(
                    threshold_percent=ACTUAL_THRESHOLD,
                    spend_basis="CURRENT_SPEND",
                ),
                gcp.billing.BudgetThresholdRuleArgs(
                    threshold_percent=FORECAST_THRESHOLD,
                    spend_basis="FORECASTED_SPEND",
                ),
            ],
            all_updates_rule=gcp.billing.BudgetAllUpdatesRuleArgs(
                pubsub_topic=self.topic.id,
                monitoring_notification_channels=[self.channel.id],
                schema_version="1.0",
            ),
            opts=child,
        )

        self.budget_name: pulumi.Output[str] = self.budget.name
        self.topic_id: pulumi.Output[str] = self.topic.id

        self.register_outputs({"budget_name": self.budget_name, "topic_id": self.topic_id})
