"""Invariants of the GCP deploy and teardown workflows.

These encode two failures that cost a CI round-trip each and whose error messages pointed
somewhere other than the cause. Both are shell-level, so no other layer can catch them.

@spec CP-PUL-021, CP-PUL-041
"""

from __future__ import annotations

import pathlib

import pytest
import yaml

WORKFLOWS = pathlib.Path(__file__).resolve().parents[3] / ".github" / "workflows"
DEPLOY = WORKFLOWS / "deploy-gcp-pulumi.yml"
TEARDOWN = WORKFLOWS / "teardown-gcp-pulumi.yml"


@pytest.fixture(params=[DEPLOY, TEARDOWN], ids=["deploy", "teardown"])
def workflow(request) -> str:
    return request.param.read_text()


class TestStackSecretsProvider:
    """A self-managed backend stores the secrets-provider declaration in Pulumi.<stack>.yaml.

    Ephemeral stacks commit no such file, so without this every re-run on a fresh checkout falls
    back to the passphrase provider and fails naming PULUMI_CONFIG_PASSPHRASE — an error that
    reads as a missing secret rather than a missing file.
    """

    def test_the_stack_config_file_is_written(self, workflow):
        assert "printf 'secretsprovider: %s\\n'" in workflow

    def test_an_existing_config_file_is_never_overwritten(self, workflow):
        # Dispatching against prod would otherwise discard the committed OAuth client and custom
        # domain — a destructive edit to production configuration from a routine deploy.
        assert 'if [ -f "Pulumi.${STACK}.yaml" ]' in workflow


class TestStackCreation:
    """`pulumi stack init` ignores the secretsprovider key in an existing config file.

    The declaration covers `stack select` on later runs; only the flag covers `stack init` on the
    first. Dropping either one breaks exactly one of the two paths, and the surviving path hides
    it — the first run passes and the second fails, or the reverse.
    """

    def test_init_passes_the_secrets_provider_flag(self):
        lines = DEPLOY.read_text().splitlines()
        commands = [
            i
            for i, line in enumerate(lines)
            if "pulumi stack init" in line and not line.lstrip().startswith("#")
        ]
        assert commands, "no `pulumi stack init` command found"
        for i in commands:
            following = " ".join(lines[i : i + 3])
            assert "--secrets-provider" in following, following

    def test_teardown_only_selects(self):
        # Teardown has nothing to create, so it needs no flag — but if it ever inits, it does.
        assert "stack init" not in TEARDOWN.read_text()


class TestCredentialConvention:
    """Consolidated GCP credentials after retiring `sudoku-app-eo` and `_NEXT` suffixes.

    `GCP_PROJECT_ID` is a repository variable (not a secret), `GCP_WIF_PROVIDER` and
    `GCP_DEPLOY_SA_EMAIL` are the un-suffixed WIF secrets, and `GCP_SMOKE_TEST_USER_PASSWORD` keeps
    the Identity Platform smoke password isolated from AWS Cognito's `SMOKE_TEST_USER_PASSWORD`.
    """

    @pytest.mark.parametrize("secret", ["GCP_WIF_PROVIDER", "GCP_DEPLOY_SA_EMAIL"])
    def test_the_consolidated_wif_secrets_are_used(self, workflow, secret):
        assert f"secrets.{secret} }}}}" in workflow

    def test_the_project_id_is_a_variable_not_a_secret(self, workflow):
        assert "vars.GCP_PROJECT_ID }}" in workflow
        assert "secrets.GCP_PROJECT_ID" not in workflow

    def test_smoke_uses_gcp_scoped_password_secret(self):
        deploy_src = DEPLOY.read_text()
        assert "secrets.GCP_SMOKE_TEST_USER_PASSWORD }}" in deploy_src
        assert "secrets.SMOKE_TEST_USER_PASSWORD }}" not in deploy_src

    def test_no_temporary_next_suffixes_remain(self, workflow):
        assert "_NEXT" not in workflow
        assert "GCP_PULUMI_PROJECT_ID" not in workflow

    def test_authorized_domains_are_synced_after_pulumi(self, workflow):
        assert "sync_gcp_authorized_domains.py" in workflow
        assert "token_format: access_token" in workflow


class TestDestructiveFlags:
    def test_teardown_never_forces(self):
        # `pulumi stack rm --force` deletes the stack while leaving its cloud resources running,
        # with no state left to find them by. Comments explaining that are fine; commands are not.
        commands = [
            line
            for line in TEARDOWN.read_text().splitlines()
            if "--force" in line and not line.lstrip().startswith("#")
        ]
        assert not commands, commands

    def test_teardown_refuses_production(self):
        assert '"${STACK}" = "prod"' in TEARDOWN.read_text()


def test_both_workflows_are_valid_yaml():
    for path in (DEPLOY, TEARDOWN):
        assert yaml.safe_load(path.read_text())["jobs"], path
