"""Stack-name derivation, resource suffixes, and label sanitisation.

Single source of truth, shared by the Pulumi programs and by CI. The bash equivalent
(`scripts/github/gcp-workspace-name.sh`) is deleted once this is authoritative, so the two
cannot drift. @spec CP-PUL-021
"""

from __future__ import annotations

import re

PROD_STACK = "prod"
"""The production stack. Replaces Terraform's `default` workspace, which was a Terraform
artefact rather than a meaningful name."""

FIREBASE_SITE_ID_MAX = 30
"""Firebase Hosting site ids are capped at 30 characters. This is a *Firebase* constraint, not a
Terraform one, so it survives the move to Pulumi unchanged."""

LOCAL_DEV_ORIGIN = "http://localhost:5173"

_ALLOWED = re.compile(r"[^a-z0-9-]")
_LABEL_DISALLOWED = re.compile(r"[^a-z0-9_-]")

# NOTE: there is deliberately no `is_rc` equivalent here. `infra/gcp/main.tf:3` defines
# `is_rc = startswith(terraform.workspace, "rc-")`, which never matches: GCP environments derive
# from `rcg-*` branches, so the name always starts "rcg-". The result was that
# coach_bedrock_api_mode was permanently "invoke" on GCP and the converse A/B never ran there.
# Do not reintroduce it. See docs/llds/cloud-platform-gcp.md — Technical Debt.


def stack_name_for_branch(branch: str, project_id: str) -> str:
    """Derive a stack name from a git branch, safe for use in a Firebase Hosting site id.

    The site id is ``{project_id}-{stack}``, so the stack is capped at
    ``30 - len(project_id) - 1``. Ports `scripts/github/gcp-workspace-name.sh`.
    """
    budget = FIREBASE_SITE_ID_MAX - len(project_id) - 1
    if budget < 1:
        raise ValueError(
            f"project_id {project_id!r} ({len(project_id)} chars) leaves no room for a stack "
            f"suffix within the {FIREBASE_SITE_ID_MAX}-char Firebase site_id limit"
        )
    sanitised = _ALLOWED.sub("", branch.lower().replace("/", "-").replace(".", "-"))
    name = sanitised[:budget].rstrip("-")
    if not name:
        raise ValueError(f"branch {branch!r} sanitises to an empty stack name")
    return name


def is_prod(stack: str) -> bool:
    return stack == PROD_STACK


def suffix(stack: str) -> str:
    """Resource-name suffix: empty in production, ``-{stack}`` elsewhere."""
    return "" if is_prod(stack) else f"-{stack}"


def environment_label(stack: str) -> str:
    """Value for the ``environment`` label. Lowercase ``[a-z0-9_-]``, <=63 chars.

    @spec CP-GCP-070
    """
    if is_prod(stack):
        return PROD_STACK
    return _LABEL_DISALLOWED.sub("-", stack.lower())[:63]


def default_labels(stack: str) -> dict[str, str]:
    """Provider default labels. ``managed_by`` is ``pulumi``, not ``terraform``.

    @spec CP-GCP-070
    """
    return {
        "project": "sudoku",
        "managed_by": "pulumi",
        "environment": environment_label(stack),
    }


def firestore_database_id(stack: str) -> str:
    """``(default)`` in production, a named ``sudoku-{stack}`` database otherwise.

    Firestore isolates environments by named database rather than by project. @spec CP-GCP-020
    """
    return "(default)" if is_prod(stack) else f"sudoku{suffix(stack)}"


def hosting_site_id(stack: str, project_id: str) -> str:
    """Firebase Hosting site id, guaranteed within the 30-char limit."""
    site_id = f"{project_id}{suffix(stack)}"
    if len(site_id) > FIREBASE_SITE_ID_MAX:
        raise ValueError(
            f"site_id {site_id!r} is {len(site_id)} chars, over the "
            f"{FIREBASE_SITE_ID_MAX}-char Firebase limit"
        )
    return site_id


def hosting_origin(stack: str, project_id: str) -> str:
    """The stack's Firebase Hosting origin. Known ahead of deploy, so it can seed CORS."""
    return f"https://{hosting_site_id(stack, project_id)}.web.app"


def cors_allowed_origins(stack: str, project_id: str, custom_domain: str | None = None) -> str:
    """Comma-separated CORS origins for the backend's ``CORS_ALLOWED_ORIGINS``.

    Production serves the custom domain; other stacks serve their own Hosting origin. localhost
    stays throughout so a locally-run UI can call a deployed backend. @spec CP-GCP-012
    """
    if is_prod(stack):
        if not custom_domain:
            raise ValueError("the prod stack requires a custom_domain for its CORS origins")
        return f"https://{custom_domain},{LOCAL_DEV_ORIGIN}"
    return f"{hosting_origin(stack, project_id)},{LOCAL_DEV_ORIGIN}"
