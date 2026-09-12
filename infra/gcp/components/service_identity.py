"""A service account plus its project role bindings.

**Additive bindings only.** Every grant here is a `gcp.projects.IAMMember`. Never use
`IAMBinding` (authoritative for a role, silently dropping other members) or `IAMPolicy`
(authoritative for the entire project, silently dropping every other binding — including the
operator's own Owner grant). This is the single largest footgun in the migration, and
`tests/test_no_authoritative_iam.py` fails the build if either appears.
@spec CP-PUL-010, CP-PUL-013
"""

from __future__ import annotations

from collections.abc import Sequence

import pulumi
import pulumi_gcp as gcp


class ServiceIdentity(pulumi.ComponentResource):
    """A service account and the project-level roles it holds."""

    def __init__(
        self,
        name: str,
        *,
        project: pulumi.Input[str],
        account_id: str,
        display_name: str,
        project_roles: Sequence[str] = (),
        opts: pulumi.ResourceOptions | None = None,
    ) -> None:
        super().__init__("sudoku:gcp:ServiceIdentity", name, None, opts)
        child = pulumi.ResourceOptions(parent=self)

        self.account = gcp.serviceaccount.Account(
            f"{name}-sa",
            project=project,
            account_id=account_id,
            display_name=display_name,
            opts=child,
        )

        self.email: pulumi.Output[str] = self.account.email
        self.member: pulumi.Output[str] = pulumi.Output.concat(
            "serviceAccount:", self.account.email
        )

        self.role_bindings = [
            gcp.projects.IAMMember(
                f"{name}-role-{role.split('/')[-1].replace('.', '-')}",
                project=project,
                role=role,
                member=self.member,
                opts=child,
            )
            for role in project_roles
        ]

        self.register_outputs({"email": self.email, "member": self.member})
