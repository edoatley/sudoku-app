"""The GCP project itself, its enabled APIs, the Pulumi state bucket, and the KMS key.

Owned by the `bootstrap` stack, which runs from a developer machine with human credentials.
@spec CP-PUL-001, CP-PUL-002, CP-PUL-040
"""

from __future__ import annotations

from collections.abc import Sequence

import pulumi
import pulumi_gcp as gcp

REQUIRED_APIS: tuple[str, ...] = (
    "cloudresourcemanager.googleapis.com",
    "cloudkms.googleapis.com",  # Pulumi secrets provider for the app stack
    "run.googleapis.com",
    "artifactregistry.googleapis.com",
    "firestore.googleapis.com",
    "firebase.googleapis.com",
    "firebasehosting.googleapis.com",
    "identitytoolkit.googleapis.com",
    "dns.googleapis.com",
    "billingbudgets.googleapis.com",
    "pubsub.googleapis.com",
    # Needed to READ back the budget's notification channel. Creation succeeds without it,
    # so the gap only surfaces on `pulumi refresh` — silent until then.
    "monitoring.googleapis.com",
    # iam.googleapis.com is NOT enabled by default on a new project, and without it the WIF
    # provider and every serviceaccount IAM binding fail with SERVICE_DISABLED. The old
    # bootstrap.sh omitted it too and got away with it only because its project predated the
    # default-API changes.
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",  # Workload Identity Federation
    "sts.googleapis.com",  # Workload Identity Federation
    "aiplatform.googleapis.com",  # Vertex AI — coach and image recognition
)
"""Ported from `scripts/infra/gcp/bootstrap.sh`, with three changes:

* dropped ``secretmanager`` — after de-Bedrocking there is no secret to store, and the project
  ends with zero long-lived credentials;
* dropped ``policytroubleshooter`` — a diagnostic, not a dependency;
* added ``cloudkms`` — the app stack's secrets provider.
"""


class ProjectFoundation(pulumi.ComponentResource):
    """Creates the project, links billing, enables APIs, and provisions state + KMS."""

    def __init__(
        self,
        name: str,
        *,
        project_id: str,
        project_name: str,
        billing_account: str,
        state_bucket_name: str,
        region: str,
        apis: Sequence[str] = REQUIRED_APIS,
        opts: pulumi.ResourceOptions | None = None,
    ) -> None:
        super().__init__("sudoku:gcp:ProjectFoundation", name, None, opts)
        child = pulumi.ResourceOptions.merge(opts, pulumi.ResourceOptions(parent=self))

        self.project = gcp.organizations.Project(
            f"{name}-project",
            project_id=project_id,
            name=project_name,
            billing_account=billing_account,
            # Suppress the default VPC and its four firewall rules. The old project has them and
            # does not want them; Cloud Run reaches Firestore over the managed public API.
            auto_create_network=False,
            # Guard against an accidental `pulumi destroy` deleting the whole project.
            deletion_policy="PREVENT",
            opts=child,
        )

        # Everything below must wait for these. Pulumi infers dependencies from data flow, and
        # a Service produces no value the other resources consume — so without an explicit
        # depends_on, Pulumi creates the API enablements *in parallel with* the resources that
        # require them. cloudkms in particular is not enabled by default on a new project, so
        # the KeyRing loses that race and the first `up` fails.
        self.services = [
            gcp.projects.Service(
                f"{name}-api-{api.split('.')[0]}",
                project=self.project.project_id,
                service=api,
                # Destroying the bootstrap stack must not start disabling APIs underneath a live
                # app stack.
                disable_on_destroy=False,
                opts=child,
            )
            for api in apis
        ]

        # Pulumi's own state. Versioned from birth so the Phase 2 state migration is recoverable.
        self.state_bucket = gcp.storage.Bucket(
            f"{name}-state",
            name=state_bucket_name,
            project=self.project.project_id,
            location=region.upper(),
            versioning=gcp.storage.BucketVersioningArgs(enabled=True),
            uniform_bucket_level_access=True,
            public_access_prevention="enforced",
            opts=pulumi.ResourceOptions.merge(
                opts,
                pulumi.ResourceOptions(parent=self, protect=True, depends_on=self.services),
            ),
        )

        self.key_ring = gcp.kms.KeyRing(
            f"{name}-keyring",
            name="sudoku-pulumi",
            project=self.project.project_id,
            location=region,
            opts=pulumi.ResourceOptions.merge(
                opts,
                pulumi.ResourceOptions(parent=self, protect=True, depends_on=self.services),
            ),
        )

        # Losing this key makes every app-stack secret permanently unreadable. KMS keys cannot be
        # deleted (only versions destroyed, after a >=24h delay), but protect it anyway and never
        # move it between key rings. @spec CP-PUL-041
        self.crypto_key = gcp.kms.CryptoKey(
            f"{name}-key",
            name="pulumi-secrets",
            key_ring=self.key_ring.id,
            purpose="ENCRYPT_DECRYPT",
            opts=pulumi.ResourceOptions.merge(
                opts, pulumi.ResourceOptions(parent=self, protect=True)
            ),
        )

        self.project_id: pulumi.Output[str] = self.project.project_id
        self.project_number: pulumi.Output[str] = self.project.number
        self.state_bucket_url: pulumi.Output[str] = pulumi.Output.concat(
            "gs://", self.state_bucket.name
        )
        # The secrets-provider URI cannot be known until the key exists; the app stack consumes
        # it as `pulumi stack init --secrets-provider`.
        self.kms_key_uri: pulumi.Output[str] = pulumi.Output.concat(
            "gcp-kms://", self.crypto_key.id
        )

        self.register_outputs(
            {
                "project_id": self.project_id,
                "project_number": self.project_number,
                "state_bucket_url": self.state_bucket_url,
                "kms_key_uri": self.kms_key_uri,
            }
        )
