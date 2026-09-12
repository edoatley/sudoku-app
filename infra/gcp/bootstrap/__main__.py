"""Sudoku GCP — bootstrap stack.

Creates the project and everything the `app` stack needs in order to exist: APIs, the Pulumi
state bucket, the KMS key for app-stack secrets, Artifact Registry, the three service accounts,
their IAM, Workload Identity Federation, and the billing budget.

**Run locally, by a human.** This stack is the privilege boundary: it holds exactly the rights
the CI deploy identity deliberately does not. See docs/llds/cloud-platform-gcp.md — The Two-Stack
Model, and docs/planning/gcp-pulumi-phase-2-bootstrap.md for the run procedure (including the
one-time local-backend -> GCS state migration).

@spec CP-PUL-001, CP-PUL-002, CP-PUL-003, CP-PUL-010, CP-PUL-011, CP-PUL-012, CP-PUL-013,
      CP-PUL-040, CP-PUL-041, CP-PUL-060
"""

from __future__ import annotations

import pulumi
import pulumi_gcp as gcp

from components.artifact_registry import ArtifactRegistry
from components.cost_guardrails import CostGuardrails
from components.project_foundation import ProjectFoundation
from components.service_identity import ServiceIdentity
from components.workload_identity import WorkloadIdentityFederation

config = pulumi.Config("sudoku")

project_id = config.require("projectId")
project_name = config.get("projectName") or "Sudoku"
billing_account = config.require("billingAccount")
state_bucket_name = config.require("stateBucketName")
region = config.get("region") or "us-central1"
github_repo = config.get("githubRepo") or "edoatley/sudoku-app"
alert_email = config.require("alertEmail")
budget_amount_usd = config.get("budgetAmountUsd") or "20"

# ── Project, APIs, state and KMS ───────────────────────────────────────────────
foundation = ProjectFoundation(
    "sudoku",
    project_id=project_id,
    project_name=project_name,
    billing_account=billing_account,
    state_bucket_name=state_bucket_name,
    region=region,
)
project = foundation.project_id

# ── Container images ──────────────────────────────────────────────────────────
registry = ArtifactRegistry("sudoku", project=project, region=region)

# ── Runtime identities ────────────────────────────────────────────────────────
# Both runtime accounts hold aiplatform.user: the backend for the Vertex coach, and
# image-recognition for the Vertex vision path that Phase 5 introduces. Neither holds any
# Secret Manager role — after de-Bedrocking there is no secret to read.
backend_sa = ServiceIdentity(
    "backend-run",
    project=project,
    account_id="sudoku-run",
    display_name="Sudoku backend (Cloud Run)",
    project_roles=["roles/datastore.user", "roles/aiplatform.user"],
)

image_recognition_sa = ServiceIdentity(
    "image-recognition-run",
    project=project,
    account_id="sudoku-image-recognition-run",
    display_name="Sudoku image recognition (Cloud Run)",
    project_roles=["roles/datastore.user", "roles/aiplatform.user"],
)

# ── CI deploy identity ────────────────────────────────────────────────────────
# The privilege boundary. This account can manage application resources and nothing else:
# no IAM administration, no Workload Identity administration, no Secret Manager, and no
# billing-account role. It cannot grant itself anything. @spec CP-PUL-012
DEPLOY_ROLES = [
    "roles/datastore.owner",
    "roles/firebase.admin",
    "roles/dns.admin",
    "roles/serviceusage.serviceUsageAdmin",
    "roles/run.admin",
    "roles/iam.serviceAccountUser",
    "roles/artifactregistry.writer",
    # Needed from Phase 4, when Identity Platform config moves into the app stack.
    "roles/identityplatform.admin",
]

deploy_sa = ServiceIdentity(
    "deploy",
    project=project,
    account_id="sudoku-deploy",
    display_name="Sudoku CI deploy (GitHub Actions via WIF)",
    project_roles=DEPLOY_ROLES,
)

# Scoped grants, kept flat rather than wrapped in a component: this is a single-use list, and a
# component over one use is an abstraction for its own sake.
deploy_state_access = gcp.storage.BucketIAMMember(
    "deploy-state-access",
    bucket=foundation.state_bucket.name,
    role="roles/storage.objectAdmin",
    member=deploy_sa.member,
)

# Mandatory and easy to miss: without it every CI run dies with an opaque decrypt error the
# first time the app stack reads a KMS-encrypted config value. @spec CP-PUL-041
deploy_kms_access = gcp.kms.CryptoKeyIAMMember(
    "deploy-kms-access",
    crypto_key_id=foundation.crypto_key.id,
    role="roles/cloudkms.cryptoKeyEncrypterDecrypter",
    member=deploy_sa.member,
)

# ── GitHub federation ─────────────────────────────────────────────────────────
federation = WorkloadIdentityFederation(
    "github",
    project=project,
    github_repo=github_repo,
    service_account_name=deploy_sa.account.name,
)

# ── Cost guardrail ────────────────────────────────────────────────────────────
# In this stack, not the app stack: gcp.billing.Budget is scoped to the BILLING ACCOUNT, so a
# repo-federated CI identity must never hold billing.budgets.*. @spec CP-PUL-060
guardrails = CostGuardrails(
    "sudoku",
    project=project,
    project_number=foundation.project_number,
    billing_account=billing_account,
    alert_email=alert_email,
    amount_usd=budget_amount_usd,
)

# ── Outputs ───────────────────────────────────────────────────────────────────
# Consumed by the app stack's StackReference, and by the operator when setting GitHub secrets
# and running `pulumi stack init --secrets-provider`.
pulumi.export("project_id", foundation.project_id)
pulumi.export("project_number", foundation.project_number)
pulumi.export("region", region)
pulumi.export("state_bucket_url", foundation.state_bucket_url)
pulumi.export("kms_key_uri", foundation.kms_key_uri)
pulumi.export("artifact_registry_url", registry.repository_url)
pulumi.export("run_service_account_email", backend_sa.email)
pulumi.export("image_recognition_service_account_email", image_recognition_sa.email)
pulumi.export("deploy_service_account_email", deploy_sa.email)
pulumi.export("wif_provider_name", federation.provider_resource_name)
