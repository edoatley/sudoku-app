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

FORBIDDEN_SUFFIXES = ("IAMBinding", "IAMPolicy")
ALLOWED_SUFFIX = "IAMMember"


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
            if any(name.endswith(suffix) for suffix in FORBIDDEN_SUFFIXES):
                offenders.append(f"{path.relative_to(ROOT)}:{lineno}: {name}")
    assert not offenders, (
        "Authoritative IAM resources found. Use the additive IAMMember form instead — "
        "IAMBinding and IAMPolicy silently remove bindings they do not list:\n  "
        + "\n  ".join(offenders)
    )


def test_the_check_detects_a_real_violation():
    # Without this, a broken detector would let the assertion above pass forever.
    bad = ast.parse("import pulumi_gcp as gcp\nx = gcp.projects.IAMPolicy('p', project='q')\n")
    names = [n for n, _ in _called_names(bad)]
    assert any(n.endswith(FORBIDDEN_SUFFIXES) for n in names)

    worse = ast.parse("y = gcp.storage.BucketIAMBinding(\n    'b',\n    role='roles/x',\n)\n")
    assert any(n.endswith(FORBIDDEN_SUFFIXES) for n, _ in _called_names(worse))


def test_the_check_permits_the_additive_form():
    good = ast.parse("z = gcp.projects.IAMMember('m', role='roles/x', member=m)\n")
    names = [n for n, _ in _called_names(good)]
    assert names and not any(n.endswith(FORBIDDEN_SUFFIXES) for n in names)
    assert any(n.endswith(ALLOWED_SUFFIX) for n in names)


def test_prose_naming_the_forbidden_types_does_not_trip_it():
    # This module's own docstring names both. So does service_identity.py's.
    prose = ast.parse('"""Never use IAMPolicy or IAMBinding."""\n# IAMPolicy in a comment too\n')
    assert not _called_names(prose)
