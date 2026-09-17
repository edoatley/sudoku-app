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

DEV_SITE_PREFIX = "sudoku-dev"
"""Prefix for every non-production Hosting site. Production's id is the bare project id.

Non-production ids deliberately omit the project id. It cost 14 of the 30 available characters
and identified nothing an ephemeral environment cares about — every stack lives in the same
project — and spending it left no room for the uniqueness suffix below."""

SITE_UNIQUE_SUFFIX_LEN = 4
"""Hex characters of the per-stack uniqueness suffix.

Firebase **tombstones a deleted site's name**: recreating a torn-down stack under the same id
fails at the Hosting site, part-way through the deploy, leaving resources to clean up by hand.
The suffix comes from Pulumi state, so it is fixed for a stack's life and fresh whenever a stack
is created. A git-derived value cannot do this — branches cut from the same base share a first
commit, and a rebase changes the hash, which would replace the site and burn its name."""

STACK_NAME_MAX = FIREBASE_SITE_ID_MAX - len(DEV_SITE_PREFIX) - 1 - SITE_UNIQUE_SUFFIX_LEN - 1
"""Characters available to a stack name, given the site id is ``{prefix}-{stack}-{suffix}``."""

LOCAL_DEV_ORIGIN = "http://localhost:5173"

_ALLOWED = re.compile(r"[^a-z0-9-]")
_LABEL_DISALLOWED = re.compile(r"[^a-z0-9_-]")

# NOTE: there is deliberately no `is_rc` equivalent here. `infra/gcp/main.tf:3` defines
# `is_rc = startswith(terraform.workspace, "rc-")`, which never matches: GCP environments derive
# from `rcg-*` branches, so the name always starts "rcg-". The result was that
# coach_bedrock_api_mode was permanently "invoke" on GCP and the converse A/B never ran there.
# Do not reintroduce it. See docs/llds/cloud-platform-gcp.md — Technical Debt.


def stack_name_for_branch(branch: str) -> str:
    """Derive a stack name from a git branch, safe for use in a Firebase Hosting site id.

    Capped at ``STACK_NAME_MAX`` so ``hosting_site_id`` always fits inside the Firebase limit.
    """
    sanitised = _ALLOWED.sub("", branch.lower().replace("/", "-").replace(".", "-"))
    name = sanitised[:STACK_NAME_MAX].rstrip("-")
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


def hosting_site_id(stack: str, project_id: str, unique_suffix: str | None = None) -> str:
    """Firebase Hosting site id, guaranteed within the 30-char limit.

    Production's id is the project id itself — a stable, long-lived site. Every other stack gets
    ``{DEV_SITE_PREFIX}-{stack}-{unique_suffix}``, and the suffix is mandatory: a reusable
    non-production id is what makes a torn-down stack unrecreatable.
    """
    if is_prod(stack):
        site_id = project_id
    else:
        if not unique_suffix:
            raise ValueError(
                f"stack {stack!r} requires a unique_suffix — a reusable non-production site id "
                "cannot be recreated once Firebase has tombstoned it"
            )
        site_id = f"{DEV_SITE_PREFIX}-{stack}-{unique_suffix}"
    if len(site_id) > FIREBASE_SITE_ID_MAX:
        raise ValueError(
            f"site_id {site_id!r} is {len(site_id)} chars, over the "
            f"{FIREBASE_SITE_ID_MAX}-char Firebase limit"
        )
    return site_id


def hosting_origin(stack: str, project_id: str, unique_suffix: str | None = None) -> str:
    """The stack's Firebase Hosting origin."""
    return f"https://{hosting_site_id(stack, project_id, unique_suffix)}.web.app"


def cors_allowed_origins(
    stack: str,
    project_id: str,
    custom_domain: str | None = None,
    unique_suffix: str | None = None,
) -> str:
    """Comma-separated CORS origins for the backend's ``CORS_ALLOWED_ORIGINS``.

    Production serves the custom domain; other stacks serve their own Hosting origin. localhost
    stays throughout so a locally-run UI can call a deployed backend. @spec CP-GCP-012
    """
    if is_prod(stack):
        if not custom_domain:
            raise ValueError("the prod stack requires a custom_domain for its CORS origins")
        return f"https://{custom_domain},{LOCAL_DEV_ORIGIN}"
    return f"{hosting_origin(stack, project_id, unique_suffix)},{LOCAL_DEV_ORIGIN}"
