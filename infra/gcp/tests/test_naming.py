"""@spec CP-PUL-021"""

from __future__ import annotations

import pytest

from components import naming


class TestStackNameForBranch:
    def test_sanitises_and_lowercases(self):
        assert naming.stack_name_for_branch("RCG-Parity") == "rcg-parity"

    def test_slashes_and_dots_become_hyphens(self):
        assert naming.stack_name_for_branch("feat/a.b") == "feat-a-b"

    def test_strips_disallowed_characters(self):
        assert naming.stack_name_for_branch("rcg_foo!bar") == "rcgfoobar"

    def test_caps_at_the_stack_name_budget(self):
        stack = naming.stack_name_for_branch("rcg-" + "x" * 60)
        assert len(stack) == naming.STACK_NAME_MAX
        site = naming.hosting_site_id(stack, "sudoku-eo-2026", "a1b2")
        assert len(site) <= naming.FIREBASE_SITE_ID_MAX

    def test_never_ends_with_a_hyphen(self):
        # Truncation can land exactly on a hyphen; a Firebase site_id may not end with one.
        assert not naming.stack_name_for_branch("rcg-abcdefgh-zzz").endswith("-")

    def test_rejects_a_branch_that_sanitises_to_nothing(self):
        with pytest.raises(ValueError, match="empty stack name"):
            naming.stack_name_for_branch("___")


class TestSuffixesAndLabels:
    def test_prod_has_no_suffix(self):
        assert naming.suffix("prod") == ""
        assert naming.is_prod("prod")

    def test_other_stacks_are_suffixed(self):
        assert naming.suffix("rcg-parity") == "-rcg-parity"

    def test_environment_label_is_prod_for_prod(self):
        assert naming.environment_label("prod") == "prod"

    def test_labels_declare_pulumi_not_terraform(self):
        assert naming.default_labels("prod")["managed_by"] == "pulumi"

    def test_label_values_are_lowercase_and_bounded(self):
        label = naming.environment_label("RCG-Some/Weird.Branch")
        assert label == label.lower()
        assert len(label) <= 63

    def test_there_is_no_is_rc_helper(self):
        # infra/gcp/main.tf's `is_rc` never matched (GCP stacks start "rcg-", not "rc-"), which
        # silently pinned coach_bedrock_api_mode to "invoke". Do not reintroduce it.
        assert not hasattr(naming, "is_rc")


class TestFirestoreAndHosting:
    def test_prod_uses_the_default_database(self):
        assert naming.firestore_database_id("prod") == "(default)"

    def test_other_stacks_get_a_named_database(self):
        assert naming.firestore_database_id("rcg-parity") == "sudoku-rcg-parity"

    def test_site_id_over_the_limit_is_rejected(self):
        with pytest.raises(ValueError, match="over the"):
            naming.hosting_site_id("a" * 40, "sudoku-eo-2026", "a1b2")


class TestHostingSiteUniqueness:
    """Firebase tombstones a deleted site's name, so an id must never be reused.

    A torn-down stack recreated under the same name fails at the Hosting site and leaves a
    partial environment behind. Non-production ids therefore carry a suffix sourced from Pulumi
    state — fixed for a stack's life, fresh on recreation — rather than from git, which collides
    across branches sharing a base and changes under a rebase.
    """

    def test_production_id_is_the_project_id_and_carries_no_suffix(self):
        assert naming.hosting_site_id("prod", "sudoku-eo-2026") == "sudoku-eo-2026"

    def test_non_prod_ids_name_the_stack_and_the_suffix(self):
        assert (
            naming.hosting_site_id("rcg-p6v2", "sudoku-eo-2026", "a1b2")
            == "sudoku-dev-rcg-p6v2-a1b2"
        )

    def test_non_prod_id_is_independent_of_the_project_id(self):
        # The project id is what consumed the character budget; dropping it made room for the
        # suffix, and it identified nothing — every stack lives in the same project.
        for project in ("sudoku-eo-2026", "some-other-project"):
            assert naming.hosting_site_id("rcg-x", project, "a1b2") == "sudoku-dev-rcg-x-a1b2"

    def test_two_stacks_with_different_suffixes_never_collide(self):
        assert naming.hosting_site_id("rcg-x", "p", "a1b2") != naming.hosting_site_id(
            "rcg-x", "p", "c3d4"
        )

    def test_a_non_prod_id_requires_a_suffix(self):
        # Silently omitting it would produce a reusable name and reintroduce the tombstone.
        with pytest.raises(ValueError, match="unique_suffix"):
            naming.hosting_site_id("rcg-x", "sudoku-eo-2026")

    def test_the_longest_allowed_stack_still_fits_exactly(self):
        stack = "a" * naming.STACK_NAME_MAX
        site = naming.hosting_site_id(stack, "sudoku-eo-2026", "a" * 4)
        assert len(site) == naming.FIREBASE_SITE_ID_MAX, (site, len(site))

    def test_the_budget_matches_the_id_shape(self):
        # 30 - len("sudoku-dev") - 1 - 4 - 1 = 14
        assert naming.STACK_NAME_MAX == 14


class TestCors:
    def test_prod_serves_the_custom_domain(self):
        origins = naming.cors_allowed_origins("prod", "sudoku-eo", "sudoku.gcp.edoatley.co.uk")
        assert origins == "https://sudoku.gcp.edoatley.co.uk,http://localhost:5173"

    def test_non_prod_serves_its_own_hosting_origin(self):
        origins = naming.cors_allowed_origins("rcg-x", "sudoku-eo", unique_suffix="a1b2")
        assert origins == "https://sudoku-dev-rcg-x-a1b2.web.app,http://localhost:5173"

    def test_prod_without_a_custom_domain_fails_loudly(self):
        with pytest.raises(ValueError, match="custom_domain"):
            naming.cors_allowed_origins("prod", "sudoku-eo")

    def test_localhost_is_always_allowed(self):
        for stack, domain, suffix in (("prod", "d.example.com", None), ("rcg-x", None, "a1b2")):
            allowed = naming.cors_allowed_origins(stack, "sudoku-eo", domain, unique_suffix=suffix)
            assert naming.LOCAL_DEV_ORIGIN in allowed
