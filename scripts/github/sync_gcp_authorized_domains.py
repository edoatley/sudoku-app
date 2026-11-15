#!/usr/bin/env python3
"""Synchronize GCP Identity Platform ``authorizedDomains`` with live Firebase Hosting sites.

Identity Platform's ``Config`` resource is a project-wide singleton owned by the ``prod`` Pulumi
stack, whereas every ``rcg-*`` stack creates its own ``sudoku-dev-<stack>-<hex>`` Firebase Hosting
site. Firebase Auth's ``signInWithPopup`` checks ``authorizedDomains`` against the calling page's
hostname (``<site_id>.web.app``) before initiating the Google OAuth flow against the shared
``<project_id>.firebaseapp.com/__/auth/handler`` redirect URI.

Running this helper after ``pulumi up`` (on both ``prod`` and ``rcg-*``) and after ``pulumi
destroy`` ensures:
1. Every active ``rcg-*`` Hosting site's ``<site_id>.web.app`` domain is authorized for Google
   sign-in immediately after deploy.
2. Deploying ``prod`` never strips active ``rcg-*`` Hosting domains from ``authorizedDomains``.
3. Tearing down an ``rcg-*`` stack prunes its ``sudoku-dev-*.web.app`` domain cleanly.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import urllib.request

DEFAULT_CUSTOM_DOMAIN = "sudoku.gcp.edoatley.co.uk"
EPHEMERAL_SITE_PREFIX = "sudoku-dev-"


def compute_authorized_domains(
    *,
    project_id: str,
    hosting_site_ids: list[str],
    existing_domains: list[str],
    custom_domain: str = DEFAULT_CUSTOM_DOMAIN,
) -> list[str]:
    """Compute the canonical ``authorizedDomains`` list for ``project_id``."""
    candidates: list[str] = [
        "localhost",
        f"{project_id}.web.app",
        f"{project_id}.firebaseapp.com",
    ]
    if custom_domain:
        candidates.append(custom_domain)

    # Preserve any non-ephemeral domains already present in Identity Platform config.
    for domain in existing_domains:
        if not domain.startswith(EPHEMERAL_SITE_PREFIX):
            candidates.append(domain)

    # Authorize every currently existing Firebase Hosting site in the project.
    for site_id in sorted(hosting_site_ids):
        if site_id:
            candidates.append(f"{site_id}.web.app")

    deduped: list[str] = []
    seen: set[str] = set()
    for domain in candidates:
        if domain and domain not in seen:
            seen.add(domain)
            deduped.append(domain)
    return deduped


def _access_token() -> str:
    env_token = os.environ.get("GCLOUD_ACCESS_TOKEN", "").strip()
    if env_token:
        return env_token
    return subprocess.check_output(["gcloud", "auth", "print-access-token"], text=True).strip()


def _request_json(
    url: str,
    *,
    token: str,
    project_id: str,
    method: str = "GET",
    body: dict | None = None,
) -> dict:
    headers = {
        "Authorization": f"Bearer {token}",
        "x-goog-user-project": project_id,
    }
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req) as resp:
        return json.load(resp)


def sync_project_authorized_domains(project_id: str) -> list[str]:
    """Fetch live Hosting sites and PATCH Identity Platform ``authorizedDomains`` if needed."""
    token = _access_token()
    sites_resp = _request_json(
        f"https://firebasehosting.googleapis.com/v1beta1/projects/{project_id}/sites",
        token=token,
        project_id=project_id,
    )
    site_ids = [
        site["name"].rsplit("/", 1)[-1] for site in sites_resp.get("sites", []) if site.get("name")
    ]

    config_url = f"https://identitytoolkit.googleapis.com/admin/v2/projects/{project_id}/config"
    config_resp = _request_json(config_url, token=token, project_id=project_id)
    existing = list(config_resp.get("authorizedDomains", []))

    desired = compute_authorized_domains(
        project_id=project_id,
        hosting_site_ids=site_ids,
        existing_domains=existing,
    )

    if desired == existing:
        print(f"Identity Platform authorizedDomains already in sync ({len(desired)}): {desired}")
        return desired

    updated = _request_json(
        f"{config_url}?updateMask=authorizedDomains",
        token=token,
        project_id=project_id,
        method="PATCH",
        body={"authorizedDomains": desired},
    )
    result = list(updated.get("authorizedDomains", desired))
    print(f"Updated Identity Platform authorizedDomains ({len(result)}): {result}")
    return result


def main() -> int:
    project_id = (os.environ.get("GCP_PROJECT_ID") or os.environ.get("PROJECT_ID") or "").strip()
    if not project_id:
        print("::error::GCP_PROJECT_ID or PROJECT_ID is required", file=sys.stderr)
        return 1
    sync_project_authorized_domains(project_id)
    return 0


if __name__ == "__main__":
    sys.exit(main())
