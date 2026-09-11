"""@spec CP-GCP-060, CP-PUL-060"""

from __future__ import annotations

import inspect

import pulumi

from components import cost_guardrails
from components.cost_guardrails import CostGuardrails


def build(name: str, **overrides) -> CostGuardrails:
    kwargs: dict = dict(
        project="test-project",
        project_number="123456789012",
        billing_account="010F10-A51056-E8EC40",
        alert_email="alerts@example.com",
        amount_usd="20",
    )
    kwargs.update(overrides)
    return CostGuardrails(name, **kwargs)


@pulumi.runtime.test
def test_alerts_at_80_percent_actual_and_100_percent_forecast():
    return build("thresholds").budget.threshold_rules.apply(
        lambda rules: _assert(
            sorted((r["threshold_percent"], r["spend_basis"]) for r in rules)
            == [(0.8, "CURRENT_SPEND"), (1.0, "FORECASTED_SPEND")],
            rules,
        )
    )


@pulumi.runtime.test
def test_budget_is_scoped_to_this_project_only():
    # A budget filter is billing-account-wide by default; unscoped, it would alert on every
    # project on the account.
    return build("scope").budget.budget_filter.apply(
        lambda f: _assert(f["projects"] == ["projects/123456789012"], f)
    )


@pulumi.runtime.test
def test_alerts_reach_both_pubsub_and_email():
    return build("routes").budget.all_updates_rule.apply(
        lambda r: _assert(r.get("pubsub_topic") and r.get("monitoring_notification_channels"), r)
    )


def test_component_does_not_accept_a_deploy_identity():
    # CostGuardrails belongs to the bootstrap stack precisely because gcp.billing.Budget is
    # scoped to the BILLING ACCOUNT, not the project. If this ever grew an option to run under
    # the CI identity, that identity would need billing.budgets.* across every project on the
    # account. Keep the door shut. @spec CP-PUL-060
    params = inspect.signature(CostGuardrails.__init__).parameters
    assert "deploy_service_account" not in params
    assert "billing_account" in params


def test_enforcement_is_documented_as_deferred():
    # GCP budgets cannot attach a deny action the way AWS Budgets can (CP-GCP-061). If someone
    # later believes this component enforces a cap, they will be wrong.
    assert "CP-GCP-061" in (cost_guardrails.CostGuardrails.__doc__ or "")


def _assert(condition, detail="") -> None:
    assert condition, detail
