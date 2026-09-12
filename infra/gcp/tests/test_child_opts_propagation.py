"""Components must merge the caller's ResourceOptions into their children's.

Pulumi does **not** inherit `depends_on` from a parent to its children. A component that builds
child options as a bare `ResourceOptions(parent=self)` therefore silently discards any
`depends_on` its caller supplied.

That bug is not theoretical: the bootstrap stack orders every resource behind 14 API
enablements, and `gcp.projects.Service` produces no value those resources consume, so
`depends_on` is the only thing sequencing them. Dropped, Pulumi creates the KeyRing in parallel
with the cloudkms enablement and the first `up` fails — cloudkms is not on by default in a new
project.

The failure is loud but wastes an apply and leaves a half-built project, which matters most in
exactly the situation the clean-room rebuild is meant to prove works. @spec CP-PUL-001
"""

from __future__ import annotations

import ast
import pathlib

COMPONENTS = pathlib.Path(__file__).resolve().parent.parent / "components"


def _component_sources() -> list[pathlib.Path]:
    return sorted(p for p in COMPONENTS.glob("*.py") if p.name not in {"__init__.py", "naming.py"})


def _is_resource_options_call(node: ast.AST) -> bool:
    return (
        isinstance(node, ast.Call)
        and isinstance(node.func, ast.Attribute)
        and node.func.attr == "ResourceOptions"
    )


def _has_parent_self(call: ast.Call) -> bool:
    return any(
        kw.arg == "parent" and isinstance(kw.value, ast.Name) and kw.value.id == "self"
        for kw in call.keywords
    )


def _merged_children(tree: ast.AST) -> set[int]:
    """ids of ResourceOptions calls that appear as an argument to ResourceOptions.merge(...)."""
    merged: set[int] = set()
    for node in ast.walk(tree):
        if (
            isinstance(node, ast.Call)
            and isinstance(node.func, ast.Attribute)
            and node.func.attr == "merge"
        ):
            for arg in node.args:
                if _is_resource_options_call(arg):
                    merged.add(id(arg))
    return merged


def test_components_exist_to_scan():
    # Guards the guard: a path change would make the assertion below vacuous.
    assert len(_component_sources()) >= 9


def test_every_component_merges_caller_opts_into_children():
    offenders: list[str] = []
    for path in _component_sources():
        tree = ast.parse(path.read_text(), filename=str(path))
        merged = _merged_children(tree)
        for node in ast.walk(tree):
            if (
                _is_resource_options_call(node)
                and _has_parent_self(node)
                and id(node) not in merged
            ):
                offenders.append(f"{path.name}:{node.lineno}")
    assert not offenders, (
        "These build child options without merging the caller's — any depends_on passed to the "
        "component is silently dropped, because Pulumi does not inherit it from parent to child. "
        "Use pulumi.ResourceOptions.merge(opts, pulumi.ResourceOptions(parent=self, ...)):\n  "
        + "\n  ".join(offenders)
    )


def test_the_check_detects_the_bare_form():
    # Without this, a broken detector would let the assertion above pass forever.
    bare = ast.parse("x = pulumi.ResourceOptions(parent=self, protect=True)\n")
    merged = _merged_children(bare)
    calls = [n for n in ast.walk(bare) if _is_resource_options_call(n) and _has_parent_self(n)]
    assert calls and not any(id(c) in merged for c in calls)


def test_the_check_permits_the_merged_form():
    ok = ast.parse("x = pulumi.ResourceOptions.merge(opts, pulumi.ResourceOptions(parent=self))\n")
    merged = _merged_children(ok)
    calls = [n for n in ast.walk(ok) if _is_resource_options_call(n) and _has_parent_self(n)]
    assert calls and all(id(c) in merged for c in calls)
