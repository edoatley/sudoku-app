"""Firebase Hosting: the Firebase project enrolment, the Hosting site, and its custom domain.

No `google-beta` provider is needed — `pulumi-gcp` is generated from the merged GA and beta
upstream providers and ships both in one SDK, so the `provider = google-beta` lines in
`firebase_hosting.tf` simply vanish.
@spec CP-GCP-040, CP-GCP-051
"""

from __future__ import annotations

import pulumi
import pulumi_gcp as gcp


class StaticSite(pulumi.ComponentResource):
    """A Firebase Hosting site, optionally served at a custom domain."""

    def __init__(
        self,
        name: str,
        *,
        project: pulumi.Input[str],
        site_id: str,
        custom_domain: str | None = None,
        wait_dns_verification: bool = False,
        opts: pulumi.ResourceOptions | None = None,
    ) -> None:
        super().__init__("sudoku:gcp:StaticSite", name, None, opts)
        child = pulumi.ResourceOptions.merge(opts, pulumi.ResourceOptions(parent=self))

        self.firebase_project = gcp.firebase.Project(
            f"{name}-firebase",
            project=project,
            opts=child,
        )

        self.site = gcp.firebase.HostingSite(
            f"{name}-site",
            project=project,
            site_id=site_id,
            opts=pulumi.ResourceOptions.merge(
                opts, pulumi.ResourceOptions(parent=self, depends_on=[self.firebase_project])
            ),
        )

        self.custom_domain_resource = None
        self.custom_domain_url: pulumi.Output[str] | None = None
        if custom_domain:
            self.custom_domain_resource = gcp.firebase.HostingCustomDomain(
                f"{name}-domain",
                project=project,
                site_id=self.site.site_id,
                custom_domain=custom_domain,
                # Cert issuance is asynchronous — minutes to ~24h — and the resource returns
                # before it completes. Waiting on the first apply would hang the pipeline, so
                # this is false for the initial bring-up and true thereafter.
                wait_dns_verification=wait_dns_verification,
                opts=pulumi.ResourceOptions.merge(
                    opts, pulumi.ResourceOptions(parent=self, protect=True)
                ),
            )
            self.custom_domain_url = pulumi.Output.from_input(f"https://{custom_domain}")

        self.site_id: pulumi.Output[str] = self.site.site_id
        # Known ahead of apply from the site id, which is why it can seed the backend's CORS list
        # without a dependency cycle.
        self.default_url: pulumi.Output[str] = pulumi.Output.concat(
            "https://", self.site.site_id, ".web.app"
        )

        self.register_outputs(
            {
                "site_id": self.site_id,
                "default_url": self.default_url,
                "custom_domain_url": self.custom_domain_url,
            }
        )
