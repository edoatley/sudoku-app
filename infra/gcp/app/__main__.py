"""Sudoku GCP — application stack.

Firestore, the two Cloud Run services, Identity Platform, Firebase Hosting and Cloud DNS. Run by
CI as the Workload-Identity-federated deploy service account, which holds no IAM-admin,
WIF-admin or billing permission — see docs/llds/cloud-platform-gcp.md, The Two-Stack Model.

Phase 3 scope: data, hosting and the DNS zone. Cloud Run lands in Phase 6, Identity Platform in
Phase 4, and the Hosting custom domain in Phase 8 — deliberately, so the NS delegation created
here has days to propagate before anything depends on it.
"""

from __future__ import annotations

import pulumi
import pulumi_gcp as gcp

from components import naming
from components.dns_zone import DnsZone
from components.firestore_database import FirestoreDatabase
from components.identity_platform import IdentityPlatform
from components.static_site import StaticSite

config = pulumi.Config("sudoku")
stack = pulumi.get_stack()
is_prod = naming.is_prod(stack)

# On a self-managed backend every project sits under a virtual organization named by the literal
# constant "organization" — not an account name, and not the bare stack name `pulumi stack ls`
# displays. Requires the project-scoped state layout, which this backend uses.
bootstrap = pulumi.StackReference(
    config.get("bootstrapStack") or "organization/sudoku-gcp-bootstrap/prod"
)

project = bootstrap.require_output("project_id")
region = config.get("region") or "us-central1"
custom_domain = config.require("customDomain")
dns_zone_domain = config.require("dnsZoneDomain")

# Pass through for the phases that follow, so Phase 6 does not re-derive what bootstrap already
# exported and the two cannot drift.
run_sa = bootstrap.require_output("run_service_account_email")
image_recognition_sa = bootstrap.require_output("image_recognition_service_account_email")
artifact_registry_url = bootstrap.require_output("artifact_registry_url")

# ── Persistence ───────────────────────────────────────────────────────────────
# The location is irreversible: a Firestore database cannot be moved, only deleted and recreated,
# and prod is protected against exactly that.
firestore = FirestoreDatabase(
    "sudoku",
    project=project,
    database_id=naming.firestore_database_id(stack),
    location=region,
    is_prod=is_prod,
)

# ── Frontend hosting ──────────────────────────────────────────────────────────
# No custom domain in this phase. Attaching it is Phase 8, after the NS delegation below has
# propagated — Google-managed cert issuance is asynchronous and needs DNS already answering.
site = StaticSite(
    "sudoku",
    project=project,
    site_id=naming.hosting_site_id(stack, config.require("projectId")),
)

# ── Authentication ────────────────────────────────────────────────────────────
# Authorised domains must cover every origin a sign-in can be initiated from. One missing entry
# fails only at runtime, with redirect_uri_mismatch — so the list is built from the same values
# Hosting uses rather than typed out again.
authorized_domains = pulumi.Output.all(site.site_id, project).apply(
    lambda args: [
        "localhost",
        f"{args[0]}.web.app",
        f"{args[1]}.firebaseapp.com",
        custom_domain,
    ]
)

# Second API to need this, after the billing budget: identitytoolkit demands a quota project,
# which human ADC does not supply, so the call lands on Google's default client project and 403s.
# Scoped to a dedicated provider rather than set stack-wide — a global user-project override
# makes every call send the header, which then requires serviceusage on the target project and
# breaks gcp.projects.Service. Harmless in CI, where service-account credentials carry an
# implicit quota project.
quota_provider = gcp.Provider(
    "gcp-quota-project",
    project=config.require("projectId"),
    region=region,
    user_project_override=True,
    billing_project=config.require("projectId"),
)

identity = IdentityPlatform(
    "sudoku",
    project=project,
    authorized_domains=authorized_domains,
    google_client_id=config.require("googleOauthClientId"),
    google_client_secret=config.require_secret("googleOauthClientSecret"),
    opts=pulumi.ResourceOptions(provider=quota_provider),
)

# ── DNS ───────────────────────────────────────────────────────────────────────
# Only the production stack owns the zone; ephemeral rcg-* stacks serve from their own
# *.web.app origin and never touch DNS.
dns = None
if is_prod:
    dns = DnsZone(
        "sudoku",
        project=project,
        zone_name="gcp-edoatley",
        dns_name=f"{dns_zone_domain}.",
    )

# ── Outputs ───────────────────────────────────────────────────────────────────
pulumi.export("project_id", project)
pulumi.export("region", region)
pulumi.export("firestore_database", firestore.name)
pulumi.export("hosting_site_id", site.site_id)
pulumi.export("hosting_default_url", site.default_url)
# The VITE_FIREBASE_* values the frontend build consumes. @spec CP-GCP-042
pulumi.export("firebase_web_app_id", site.web_app_id)
pulumi.export("firebase_api_key", site.web_api_key)
pulumi.export("firebase_auth_domain", site.web_auth_domain)
pulumi.export("run_service_account_email", run_sa)
pulumi.export("image_recognition_service_account_email", image_recognition_sa)
pulumi.export("artifact_registry_url", artifact_registry_url)
pulumi.export("custom_domain", custom_domain)
# The backend's %gcp profile hard-codes these shapes; a mismatch 401s every request. Exported so
# Phase 6 wires the Cloud Run environment from the stack rather than by hand. @spec CP-GCP-011
pulumi.export("identity_platform_issuer", identity.issuer)
pulumi.export("identity_platform_audience", identity.audience)
# Read with `pulumi stack output name_servers` to create the one-time NS delegation in the
# Route53 parent zone. Null on non-prod stacks, which own no zone.
pulumi.export("name_servers", dns.name_servers if dns else None)
