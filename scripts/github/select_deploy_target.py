#!/usr/bin/env python3
"""Decide which cloud a deployment goes to.

One place, shelled into from `ci-deploy.yml`'s `select-target` job, so the precedence lives
somewhere it can be unit-tested rather than spread across `branches:` filters in two workflows
that cannot see each other. Same pattern as `infra/gcp/components/naming.py`.

The precedence, per `docs/llds/cloud-platform.md` — Deploy Target Selection:

1. A ``workflow_dispatch`` ``target`` input other than ``auto`` wins outright.
2. Branch ``rc-*`` -> AWS; branch ``rcg-*`` -> GCP. Absolute; never consults the variable.
3. Branch ``main`` -> the repository variable ``DEPLOY_TARGET``, defaulting to ``aws``.

Every unrecognised value raises. The failure mode this guards against is deploying the wrong
cloud, which looks entirely healthy from the run's point of view — a green deploy to somewhere
nobody expected.

Usage (from a workflow step):

    eval "$(python3 scripts/github/select_deploy_target.py)"   # sets aws=/gcp= for GITHUB_OUTPUT

@spec CP-CD-001, CP-CD-002, CP-CD-003, CP-CD-004
"""

from __future__ import annotations

import os
import sys

AWS = "aws"
GCP = "gcp"
BOTH = "both"
AUTO = "auto"

_TARGETS: dict[str, set[str]] = {AWS: {AWS}, GCP: {GCP}, BOTH: {AWS, GCP}}
"""``both`` is two independent targets, not a third mode: each cloud deploys to its own stable
hostname, so selecting it is not a failover and not a merge. @spec CP-CD-004"""

PROD_BRANCH = "main"
AWS_BRANCH_PREFIX = "rc-"
GCP_BRANCH_PREFIX = "rcg-"


def select_deploy_target(
    *,
    event_name: str,
    ref_name: str,
    dispatch_target: str = "",
    deploy_target_var: str = "",
) -> set[str]:
    """Return the set of clouds to deploy to. Raises ``ValueError`` on anything unrecognised."""
    dispatch = (dispatch_target or "").strip().lower()
    if event_name == "workflow_dispatch" and dispatch and dispatch != AUTO:
        if dispatch not in _TARGETS:
            raise ValueError(
                f"workflow_dispatch target {dispatch_target!r} is not one of "
                f"{sorted([*_TARGETS, AUTO])}"
            )
        return _TARGETS[dispatch]

    # Checked before the AWS prefix: "rcg-" also starts with "rc", so testing in the other order
    # sends every GCP environment to AWS. infra/gcp/main.tf's `is_rc` was the mirror image of this
    # bug — it tested for "rc-" against names that always began "rcg-", and so never matched.
    if ref_name.startswith(GCP_BRANCH_PREFIX):
        return {GCP}
    if ref_name.startswith(AWS_BRANCH_PREFIX):
        return {AWS}

    if ref_name == PROD_BRANCH:
        var = (deploy_target_var or "").strip().lower() or AWS
        if var not in _TARGETS:
            raise ValueError(
                f"DEPLOY_TARGET {deploy_target_var!r} is not one of {sorted(_TARGETS)} — "
                "refusing to guess, because guessing deploys a cloud nobody asked for"
            )
        return _TARGETS[var]

    raise ValueError(
        f"branch {ref_name!r} does not deploy: expected {PROD_BRANCH!r}, "
        f"{AWS_BRANCH_PREFIX}* or {GCP_BRANCH_PREFIX}*"
    )


def main() -> int:
    """Print ``aws=``/``gcp=`` lines for ``$GITHUB_OUTPUT``."""
    try:
        targets = select_deploy_target(
            event_name=os.environ.get("GITHUB_EVENT_NAME", ""),
            ref_name=os.environ.get("GITHUB_REF_NAME", ""),
            dispatch_target=os.environ.get("DISPATCH_TARGET", ""),
            deploy_target_var=os.environ.get("DEPLOY_TARGET", ""),
        )
    except ValueError as exc:
        print(f"::error::{exc}", file=sys.stderr)
        return 1
    print(f"aws={str(AWS in targets).lower()}")
    print(f"gcp={str(GCP in targets).lower()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
