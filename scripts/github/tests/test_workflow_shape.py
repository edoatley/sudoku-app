"""Cross-workflow invariants for the deploy entry point.

`ci-deploy.yml` gates every deploying branch and `ci.yml` checks everything else. That split only
holds if the two push filters partition the branch space: a branch matching both burns CI minutes
twice, and a branch matching neither ships with no checks at all. Neither shows up as a failure —
the first looks merely slow, the second looks fast.

@spec CP-CD-001, CP-CD-002, CP-PUL-080
"""

from __future__ import annotations

import fnmatch
import pathlib

import pytest
import yaml

WORKFLOWS = pathlib.Path(__file__).resolve().parents[3] / ".github" / "workflows"


def _triggers(name: str) -> dict:
    # PyYAML parses a bare `on:` key as the boolean True.
    doc = yaml.safe_load((WORKFLOWS / name).read_text())
    return doc.get(True) or doc.get("on")


def _push_branches(name: str) -> list[str]:
    return _triggers(name)["push"]["branches"]


def matches(branch: str, patterns: list[str]) -> bool:
    """Whether a branch matches a GitHub `branches:` filter.

    GitHub resolves mixed positive and negative patterns by **last match wins**, not by "any
    negative excludes" — a positive pattern after a negative one re-includes the ref. Getting that
    backwards would make this whole module assert the wrong thing.
    """
    verdict = False
    for pattern in patterns:
        negated = pattern.startswith("!")
        glob = pattern[1:] if negated else pattern
        # GitHub's `**` spans separators; fnmatch's `*` already does, so they coincide here.
        if fnmatch.fnmatch(branch, glob.replace("**", "*")):
            verdict = not negated
    return verdict


DEPLOYING = ["main", "rc-parity", "rcg-parity", "rc-x", "rcg-x"]
NON_DEPLOYING = ["feat/thing", "fix/bug", "plan/phase-8", "dependabot/npm/x", "docs/readme"]


class TestMatcher:
    """The matcher itself, since every assertion below rests on it."""

    def test_last_match_wins_over_any_negative(self):
        assert matches("main", ["**", "!main"]) is False
        assert matches("main", ["!main", "**"]) is True

    def test_a_plain_pattern_matches_only_itself(self):
        assert matches("main", ["main"]) is True
        assert matches("mainline", ["main"]) is False


class TestFiltersPartitionTheBranchSpace:
    @pytest.mark.parametrize("branch", DEPLOYING + NON_DEPLOYING)
    def test_every_branch_matches_exactly_one_workflow(self, branch):
        ci = matches(branch, _push_branches("ci.yml"))
        deploy = matches(branch, _push_branches("ci-deploy.yml"))
        assert ci != deploy, (
            f"{branch!r}: ci.yml={ci}, ci-deploy.yml={deploy} — "
            "matching both runs the suite twice; matching neither ships it unchecked"
        )

    @pytest.mark.parametrize("branch", DEPLOYING)
    def test_deploying_branches_are_gated_by_ci_deploy(self, branch):
        assert matches(branch, _push_branches("ci-deploy.yml"))

    @pytest.mark.parametrize("branch", NON_DEPLOYING)
    def test_everything_else_is_checked_by_ci(self, branch):
        assert matches(branch, _push_branches("ci.yml"))


class TestGatesAreReusedNotCopied:
    def test_ci_deploy_calls_ci_rather_than_redefining_gates(self):
        jobs = yaml.safe_load((WORKFLOWS / "ci-deploy.yml").read_text())["jobs"]
        assert jobs["gates"].get("uses") == "./.github/workflows/ci.yml"

    def test_ci_is_callable(self):
        assert "workflow_call" in _triggers("ci.yml")

    def test_no_test_job_is_defined_in_both(self):
        # Two copies of a test job drift, and the thinner copy is the one that gates the deploy.
        ci = set(yaml.safe_load((WORKFLOWS / "ci.yml").read_text())["jobs"])
        deploy = set(yaml.safe_load((WORKFLOWS / "ci-deploy.yml").read_text())["jobs"])
        assert not (ci & deploy), ci & deploy


class TestGcpHasNoEntryPointOfItsOwn:
    """@spec CP-PUL-080"""

    def test_the_gcp_deploy_is_reusable_only(self):
        # A push or dispatch trigger here would be a second way to start a GCP deploy, and a
        # second place the target could be decided.
        assert list(_triggers("deploy-gcp-pulumi.yml")) == ["workflow_call"]

    def test_ci_deploy_invokes_it(self):
        jobs = yaml.safe_load((WORKFLOWS / "ci-deploy.yml").read_text())["jobs"]
        assert jobs["deploy-gcp"].get("uses") == "./.github/workflows/deploy-gcp-pulumi.yml"

    def test_the_terraform_gcp_workflow_keeps_no_push_trigger(self):
        # It targets the incumbent project and is dispatch-only until Phase 9 deletes it.
        assert "push" not in _triggers("deploy-gcp.yml")
