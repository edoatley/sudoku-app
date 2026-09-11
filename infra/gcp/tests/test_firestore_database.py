"""@spec CP-GCP-020, CP-GCP-022, CP-GCP-023, CP-GCP-024, CP-PUL-022"""

from __future__ import annotations

import pulumi

from components.firestore_database import FirestoreDatabase


def build(name: str, **overrides) -> FirestoreDatabase:
    kwargs: dict = dict(
        project="test-project",
        database_id="(default)",
        location="us-central1",
        is_prod=True,
    )
    kwargs.update(overrides)
    return FirestoreDatabase(name, **kwargs)


@pulumi.runtime.test
def test_prod_enables_pitr_and_delete_protection():
    db = build("prod").database

    def check(args):
        pitr, protection, policy = args
        assert pitr == "POINT_IN_TIME_RECOVERY_ENABLED"
        assert protection == "DELETE_PROTECTION_ENABLED"
        assert policy == "ABANDON"

    return pulumi.Output.all(
        db.point_in_time_recovery_enablement,
        db.delete_protection_state,
        db.deletion_policy,
    ).apply(check)


@pulumi.runtime.test
def test_ephemeral_stacks_are_disposable():
    # An rcg-* database must be destroyable, or teardown leaks cost on every branch.
    db = build("rc", is_prod=False, database_id="sudoku-rcg-x").database

    def check(args):
        pitr, protection, policy = args
        assert pitr == "POINT_IN_TIME_RECOVERY_DISABLED"
        assert protection == "DELETE_PROTECTION_DISABLED"
        assert policy == "DELETE"

    return pulumi.Output.all(
        db.point_in_time_recovery_enablement,
        db.delete_protection_state,
        db.deletion_policy,
    ).apply(check)


@pulumi.runtime.test
def test_native_mode_in_the_configured_location():
    db = build("loc", is_prod=False, database_id="d1").database

    def check(args):
        assert args[0] == "FIRESTORE_NATIVE"
        assert args[1] == "us-central1"

    return pulumi.Output.all(db.type, db.location_id).apply(check)


def test_default_ttl_covers_the_coach_rate_limiter():
    db = build("ttl", is_prod=False, database_id="d2")
    assert len(db.ttl_fields) == 1


@pulumi.runtime.test
def test_the_games_composite_index_matches_the_query():
    # userId + status + endedAt DESC — the game-history listing query. Getting the order wrong
    # makes Firestore reject the query at runtime, not at deploy time.
    db = build("idx", is_prod=False, database_id="d3")
    assert len(db.indexes) == 1

    def check(fields):
        assert [(f["field_path"], f["order"]) for f in fields] == [
            ("userId", "ASCENDING"),
            ("status", "ASCENDING"),
            ("endedAt", "DESCENDING"),
        ]

    return db.indexes[0].fields.apply(check)
