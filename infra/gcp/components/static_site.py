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
        web_app_display_name: str = "Sudoku",
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

        # The Firebase Web App supplies the VITE_FIREBASE_* values the frontend build needs
        # (CP-GCP-042). Nothing created one before — the old project's was made by hand or by the
        # Firebase CLI, so it existed outside IaC entirely and its absence here only surfaced when
        # the smoke-user script asked for an API key that did not exist.
        self.web_app = gcp.firebase.WebApp(
            f"{name}-webapp",
            project=project,
            display_name=web_app_display_name,
            # Keep the app if the stack is destroyed: deleting it invalidates the API key that
            # any built frontend bundle has already baked in.
            deletion_policy="ABANDON",
            opts=pulumi.ResourceOptions.merge(
                opts, pulumi.ResourceOptions(parent=self, depends_on=[self.firebase_project])
            ),
        )

        # api_key is server-assigned, so this is a data source rather than an attribute.
        self._web_config = gcp.firebase.get_web_app_config_output(
            web_app_id=self.web_app.app_id, project=project
        )
        self.web_api_key: pulumi.Output[str] = self._web_config.api_key
        self.web_auth_domain: pulumi.Output[str] = self._web_config.auth_domain
        self.web_app_id: pulumi.Output[str] = self.web_app.app_id

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
                "web_app_id": self.web_app_id,
                "web_api_key": self.web_api_key,
                "web_auth_domain": self.web_auth_domain,
            }
        )
