"""The CI deploy identity must not be able to escalate its own privilege.

This is the claim the whole two-stack split rests on, and it is the security argument for
revising the HLD's original "manual identity on GCP" tenet. If the deploy service account ever
gains an IAM-admin, WIF-admin, Secret Manager or billing role, the split stops meaning anything
and the revision stops being justified.

Asserted by parsing bootstrap/__main__.py rather than importing it — importing would execute the
program and demand real config. @spec CP-PUL-012
"""

from __future__ import annotations

import ast
import pathlib

import pytest

BOOTSTRAP = pathlib.Path(__file__).resolve().parent.parent / "bootstrap" / "__main__.py"

# Prefixes that would let the deploy identity grant itself something, read a secret, or spend
# money outside the project.
FORBIDDEN_ROLE_PREFIXES = (
    "roles/owner",
    "roles/editor",
    "roles/resourcemanager.projectIamAdmin",
    "roles/iam.securityAdmin",
    "roles/iam.serviceAccountAdmin",
    "roles/iam.serviceAccountKeyAdmin",
    "roles/iam.workloadIdentityPoolAdmin",
    "roles/secretmanager.",
    "roles/billing.",
)


def _deploy_roles() -> list[str]:
    tree = ast.parse(BOOTSTRAP.read_text(), filename=str(BOOTSTRAP))
    for node in ast.walk(tree):
        if isinstance(node, ast.Assign) and any(
            isinstance(t, ast.Name) and t.id == "DEPLOY_ROLES" for t in node.targets
        ):
            return [ast.literal_eval(e) for e in node.value.elts]
    raise AssertionError("DEPLOY_ROLES not found in bootstrap/__main__.py")


def test_deploy_roles_are_declared():
    # Guards the guard: a rename would make every assertion below vacuous.
    roles = _deploy_roles()
    assert roles, "DEPLOY_ROLES is empty"
    assert all(r.startswith("roles/") for r in roles), roles


@pytest.mark.parametrize("prefix", FORBIDDEN_ROLE_PREFIXES)
def test_deploy_identity_cannot_escalate(prefix: str):
    offenders = [r for r in _deploy_roles() if r.startswith(prefix)]
    assert not offenders, (
        f"The CI deploy identity must not hold {prefix}* — it would break the two-stack "
        f"privilege split that justifies the HLD tenet revision. Found: {offenders}"
    )


def test_no_duplicate_roles():
    roles = _deploy_roles()
    assert len(roles) == len(set(roles)), roles


def test_billing_budget_is_not_granted_to_the_deploy_identity():
    # A budget is scoped to the BILLING ACCOUNT, not the project: granting it here would give a
    # repo-federated principal write access across every project on the account. CostGuardrails
    # therefore lives in this stack, run by a human. @spec CP-PUL-060
    source = BOOTSTRAP.read_text()
    assert "CostGuardrails" in source, "the budget should be created by the bootstrap stack"
    assert not [r for r in _deploy_roles() if "billing" in r]
