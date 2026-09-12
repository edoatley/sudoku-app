"""Structural guardrail, not a behaviour test.

An authoritative IAM resource silently deletes every binding at its scope that it does not
list. A project-level ``IAMPolicy`` removes *every* existing binding, including the operator's
own Owner grant — recoverable only via an organization, which this account does not have.
``IAMBinding`` is milder but still drops every member of that role it omits.

Only the additive per-member form (``IAMMember``) is permitted anywhere in this codebase.

The check parses the AST rather than matching text, so prose in docstrings and comments — like
the paragraph above, which names both forbidden types — cannot trip it, and a construction
cannot hide from it by being split across lines. @spec CP-PUL-013
"""

from __future__ import annotations

import ast
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCAN_DIRS = ("components", "bootstrap", "app")

# pulumi-gcp is inconsistent about casing: gcp.projects.IAMPolicy but
# gcp.cloudrunv2.ServiceIamPolicy. Match case-insensitively, or the lowercase spellings walk
# straight through the check.
FORBIDDEN_SUFFIXES = ("iambinding", "iampolicy")
ALLOWED_SUFFIX = "iammember"


def _is_forbidden(name: str) -> bool:
    return name.lower().endswith(FORBIDDEN_SUFFIXES)


def _python_sources() -> list[pathlib.Path]:
    files: list[pathlib.Path] = []
    for name in SCAN_DIRS:
        directory = ROOT / name
        if directory.exists():
            files.extend(sorted(directory.rglob("*.py")))
    return files


def _called_names(tree: ast.AST) -> list[tuple[str, int]]:
    """Every attribute/name actually *called*, e.g. gcp.projects.IAMMember(...) -> IAMMember."""
    found: list[tuple[str, int]] = []
    for node in ast.walk(tree):
        if not isinstance(node, ast.Call):
            continue
        func = node.func
        if isinstance(func, ast.Attribute):
            found.append((func.attr, func.lineno))
        elif isinstance(func, ast.Name):
            found.append((func.id, func.lineno))
    return found


def test_sources_exist_to_scan():
    # Guards the guard: a path typo would make the assertion below vacuously pass.
    assert len(_python_sources()) >= 5


def test_no_authoritative_iam_resources():
    offenders: list[str] = []
    for path in _python_sources():
        tree = ast.parse(path.read_text(), filename=str(path))
        for name, lineno in _called_names(tree):
            if _is_forbidden(name):
                offenders.append(f"{path.relative_to(ROOT)}:{lineno}: {name}")
    assert not offenders, (
        "Authoritative IAM resources found. Use the additive IAMMember form instead — "
        "IAMBinding and IAMPolicy silently remove bindings they do not list:\n  "
        + "\n  ".join(offenders)
    )


def test_the_check_detects_a_real_violation():
    # Without this, a broken detector would let the assertion above pass forever.
    bad = ast.parse("import pulumi_gcp as gcp\nx = gcp.projects.IAMPolicy('p', project='q')\n")
    assert any(_is_forbidden(n) for n, _ in _called_names(bad))

    worse = ast.parse("y = gcp.storage.BucketIAMBinding(\n    'b',\n    role='roles/x',\n)\n")
    assert any(_is_forbidden(n) for n, _ in _called_names(worse))


def test_the_check_is_not_fooled_by_casing():
    # pulumi-gcp spells it BOTH ways: gcp.projects.IAMPolicy but gcp.cloudrunv2.ServiceIamPolicy.
    # An earlier version of this check matched only the uppercase form and would have let
    # ServiceIamPolicy through.
    for spelling in ("gcp.cloudrunv2.ServiceIamPolicy", "gcp.cloudrunv2.ServiceIamBinding"):
        tree = ast.parse(f"x = {spelling}('s')\n")
        assert any(_is_forbidden(n) for n, _ in _called_names(tree)), spelling


def test_the_check_permits_the_additive_form():
    for spelling in ("gcp.projects.IAMMember", "gcp.cloudrunv2.ServiceIamMember"):
        tree = ast.parse(f"z = {spelling}('m', role='roles/x', member=m)\n")
        names = [n for n, _ in _called_names(tree)]
        assert names and not any(_is_forbidden(n) for n in names), spelling
        assert any(n.lower().endswith(ALLOWED_SUFFIX) for n in names), spelling


def test_prose_naming_the_forbidden_types_does_not_trip_it():
    # This module's own docstring names both. So does service_identity.py's.
    prose = ast.parse('"""Never use IAMPolicy or IAMBinding."""\n# IAMPolicy in a comment too\n')
    assert not _called_names(prose)
