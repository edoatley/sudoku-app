"""Identity Platform: the auth config, the Google sign-in provider, and authorized domains.

Reverses `CP-GCP-031`, which put this outside IaC. Only the OAuth consent screen and the OAuth
2.0 client stay manual, because no GCP API creates OAuth client IDs (`CP-PUL-032`).
@spec CP-GCP-030, CP-PUL-030
"""

from __future__ import annotations

import pulumi
import pulumi_gcp as gcp

FIREBASE_ISSUER_PREFIX = "https://securetoken.google.com/"


class IdentityPlatform(pulumi.ComponentResource):
    """The project's single-tenant Identity Platform configuration."""

    def __init__(
        self,
        name: str,
        *,
        project: pulumi.Input[str],
        authorized_domains: pulumi.Input[list[str]],
        google_client_id: pulumi.Input[str],
        google_client_secret: pulumi.Input[str],
        opts: pulumi.ResourceOptions | None = None,
    ) -> None:
        super().__init__("sudoku:gcp:IdentityPlatform", name, None, opts)
        child = pulumi.ResourceOptions.merge(opts, pulumi.ResourceOptions(parent=self))

        self.config = gcp.identityplatform.Config(
            f"{name}-config",
            project=project,
            authorized_domains=authorized_domains,
            sign_in=gcp.identityplatform.ConfigSignInArgs(
                # Email/password exists solely for the CI smoke-test user, which authenticates
                # via signInWithPassword. The app itself offers Google sign-in only, and the
                # backend's UserIdentityResolver rejects any other sign_in_provider.
                email=gcp.identityplatform.ConfigSignInEmailArgs(
                    enabled=True,
                    password_required=True,
                ),
                # Declared explicitly although disabled. The server populates this block
                # regardless, so omitting it makes `pulumi refresh` report drift on every single
                # run — and a drift check that always fires is one nobody reads, which would
                # mask the real drift it exists to catch.
                phone_number=gcp.identityplatform.ConfigSignInPhoneNumberArgs(
                    enabled=False,
                    test_phone_numbers={},
                ),
            ),
            opts=child,
        )

        self.google_idp = gcp.identityplatform.DefaultSupportedIdpConfig(
            f"{name}-google-idp",
            project=project,
            idp_id="google.com",
            client_id=google_client_id,
            client_secret=google_client_secret,
            enabled=True,
            opts=pulumi.ResourceOptions.merge(
                opts, pulumi.ResourceOptions(parent=self, depends_on=[self.config])
            ),
        )

        # Firebase ID tokens are not OIDC-discoverable, so the backend needs issuer and audience
        # configured explicitly (`%gcp` profile: discovery-enabled=false plus an explicit
        # jwks-path). Both derive from the project id.
        self.issuer: pulumi.Output[str] = pulumi.Output.concat(FIREBASE_ISSUER_PREFIX, project)
        self.audience: pulumi.Output[str] = pulumi.Output.from_input(project)

        self.register_outputs({"issuer": self.issuer, "audience": self.audience})
