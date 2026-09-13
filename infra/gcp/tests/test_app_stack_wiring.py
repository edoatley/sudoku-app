"""How the app stack composes its components.

Asserts the decisions that are easy to get wrong and expensive to discover late — a Firestore
database in the wrong location cannot be moved, and a custom domain attached before its DNS
delegation has propagated stalls on asynchronous certificate issuance.

@spec CP-GCP-020, CP-GCP-024, CP-GCP-040, CP-PUL-021, CP-PUL-022, CP-PUL-050
"""

from __future__ import annotations

import pathlib

import pulumi
import pytest

from components import naming
from components.dns_zone import DnsZone
from components.firestore_database import FirestoreDatabase
from components.static_site import StaticSite

APP_MAIN = pathlib.Path(__file__).resolve().parent.parent / "app" / "__main__.py"
PROJECT_ID = "sudoku-eo-2026"


class TestPhase3Scope:
    """Phase 3 stands up data, hosting and the DNS zone — and deliberately nothing else."""

    def test_no_custom_domain_is_attached_yet(self):
        # Attaching it is Phase 8. Google-managed cert issuance is asynchronous and needs DNS
        # already answering, so the zone (here) and the domain (Phase 8) are split on purpose.
        src = APP_MAIN.read_text()
        assert "custom_domain=" not in src, (
            "StaticSite must not receive custom_domain in Phase 3 — see the phase plan §1"
        )

    def test_no_cloud_run_services_yet(self):
        assert "ContainerService" not in APP_MAIN.read_text(), "Cloud Run is Phase 6"

    def test_no_identity_platform_yet(self):
        assert "IdentityPlatform" not in APP_MAIN.read_text(), "Identity Platform is Phase 4"

    def test_stack_reference_uses_the_diy_backend_form(self):
        # DIY backends place every project under a virtual organization named by the literal
        # constant "organization". The bare stack name that `pulumi stack ls` shows does not
        # resolve across projects.
        assert "organization/sudoku-gcp-bootstrap/prod" in APP_MAIN.read_text()


class TestFirestore:
    @pulumi.runtime.test
    def test_production_database_is_in_the_configured_region(self):
        # Irreversible: a Firestore database cannot be moved, only deleted and recreated — and
        # prod is protected against exactly that.
        db = FirestoreDatabase(
            "loc-check",
            project="p",
            database_id="(default)",
            location="us-central1",
            is_prod=True,
        ).database
        return db.location_id.apply(lambda loc: _assert(loc == "us-central1", loc))

    def test_prod_uses_the_default_database_name(self):
        assert naming.firestore_database_id("prod") == "(default)"

    def test_ephemeral_stacks_get_their_own_database(self):
        # Environments isolate by named database, not by project.
        assert naming.firestore_database_id("rcg-x") == "sudoku-rcg-x"


class TestHosting:
    def test_prod_site_id_is_the_project_id(self):
        assert naming.hosting_site_id("prod", PROJECT_ID) == PROJECT_ID

    def test_site_id_stays_within_the_firebase_limit_for_real_branches(self):
        for branch in ("rcg-parity", "rcg-some-long-feature-branch", "rcg-a"):
            stack = naming.stack_name_for_branch(branch, PROJECT_ID)
            site = naming.hosting_site_id(stack, PROJECT_ID)
            assert len(site) <= naming.FIREBASE_SITE_ID_MAX, (branch, site, len(site))

    @pulumi.runtime.test
    def test_default_url_matches_the_site_id(self):
        site = StaticSite("host-check", project="p", site_id=PROJECT_ID)
        return site.default_url.apply(lambda u: _assert(u == f"https://{PROJECT_ID}.web.app", u))


class TestDns:
    @pulumi.runtime.test
    def test_zone_is_created_for_the_delegated_subdomain(self):
        zone = DnsZone(
            "dns-check",
            project="p",
            zone_name="gcp-edoatley",
            dns_name="gcp.edoatley.co.uk.",
        )
        return zone.zone.dns_name.apply(lambda d: _assert(d == "gcp.edoatley.co.uk.", d))

    def test_unqualified_dns_name_is_rejected(self):
        # A missing trailing dot is accepted by some tooling and silently produces records under
        # the wrong suffix.
        with pytest.raises(ValueError, match="trailing dot"):
            DnsZone("bad", project="p", zone_name="z", dns_name="gcp.edoatley.co.uk")

    def test_only_production_owns_the_zone(self):
        # Ephemeral stacks serve from their own *.web.app origin and must never touch shared DNS.
        src = APP_MAIN.read_text()
        assert "if is_prod:" in src and "DnsZone(" in src


def _assert(condition, detail="") -> None:
    assert condition, detail
