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

from components import naming
from components.dns_zone import DnsZone
from components.firestore_database import FirestoreDatabase
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
pulumi.export("run_service_account_email", run_sa)
pulumi.export("image_recognition_service_account_email", image_recognition_sa)
pulumi.export("artifact_registry_url", artifact_registry_url)
pulumi.export("custom_domain", custom_domain)
# Read with `pulumi stack output name_servers` to create the one-time NS delegation in the
# Route53 parent zone. Null on non-prod stacks, which own no zone.
pulumi.export("name_servers", dns.name_servers if dns else None)
