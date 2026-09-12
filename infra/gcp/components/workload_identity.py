"""Workload Identity Federation, letting GitHub Actions impersonate the deploy service account
without a long-lived key. @spec CP-PUL-011
"""

from __future__ import annotations

import pulumi
import pulumi_gcp as gcp

GITHUB_OIDC_ISSUER = "https://token.actions.githubusercontent.com"


class WorkloadIdentityFederation(pulumi.ComponentResource):
    """A pool, an OIDC provider restricted to one repository, and the impersonation grant."""

    def __init__(
        self,
        name: str,
        *,
        project: pulumi.Input[str],
        github_repo: str,
        service_account_name: pulumi.Input[str],
        pool_id: str = "github-pool",
        provider_id: str = "github-provider",
        opts: pulumi.ResourceOptions | None = None,
    ) -> None:
        super().__init__("sudoku:gcp:WorkloadIdentityFederation", name, None, opts)
        child = pulumi.ResourceOptions.merge(opts, pulumi.ResourceOptions(parent=self))

        self.pool = gcp.iam.WorkloadIdentityPool(
            f"{name}-pool",
            project=project,
            workload_identity_pool_id=pool_id,
            display_name="GitHub Actions pool",
            opts=child,
        )

        self.provider = gcp.iam.WorkloadIdentityPoolProvider(
            f"{name}-provider",
            project=project,
            workload_identity_pool_id=self.pool.workload_identity_pool_id,
            workload_identity_pool_provider_id=provider_id,
            display_name="GitHub OIDC",
            oidc=gcp.iam.WorkloadIdentityPoolProviderOidcArgs(issuer_uri=GITHUB_OIDC_ISSUER),
            attribute_mapping={
                "google.subject": "assertion.sub",
                "attribute.repository": "assertion.repository",
            },
            # THE security boundary. Without it any GitHub repository — including a fork — could
            # mint a token and impersonate the deploy SA: the classic confused deputy. Google
            # requires a condition here for exactly this reason.
            attribute_condition=f"assertion.repository == '{github_repo}'",
            opts=child,
        )

        # The pool's resource name is server-assigned, so this string cannot be known until the
        # pool exists. `github-bootstrap.sh` has to shell out to `gcloud --format='value(name)'`
        # to discover it; here it is an ordinary output composition.
        self.principal_set: pulumi.Output[str] = pulumi.Output.concat(
            "principalSet://iam.googleapis.com/",
            self.pool.name,
            "/attribute.repository/",
            github_repo,
        )

        self.impersonation = gcp.serviceaccount.IAMMember(
            f"{name}-impersonation",
            service_account_id=service_account_name,
            role="roles/iam.workloadIdentityUser",
            member=self.principal_set,
            opts=child,
        )

        self.provider_resource_name: pulumi.Output[str] = self.provider.name

        self.register_outputs(
            {
                "provider_resource_name": self.provider_resource_name,
                "principal_set": self.principal_set,
            }
        )
