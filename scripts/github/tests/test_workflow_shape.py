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


class TestContextAvailability:
    """`inputs` exists only in reusable and dispatch-only workflows.

    Referencing it from a workflow that can also be triggered by `push` fails the entire run at
    validation time — no jobs, no logs, and nothing in the API that names the cause. The
    always-defined form is `github.event.inputs`.
    """

    def test_push_triggered_workflows_use_github_event_inputs(self):
        for name in ("ci-deploy.yml", "ci.yml"):
            src = (WORKFLOWS / name).read_text()
            assert "${{ inputs." not in src, (
                f"{name} is push-triggered and must use github.event.inputs, not the inputs context"
            )

    def test_reusable_only_workflows_may_use_inputs(self):
        # deploy-gcp-pulumi.yml has no push trigger, so the inputs context is valid there.
        assert list(_triggers("deploy-gcp-pulumi.yml")) == ["workflow_call"]
        assert "${{ inputs.stack }}" in (WORKFLOWS / "deploy-gcp-pulumi.yml").read_text()


class TestCallerGrantsWhatCalleesRequest:
    """A called workflow may not request more permission than its caller grants.

    An omitted scope means `none`, so a callee job declaring `pull-requests: write` under a caller
    that lists a permissions block without it fails the entire run before any job starts — no
    jobs, no logs, no API message, and actionlint does not check it either. The only way back from
    that symptom is to compare the two blocks by hand, so compare them here instead.
    """

    RANK = {"none": 0, "read": 1, "write": 2}

    def _granted(self, name: str) -> dict[str, str]:
        return yaml.safe_load((WORKFLOWS / name).read_text()).get("permissions") or {}

    def _requested(self, name: str) -> dict[str, str]:
        doc = yaml.safe_load((WORKFLOWS / name).read_text())
        want = dict(doc.get("permissions") or {})
        for job in doc["jobs"].values():
            for scope, level in (job.get("permissions") or {}).items():
                if self.RANK[level] > self.RANK.get(want.get(scope, "none"), 0):
                    want[scope] = level
        return want

    @pytest.mark.parametrize(
        "caller,callee",
        [
            ("ci-deploy.yml", "ci.yml"),
            ("ci-deploy.yml", "deploy-gcp-pulumi.yml"),
            ("ci-deploy.yml", "smoke-tests.yml"),
        ],
    )
    def test_no_callee_job_escalates_beyond_its_caller(self, caller, callee):
        granted = self._granted(caller)
        assert granted, f"{caller} must declare a permissions block for this check to mean anything"
        escalations = {
            scope: level
            for scope, level in self._requested(callee).items()
            if self.RANK[level] > self.RANK.get(granted.get(scope, "none"), 0)
        }
        assert not escalations, (
            f"{callee} requests {escalations} which {caller} does not grant — "
            "the run will fail at startup with no diagnostic"
        )


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
