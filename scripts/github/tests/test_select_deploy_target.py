"""Which cloud a deployment goes to.

The failure mode here is deploying the wrong cloud, and both deploys look healthy doing it — so
every branch of the precedence is asserted rather than reviewed. Order matters: a dispatch input
beats the branch convention, and the branch convention beats the repository variable.

@spec CP-CD-001, CP-CD-002, CP-CD-003, CP-CD-004
"""

from __future__ import annotations

import pytest

from select_deploy_target import AWS, GCP, select_deploy_target


def select(event="push", ref="main", dispatch="", var=""):
    return select_deploy_target(
        event_name=event, ref_name=ref, dispatch_target=dispatch, deploy_target_var=var
    )


class TestDispatchWins:
    """@spec CP-CD-003"""

    @pytest.mark.parametrize(
        "target,expected",
        [("aws", {AWS}), ("gcp", {GCP}), ("both", {AWS, GCP})],
    )
    def test_an_explicit_dispatch_target_overrides_everything(self, target, expected):
        # Overrides the variable AND the branch convention — this is the manual escape hatch.
        got = select(event="workflow_dispatch", ref="rcg-x", dispatch=target, var="aws")
        assert got == expected

    def test_auto_falls_through_to_the_branch_convention(self):
        assert select(event="workflow_dispatch", ref="rc-x", dispatch="auto", var="gcp") == {AWS}

    def test_an_empty_input_is_treated_as_auto(self):
        # A dispatch with the input left at its default must not be read as "no target".
        assert select(event="workflow_dispatch", ref="rcg-x", dispatch="", var="aws") == {GCP}


class TestBranchConvention:
    """The branch convention is absolute and never consults the variable. @spec CP-CD-002"""

    @pytest.mark.parametrize("var", ["", "aws", "gcp", "both"])
    def test_rc_branches_always_go_to_aws(self, var):
        assert select(ref="rc-parity", var=var) == {AWS}

    @pytest.mark.parametrize("var", ["", "aws", "gcp", "both"])
    def test_rcg_branches_always_go_to_gcp(self, var):
        assert select(ref="rcg-parity", var=var) == {GCP}

    def test_rcg_is_not_matched_as_an_rc_branch(self):
        # "rcg-" starts with "rc", so a naive prefix test sends every GCP environment to AWS.
        # This is the same class of bug as main.tf's is_rc, which never matched at all.
        assert select(ref="rcg-x") == {GCP}


class TestMainUsesTheVariable:
    """@spec CP-CD-001"""

    def test_unset_defaults_to_aws(self):
        assert select(ref="main", var="") == {AWS}

    @pytest.mark.parametrize("var,expected", [("aws", {AWS}), ("gcp", {GCP}), ("both", {AWS, GCP})])
    def test_the_variable_selects_the_target(self, var, expected):
        assert select(ref="main", var=var) == expected

    def test_the_variable_is_case_and_space_insensitive(self):
        # A repository variable is typed by hand in a web form.
        assert select(ref="main", var=" GCP ") == {GCP}


class TestFailLoud:
    @pytest.mark.parametrize("var", ["awz", "AWS ,GCP", "none", "gcp-only"])
    def test_an_unrecognised_variable_is_rejected(self, var):
        # Defaulting a typo to aws would deploy the wrong cloud and look entirely healthy.
        with pytest.raises(ValueError, match="DEPLOY_TARGET"):
            select(ref="main", var=var)

    def test_an_unrecognised_dispatch_target_is_rejected(self):
        with pytest.raises(ValueError, match="target"):
            select(event="workflow_dispatch", ref="main", dispatch="both-clouds")

    def test_a_branch_that_deploys_nowhere_is_rejected(self):
        # ci-deploy.yml's push filter should never let this through; if it does, say so rather
        # than silently deploying main's default.
        with pytest.raises(ValueError, match="does not deploy"):
            select(ref="feat/some-branch")


class TestBothIsIndependent:
    """@spec CP-CD-004"""

    def test_both_yields_two_targets_not_a_third_mode(self):
        # Each cloud deploys to its own hostname; "both" is not a failover or a merge.
        assert select(ref="main", var="both") == {AWS, GCP}
