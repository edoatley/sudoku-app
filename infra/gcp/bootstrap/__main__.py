"""Sudoku GCP — bootstrap stack.

Creates the project and everything the `app` stack needs in order to exist: APIs, the Pulumi
state bucket, the KMS key for app-stack secrets, Artifact Registry, the three service accounts,
their IAM, Workload Identity Federation, and the billing budget.

**Run locally, by a human.** This stack is the privilege boundary: it holds the rights that the
CI deploy identity deliberately does not. See docs/llds/cloud-platform-gcp.md — The Two-Stack
Model.

Phase 1 note: this program is written but not yet run against any cloud. Wiring and acceptance
criteria are in docs/planning/gcp-pulumi-phase-2-bootstrap.md.
"""

from __future__ import annotations

import pulumi

config = pulumi.Config("sudoku")

# Placeholder wiring: Phase 2 populates this with ProjectFoundation, ArtifactRegistry,
# ServiceIdentity x3, WorkloadIdentityFederation, the deploy-privilege grants, and
# CostGuardrails, then exports project_id / project_number / state_bucket_url / kms_key_uri /
# wif_provider_name / the service-account emails / artifact_registry_url.
pulumi.log.info("bootstrap stack is scaffolded; resources land in Phase 2")
