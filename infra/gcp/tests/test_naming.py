"""@spec CP-PUL-021"""

from __future__ import annotations

import pytest

from components import naming


class TestStackNameForBranch:
    def test_sanitises_and_lowercases(self):
        assert naming.stack_name_for_branch("RCG-Parity", "sudoku-eo") == "rcg-parity"

    def test_slashes_and_dots_become_hyphens(self):
        assert naming.stack_name_for_branch("feat/a.b", "sudoku-eo") == "feat-a-b"

    def test_strips_disallowed_characters(self):
        assert naming.stack_name_for_branch("rcg_foo!bar", "sudoku-eo") == "rcgfoobar"

    def test_caps_at_the_firebase_site_id_limit(self):
        project = "sudoku-eo-2026"  # 14 chars -> budget 30-14-1 = 15
        stack = naming.stack_name_for_branch("rcg-" + "x" * 60, project)
        assert len(stack) == 15
        assert len(naming.hosting_site_id(stack, project)) <= naming.FIREBASE_SITE_ID_MAX

    def test_never_ends_with_a_hyphen(self):
        # Truncation can land exactly on a hyphen; a Firebase site_id may not end with one.
        project = "sudoku-eo-2026"
        assert not naming.stack_name_for_branch("rcg-abcdefghijk-zzz", project).endswith("-")

    def test_rejects_a_project_id_leaving_no_room(self):
        with pytest.raises(ValueError, match="no room"):
            naming.stack_name_for_branch("rcg-x", "a" * 30)

    def test_rejects_a_branch_that_sanitises_to_nothing(self):
        with pytest.raises(ValueError, match="empty stack name"):
            naming.stack_name_for_branch("___", "sudoku-eo")


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
            naming.hosting_site_id("a" * 40, "sudoku-eo-2026")


class TestCors:
    def test_prod_serves_the_custom_domain(self):
        origins = naming.cors_allowed_origins("prod", "sudoku-eo", "sudoku.gcp.edoatley.co.uk")
        assert origins == "https://sudoku.gcp.edoatley.co.uk,http://localhost:5173"

    def test_non_prod_serves_its_own_hosting_origin(self):
        origins = naming.cors_allowed_origins("rcg-x", "sudoku-eo")
        assert origins == "https://sudoku-eo-rcg-x.web.app,http://localhost:5173"

    def test_prod_without_a_custom_domain_fails_loudly(self):
        with pytest.raises(ValueError, match="custom_domain"):
            naming.cors_allowed_origins("prod", "sudoku-eo")

    def test_localhost_is_always_allowed(self):
        for stack in ("prod", "rcg-x"):
            domain = "d.example.com" if stack == "prod" else None
            allowed = naming.cors_allowed_origins(stack, "sudoku-eo", domain)
            assert naming.LOCAL_DEV_ORIGIN in allowed
