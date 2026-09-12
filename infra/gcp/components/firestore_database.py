"""The Firestore database, its TTL policies, and its composite indexes.

@spec CP-GCP-020, CP-GCP-022, CP-GCP-023, CP-GCP-024, CP-PUL-022
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

import pulumi
import pulumi_gcp as gcp


@dataclass(frozen=True)
class IndexField:
    field_path: str
    order: str  # "ASCENDING" | "DESCENDING"


@dataclass(frozen=True)
class IndexSpec:
    """A composite index. A frozen dataclass rather than a dict, so it is typed and testable."""

    collection: str
    fields: tuple[IndexField, ...]


@dataclass(frozen=True)
class TtlSpec:
    collection: str
    field: str


GAMES_BY_USER_STATUS_ENDED = IndexSpec(
    collection="games",
    fields=(
        IndexField("userId", "ASCENDING"),
        IndexField("status", "ASCENDING"),
        IndexField("endedAt", "DESCENDING"),
    ),
)

COACH_RATE_LIMIT_TTL = TtlSpec(collection="coachRateLimits", field="expiresAt")


class FirestoreDatabase(pulumi.ComponentResource):
    """A Native-mode Firestore database, hardened in production."""

    def __init__(
        self,
        name: str,
        *,
        project: pulumi.Input[str],
        database_id: str,
        location: str,
        is_prod: bool,
        indexes: Sequence[IndexSpec] = (GAMES_BY_USER_STATUS_ENDED,),
        ttls: Sequence[TtlSpec] = (COACH_RATE_LIMIT_TTL,),
        opts: pulumi.ResourceOptions | None = None,
    ) -> None:
        super().__init__("sudoku:gcp:FirestoreDatabase", name, None, opts)

        # `protect` is strictly stronger than Terraform's deletion_policy=ABANDON: it blocks
        # replacement as well as deletion, so a forced-new property change also fails loudly
        # rather than recreating the production database.
        db_opts = pulumi.ResourceOptions.merge(
            opts,
            pulumi.ResourceOptions(parent=self, protect=is_prod, retain_on_delete=is_prod),
        )

        self.database = gcp.firestore.Database(
            f"{name}-db",
            project=project,
            name=database_id,
            location_id=location,
            type="FIRESTORE_NATIVE",
            point_in_time_recovery_enablement=(
                "POINT_IN_TIME_RECOVERY_ENABLED" if is_prod else "POINT_IN_TIME_RECOVERY_DISABLED"
            ),
            delete_protection_state=(
                "DELETE_PROTECTION_ENABLED" if is_prod else "DELETE_PROTECTION_DISABLED"
            ),
            deletion_policy="ABANDON" if is_prod else "DELETE",
            opts=db_opts,
        )

        child = pulumi.ResourceOptions.merge(opts, pulumi.ResourceOptions(parent=self))

        self.ttl_fields = [
            gcp.firestore.Field(
                f"{name}-ttl-{ttl.collection}",
                project=project,
                database=self.database.name,
                collection=ttl.collection,
                field=ttl.field,
                ttl_config=gcp.firestore.FieldTtlConfigArgs(),
                opts=child,
            )
            for ttl in ttls
        ]

        self.indexes = [
            gcp.firestore.Index(
                f"{name}-index-{spec.collection}-{i}",
                project=project,
                database=self.database.name,
                collection=spec.collection,
                fields=[
                    gcp.firestore.IndexFieldArgs(field_path=f.field_path, order=f.order)
                    for f in spec.fields
                ],
                opts=child,
            )
            for i, spec in enumerate(indexes)
        ]

        self.name: pulumi.Output[str] = self.database.name
        self.register_outputs({"name": self.name})
