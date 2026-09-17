"""Sudoku GCP — application stack.

Firestore, the two Cloud Run services, Identity Platform, Firebase Hosting and Cloud DNS. Run by
CI as the Workload-Identity-federated deploy service account, which holds no IAM-admin,
WIF-admin or billing permission — see docs/llds/cloud-platform-gcp.md, The Two-Stack Model.

The Hosting custom domain is attached in Phase 8 rather than here, deliberately: Google-managed
certificate issuance is asynchronous and needs the NS delegation created by this stack to have
already propagated.
"""

from __future__ import annotations

import pulumi
import pulumi_gcp as gcp
import pulumi_random as random

from components import naming
from components.container_service import ContainerService
from components.dns_zone import DnsZone
from components.firestore_database import FirestoreDatabase
from components.identity_platform import IdentityPlatform
from components.service_env import backend_env, image_recognition_env
from components.static_site import StaticSite

BACKEND_MAX_INSTANCES = 4
BACKEND_CONCURRENCY = 40
IMAGE_RECOGNITION_MAX_INSTANCES = 2
IMAGE_RECOGNITION_CONCURRENCY = 4
"""GCP has no API-Gateway-style request-rate throttle. These caps, multiplied together and
bounded by the per-request timeout, ARE the spend and load bound. @spec CP-GCP-013"""

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
project_id = config.require("projectId")

# Required on production, absent on ephemeral stacks. Those serve from their own *.web.app origin,
# own no DNS, and share the project's single Identity Platform tenant — so a new stack needs no
# configuration file of its own beyond the defaults in Pulumi.yaml.
custom_domain = config.require("customDomain") if is_prod else None
dns_zone_domain = config.require("dnsZoneDomain") if is_prod else None

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
# Firebase **tombstones a deleted site's name**, so a non-production id must never be reused: a
# torn-down stack redeployed under the same id fails part-way through, at the Hosting site,
# leaving the rest of the environment to clean up by hand. The suffix lives in Pulumi state, so
# it is fixed for this stack's life and fresh whenever a stack is created.
#
# Not derived from git: branches cut from the same base share a first commit, and a rebase
# rewrites the hash — which, site_id being immutable, would replace the site, change its URL and
# burn the old name. Production has no suffix; its site is long-lived and never deleted.
site_unique = None
if is_prod:
    site_id: pulumi.Input[str] = naming.hosting_site_id(stack, project_id)
else:
    site_unique = random.RandomId(
        "sudoku-site-unique", byte_length=naming.SITE_UNIQUE_SUFFIX_LEN // 2
    )
    site_id = site_unique.hex.apply(lambda hex_: naming.hosting_site_id(stack, project_id, hex_))

site = StaticSite(
    "sudoku",
    project=project,
    site_id=site_id,
    # Both are once-per-project concerns that production owns. An ephemeral stack creates its own
    # site and web app inside the enrolment production already made.
    enroll_firebase=is_prod,
    abandon_web_app=is_prod,
)

# ── Authentication ────────────────────────────────────────────────────────────
# Identity Platform is configured once per *project*, not per stack: the tenant, its authorised
# domains and the Google IdP are all project-level singletons. Production owns them; an ephemeral
# stack signs in against the same tenant rather than declaring a second one that cannot exist.
#
# The consequence is that an RC stack's own *.web.app origin is not an authorised sign-in origin.
# `localhost` is, on every stack, which is why a locally-served UI pointed at the RC backend is
# the supported way to exercise a real Google sign-in against an ephemeral environment. The
# password-based smoke user is unaffected — authorised domains gate the OAuth redirect only.
identity = None
if is_prod:
    # Authorised domains must cover every origin a sign-in can be initiated from. One missing
    # entry fails only at runtime, with redirect_uri_mismatch — so the list is built from the same
    # values Hosting uses rather than typed out again.
    authorized_domains = pulumi.Output.all(site.site_id, project).apply(
        lambda args: [
            "localhost",
            f"{args[0]}.web.app",
            f"{args[1]}.firebaseapp.com",
            custom_domain,
        ]
    )

    # Second API to need this, after the billing budget: identitytoolkit demands a quota project,
    # which human ADC does not supply, so the call lands on Google's default client project and
    # 403s. Scoped to a dedicated provider rather than set stack-wide — a global user-project
    # override makes every call send the header, which then requires serviceusage on the target
    # project and breaks gcp.projects.Service. Harmless in CI, where service-account credentials
    # carry an implicit quota project.
    quota_provider = gcp.Provider(
        "gcp-quota-project",
        project=project_id,
        region=region,
        user_project_override=True,
        billing_project=project_id,
    )

    identity = IdentityPlatform(
        "sudoku",
        project=project,
        authorized_domains=authorized_domains,
        google_client_id=config.require("googleOauthClientId"),
        google_client_secret=config.require_secret("googleOauthClientSecret"),
        opts=pulumi.ResourceOptions(provider=quota_provider),
    )

# ── Compute ───────────────────────────────────────────────────────────────────
# Both services are created only when an image tag is configured. That gates them on the presence
# of a built artefact rather than on a feature toggle — a toggle is what allowed a manual deploy
# dispatch to silently revert the Vertex cutover on the Terraform path, and it also lets an
# infrastructure-only `pulumi up` run against a stack that has never had an image pushed.
backend_image_tag = config.get("backendImageTag")
image_recognition_image_tag = config.get("imageRecognitionImageTag")

# Derived from the same function that names the Hosting site, so the origin the backend allows and
# the origin the frontend is served from cannot disagree. The equivalent AWS wiring needs a
# post-apply `update-user-pool-client` call because the Amplify URL is not knowable here.
# @spec CP-GCP-012
if is_prod:
    cors_origins: pulumi.Input[str] = naming.cors_allowed_origins(stack, project_id, custom_domain)
else:
    cors_origins = site_unique.hex.apply(
        lambda hex_: naming.cors_allowed_origins(stack, project_id, unique_suffix=hex_)
    )

backend = None
if backend_image_tag:
    backend = ContainerService(
        "backend",
        project=project,
        location=region,
        service_name=f"sudoku{naming.suffix(stack)}",
        image=pulumi.Output.concat(artifact_registry_url, "/backend:", backend_image_tag),
        service_account_email=run_sa,
        env=backend_env(project=project, region=region, cors_origins=cors_origins),
        max_instances=BACKEND_MAX_INSTANCES,
        concurrency=BACKEND_CONCURRENCY,
        deletion_protection=is_prod,
    )

image_recognition = None
if image_recognition_image_tag:
    image_recognition = ContainerService(
        "image-recognition",
        project=project,
        location=region,
        service_name=f"sudoku-image-recognition{naming.suffix(stack)}",
        image=pulumi.Output.concat(
            artifact_registry_url, "/image-recognition:", image_recognition_image_tag
        ),
        service_account_email=image_recognition_sa,
        env=image_recognition_env(project=project, cors_origins=cors_origins),
        max_instances=IMAGE_RECOGNITION_MAX_INSTANCES,
        concurrency=IMAGE_RECOGNITION_CONCURRENCY,
        deletion_protection=is_prod,
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
# Null until an image tag is configured. The frontend build fails loud on an empty backend URL
# rather than shipping a hostless "/api/v1" into the SPA bundle.
pulumi.export("backend_url", backend.url if backend else None)
pulumi.export("image_recognition_url", image_recognition.url if image_recognition else None)
pulumi.export("run_service_account_email", run_sa)
pulumi.export("image_recognition_service_account_email", image_recognition_sa)
pulumi.export("artifact_registry_url", artifact_registry_url)
pulumi.export("custom_domain", custom_domain)
# The backend's %gcp profile hard-codes these shapes; a mismatch 401s every request. Exported so
# Phase 6 wires the Cloud Run environment from the stack rather than by hand. @spec CP-GCP-011
pulumi.export("identity_platform_issuer", identity.issuer if identity else None)
pulumi.export("identity_platform_audience", identity.audience if identity else None)
# Read with `pulumi stack output name_servers` to create the one-time NS delegation in the
# Route53 parent zone. Null on non-prod stacks, which own no zone.
pulumi.export("name_servers", dns.name_servers if dns else None)
