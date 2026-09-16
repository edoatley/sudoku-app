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


class TestCredentialConvention:
    """The un-suffixed secrets serve AWS Cognito and the incumbent GCP project.

    Overwriting one breaks a live system that has nothing to do with this workflow, so the new
    project reads only `_NEXT` values until the Phase 8 rename.
    """

    @pytest.mark.parametrize("secret", ["GCP_WIF_PROVIDER_NEXT", "GCP_DEPLOY_SA_EMAIL_NEXT"])
    def test_the_next_secrets_are_used(self, workflow, secret):
        assert secret in workflow

    @pytest.mark.parametrize(
        "secret", ["GCP_WIF_PROVIDER", "GCP_DEPLOY_SA_EMAIL", "GCP_PROJECT_ID"]
    )
    def test_the_incumbent_secrets_are_never_read(self, workflow, secret):
        assert f"secrets.{secret} }}}}" not in workflow, secret


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
