"""Bootstrap-stack components.

@spec CP-PUL-001, CP-PUL-002, CP-PUL-003, CP-PUL-010, CP-PUL-011, CP-PUL-040, CP-PUL-041
"""

from __future__ import annotations

import pulumi
import pytest

from components.artifact_registry import ArtifactRegistry
from components.project_foundation import REQUIRED_APIS, ProjectFoundation
from components.service_identity import ServiceIdentity
from components.workload_identity import WorkloadIdentityFederation


def _apis_for(resource_kind: str) -> set[str]:
    """APIs a given resource kind needs enabled before it can be created."""
    return {
        "service_account": {"iam.googleapis.com"},
        "wif": {"iam.googleapis.com", "iamcredentials.googleapis.com", "sts.googleapis.com"},
        "firestore": {"firestore.googleapis.com"},
        "cloud_run": {"run.googleapis.com"},
        "artifact_registry": {"artifactregistry.googleapis.com"},
        "kms": {"cloudkms.googleapis.com"},
        "dns": {"dns.googleapis.com"},
        "budget": {
            "billingbudgets.googleapis.com",
            "pubsub.googleapis.com",
            "monitoring.googleapis.com",  # required to read the channel back
        },
        "identity_platform": {"identitytoolkit.googleapis.com"},
        "hosting": {"firebase.googleapis.com", "firebasehosting.googleapis.com"},
        "vertex": {"aiplatform.googleapis.com"},
    }[resource_kind]


def foundation(name: str) -> ProjectFoundation:
    return ProjectFoundation(
        name,
        project_id="sudoku-eo-2026",
        project_name="Sudoku",
        billing_account="010F10-A51056-E8EC40",
        state_bucket_name="sudoku-pulumi-state-eo",
        region="us-central1",
    )


class TestApiSurface:
    def test_secret_manager_is_not_enabled(self):
        # After de-Bedrocking there is no secret to store; the project ends with zero
        # long-lived credentials. @spec CP-GCP-089
        assert not [a for a in REQUIRED_APIS if "secretmanager" in a]

    def test_vertex_ai_is_enabled(self):
        # Both the coach and image recognition need it.
        assert "aiplatform.googleapis.com" in REQUIRED_APIS

    def test_kms_is_enabled_for_the_app_stacks_secrets_provider(self):
        assert "cloudkms.googleapis.com" in REQUIRED_APIS

    def test_federation_apis_are_enabled(self):
        # Without all three, WIF and every serviceaccount IAM binding fail with SERVICE_DISABLED.
        # iam.googleapis.com is NOT on by default in a new project — its omission broke the first
        # bootstrap apply.
        assert {
            "iam.googleapis.com",
            "iamcredentials.googleapis.com",
            "sts.googleapis.com",
        } <= set(REQUIRED_APIS)

    def test_no_duplicate_apis(self):
        assert len(REQUIRED_APIS) == len(set(REQUIRED_APIS))

    @pytest.mark.parametrize(
        "kind",
        [
            "service_account",
            "wif",
            "firestore",
            "cloud_run",
            "artifact_registry",
            "kms",
            "dns",
            "budget",
            "identity_platform",
            "hosting",
            "vertex",
        ],
    )
    def test_every_resource_kind_has_its_api_enabled(self, kind):
        # A missing enablement does not fail at plan time — only mid-apply, with a
        # SERVICE_DISABLED that reads like a permissions problem. iam.googleapis.com was missing
        # and broke the first real bootstrap.
        missing = _apis_for(kind) - set(REQUIRED_APIS)
        assert not missing, f"{kind} needs {missing}, which REQUIRED_APIS does not enable"


class TestProjectFoundation:
    @pulumi.runtime.test
    def test_no_default_vpc_is_created(self):
        return foundation("novpc").project.auto_create_network.apply(
            lambda v: _assert(v is False, v)
        )

    @pulumi.runtime.test
    def test_project_deletion_is_prevented(self):
        return foundation("del").project.deletion_policy.apply(lambda v: _assert(v == "PREVENT", v))

    @pulumi.runtime.test
    def test_state_bucket_is_versioned_and_private(self):
        b = foundation("bucket").state_bucket

        def check(args):
            versioning, ubla, pap = args
            assert versioning["enabled"] is True, versioning
            assert ubla is True
            assert pap == "enforced"

        return pulumi.Output.all(
            b.versioning, b.uniform_bucket_level_access, b.public_access_prevention
        ).apply(check)

    @pulumi.runtime.test
    def test_apis_survive_a_stack_destroy(self):
        # Destroying bootstrap must not disable APIs underneath a live app stack.
        f = foundation("apis")
        return pulumi.Output.all(*[s.disable_on_destroy for s in f.services]).apply(
            lambda flags: _assert(all(v is False for v in flags), flags)
        )

    @pulumi.runtime.test
    def test_kms_key_uri_is_a_usable_secrets_provider(self):
        # This asserted "gcp-kms://" and so confirmed the bug rather than catching it: Pulumi
        # only accepts `gcpkms://` and rejects anything else with "unknown secrets provider
        # type". The assertion is now pinned to the scheme Pulumi actually parses.
        return foundation("kms").kms_key_uri.apply(
            lambda u: _assert(u.startswith("gcpkms://projects/"), u)
        )

    @pulumi.runtime.test
    def test_kms_key_uri_uses_a_scheme_pulumi_accepts(self):
        valid = ("default", "passphrase", "awskms", "azurekeyvault", "gcpkms", "hashivault")
        return foundation("kms-scheme").kms_key_uri.apply(
            lambda u: _assert(u.split("://")[0] in valid, u)
        )

    @pulumi.runtime.test
    def test_state_bucket_url_is_a_gs_uri(self):
        return foundation("url").state_bucket_url.apply(
            lambda u: _assert(u == "gs://sudoku-pulumi-state-eo", u)
        )


class TestArtifactRegistry:
    @pulumi.runtime.test
    def test_cleanup_policies_keep_recent_and_delete_untagged(self):
        ar = ArtifactRegistry("ar", project="p", region="us-central1")

        def check(policies):
            by_id = {p["id"]: p for p in policies}
            assert by_id["keep-10-recent"]["most_recent_versions"]["keep_count"] == 10
            assert by_id["delete-older-untagged"]["condition"]["tag_state"] == "UNTAGGED"
            assert by_id["delete-older-untagged"]["action"] == "DELETE"

        return ar.repository.cleanup_policies.apply(check)

    @pulumi.runtime.test
    def test_cleanup_actually_deletes_rather_than_reporting(self):
        ar = ArtifactRegistry("ar-dry", project="p", region="us-central1")
        return ar.repository.cleanup_policy_dry_run.apply(lambda v: _assert(v is False, v))

    @pulumi.runtime.test
    def test_repository_url_is_a_pushable_path(self):
        ar = ArtifactRegistry("ar-url", project="test-project", region="us-central1")
        return ar.repository_url.apply(
            lambda u: _assert(u == "us-central1-docker.pkg.dev/test-project/sudoku-backend", u)
        )


class TestServiceIdentity:
    @pulumi.runtime.test
    def test_member_is_prefixed_for_iam(self):
        si = ServiceIdentity(
            "si", project="p", account_id="sudoku-run", display_name="Sudoku backend"
        )
        return si.member.apply(lambda m: _assert(m.startswith("serviceAccount:sudoku-run@"), m))

    def test_one_binding_per_role(self):
        si = ServiceIdentity(
            "si-roles",
            project="p",
            account_id="sudoku-run",
            display_name="Sudoku backend",
            project_roles=["roles/datastore.user", "roles/aiplatform.user"],
        )
        assert len(si.role_bindings) == 2

    def test_no_roles_means_no_bindings(self):
        si = ServiceIdentity("si-none", project="p", account_id="x", display_name="X")
        assert si.role_bindings == []


class TestWorkloadIdentityFederation:
    @pulumi.runtime.test
    def test_provider_is_restricted_to_one_repository(self):
        # THE security boundary. Without it any fork could mint a token and impersonate the
        # deploy SA — the classic confused deputy.
        wif = WorkloadIdentityFederation(
            "wif", project="p", github_repo="edoatley/sudoku-app", service_account_name="sa"
        )
        return wif.provider.attribute_condition.apply(
            lambda c: _assert(c == "assertion.repository == 'edoatley/sudoku-app'", c)
        )

    @pulumi.runtime.test
    def test_principal_set_is_built_from_the_server_assigned_pool_name(self):
        wif = WorkloadIdentityFederation(
            "wif-ps", project="p", github_repo="edoatley/sudoku-app", service_account_name="sa"
        )
        return wif.principal_set.apply(
            lambda ps: _assert(
                ps.startswith("principalSet://iam.googleapis.com/projects/")
                and ps.endswith("/attribute.repository/edoatley/sudoku-app"),
                ps,
            )
        )

    @pulumi.runtime.test
    def test_impersonation_uses_the_workload_identity_user_role(self):
        wif = WorkloadIdentityFederation(
            "wif-role", project="p", github_repo="edoatley/sudoku-app", service_account_name="sa"
        )
        return wif.impersonation.role.apply(
            lambda r: _assert(r == "roles/iam.workloadIdentityUser", r)
        )


def _assert(condition, detail="") -> None:
    assert condition, detail
