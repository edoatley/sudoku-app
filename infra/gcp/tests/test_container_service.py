"""@spec CP-GCP-001, CP-GCP-003, CP-GCP-004, CP-GCP-013, CP-PUL-020, CP-PUL-023"""

from __future__ import annotations

import pulumi

from components.container_service import ContainerService


def build(name: str, **overrides) -> ContainerService:
    kwargs: dict = dict(
        project="test-project",
        location="us-central1",
        service_name="sudoku",
        image="us-central1-docker.pkg.dev/test-project/sudoku-backend/backend:abc123",
        service_account_email="sudoku-run@test-project.iam.gserviceaccount.com",
        env={"QUARKUS_PROFILE": "gcp"},
        max_instances=4,
        concurrency=40,
    )
    kwargs.update(overrides)
    return ContainerService(name, **kwargs)


@pulumi.runtime.test
def test_min_instances_is_always_zero():
    # The free-tier tenet depends on this: no idle cost when nobody is playing.
    return build("scale").service.template.apply(
        lambda t: _assert(t["scaling"]["min_instance_count"] == 0, t["scaling"])
    )


@pulumi.runtime.test
def test_max_instances_and_concurrency_bound_spend():
    # GCP has no request-rate throttle; max_instances x concurrency IS the spend bound.
    svc = build("bound", max_instances=7, concurrency=25)

    def check(t):
        assert t["scaling"]["max_instance_count"] == 7
        assert t["max_instance_request_concurrency"] == 25

    return svc.service.template.apply(check)


@pulumi.runtime.test
def test_runs_as_the_supplied_service_account():
    # Never the default compute SA. @spec CP-GCP-003
    svc = build("sa", service_account_email="ir-run@test-project.iam.gserviceaccount.com")
    return svc.service.template.apply(
        lambda t: _assert(t["service_account"].startswith("ir-run@"), t["service_account"])
    )


@pulumi.runtime.test
def test_public_grants_allusers_invoker():
    svc = build("pub", public=True)
    assert svc.public_invoker is not None

    def check(args):
        member, role = args
        assert member == "allUsers"
        assert role == "roles/run.invoker"

    return pulumi.Output.all(svc.public_invoker.member, svc.public_invoker.role).apply(check)


def test_prod_also_gets_the_invoker():
    # Closes gap G1 and deletes grant-prod-invoker.sh: prod is no longer special.
    assert build("prodinv", public=True, deletion_protection=True).public_invoker is not None


def test_private_omits_the_invoker_entirely():
    assert build("priv", public=False).public_invoker is None


@pulumi.runtime.test
def test_env_is_rendered_as_sorted_name_value_pairs():
    # Unsorted dict ordering would produce spurious diffs on every preview.
    svc = build("env", env={"Z": "1", "A": "2", "M": "3"})

    def check(t):
        envs = t["containers"][0]["envs"]
        assert [e["name"] for e in envs] == ["A", "M", "Z"]
        assert {e["name"]: e["value"] for e in envs} == {"A": "2", "M": "3", "Z": "1"}

    return svc.service.template.apply(check)


@pulumi.runtime.test
def test_no_aws_credentials_are_injected():
    # The GCP project must end with zero long-lived credentials. @spec CP-GCP-089
    svc = build("noaws", env={"COACH_AI_PROVIDER": "vertex", "IMAGE_AI_PROVIDER": "vertex"})
    return svc.service.template.apply(
        lambda t: _assert(
            not [e for e in t["containers"][0]["envs"] if "AWS" in e["name"]],
            "AWS env var present",
        )
    )


@pulumi.runtime.test
def test_url_is_exposed_from_the_service():
    return build("url").url.apply(lambda u: _assert(u.startswith("https://"), u))


@pulumi.runtime.test
def test_deletion_protection_is_opt_in():
    off = build("dp1").service.deletion_protection
    on = build("dp2", deletion_protection=True).service.deletion_protection

    def check(args):
        assert args[0] is False
        assert args[1] is True

    return pulumi.Output.all(off, on).apply(check)


def _assert(condition: bool, detail: object = "") -> None:
    assert condition, detail
