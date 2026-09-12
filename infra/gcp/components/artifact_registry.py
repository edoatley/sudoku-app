"""The Docker repository that holds both service images.

**One repository, not two.** `scripts/infra/gcp/bootstrap.sh` creates `sudoku-backend` and
`sudoku-image-recognition`, but CI has only ever pushed to the first — both images live there
under different image names (`backend`, `image-recognition`). The second is dropped.
@spec CP-PUL-003
"""

from __future__ import annotations

import pulumi
import pulumi_gcp as gcp

UNTAGGED_MAX_AGE_SECONDS = "2592000s"  # 30 days
KEEP_RECENT_VERSIONS = 10


class ArtifactRegistry(pulumi.ComponentResource):
    """A Docker repository with cleanup policies, shared across all stacks."""

    def __init__(
        self,
        name: str,
        *,
        project: pulumi.Input[str],
        region: str,
        repository_id: str = "sudoku-backend",
        opts: pulumi.ResourceOptions | None = None,
    ) -> None:
        super().__init__("sudoku:gcp:ArtifactRegistry", name, None, opts)

        self.repository = gcp.artifactregistry.Repository(
            f"{name}-repo",
            project=project,
            location=region,
            repository_id=repository_id,
            format="DOCKER",
            description="Sudoku backend and image-recognition container images",
            cleanup_policies=[
                gcp.artifactregistry.RepositoryCleanupPolicyArgs(
                    id="keep-10-recent",
                    action="KEEP",
                    most_recent_versions=gcp.artifactregistry.RepositoryCleanupPolicyMostRecentVersionsArgs(
                        keep_count=KEEP_RECENT_VERSIONS,
                    ),
                ),
                gcp.artifactregistry.RepositoryCleanupPolicyArgs(
                    id="delete-older-untagged",
                    action="DELETE",
                    condition=gcp.artifactregistry.RepositoryCleanupPolicyConditionArgs(
                        tag_state="UNTAGGED",
                        older_than=UNTAGGED_MAX_AGE_SECONDS,
                    ),
                ),
            ],
            # Policies must actually delete, not just report what they would delete.
            cleanup_policy_dry_run=False,
            opts=pulumi.ResourceOptions(parent=self),
        )

        # Host prefix is knowable, but the repository id is a resource attribute — build the URL
        # from the resource so a rename cannot silently desync CI's image paths.
        self.repository_url: pulumi.Output[str] = pulumi.Output.concat(
            region, "-docker.pkg.dev/", project, "/", self.repository.repository_id
        )

        self.register_outputs({"repository_url": self.repository_url})
