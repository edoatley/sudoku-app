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


class TestStackScope:
    """What the app stack does and does not yet contain.

    These guards exist so scope cannot drift silently. When a phase lands, the corresponding
    assertion flips rather than being deleted — Identity Platform moved from "not yet" to
    "wired" at Phase 4.
    """

    def test_no_custom_domain_is_attached_yet(self):
        # Attaching it is Phase 8. Google-managed cert issuance is asynchronous and needs DNS
        # already answering, so the zone (here) and the domain (Phase 8) are split on purpose.
        src = APP_MAIN.read_text()
        assert "custom_domain=" not in src, (
            "StaticSite must not receive custom_domain in Phase 3 — see the phase plan §1"
        )

    def test_cloud_run_landed_in_phase_6(self):
        assert "ContainerService" in APP_MAIN.read_text()

    def test_services_are_conditional_on_an_image_tag(self):
        # The prod stack carries no image tags and must keep previewing clean. Presence of a
        # built artefact gates the service, not a feature toggle — a toggle is what let a manual
        # dispatch silently revert the Vertex cutover on the Terraform path.
        src = APP_MAIN.read_text()
        assert 'config.get("backendImageTag")' in src
        assert 'config.get("imageRecognitionImageTag")' in src

    def test_image_uris_are_built_from_the_registry_output(self):
        # Hand-assembling the registry host would desync silently if the repository were renamed.
        src = APP_MAIN.read_text()
        assert "docker.pkg.dev" not in src, "registry host must come from the bootstrap output"
        assert "artifact_registry_url" in src

    def test_environments_come_from_the_shared_helpers(self):
        src = APP_MAIN.read_text()
        assert "backend_env(" in src and "image_recognition_env(" in src

    def test_no_aws_or_bedrock_anywhere_in_the_program(self):
        # The whole point of the re-platform: zero long-lived credentials on GCP. @spec CP-GCP-089
        src = APP_MAIN.read_text().upper()
        for token in ("AWS_", "BEDROCK", "SECRET_KEY_REF"):
            assert token not in src, token

    def test_identity_platform_landed_in_phase_4(self):
        assert "IdentityPlatform" in APP_MAIN.read_text()

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


class TestIdentityPlatform:
    """@spec CP-GCP-011, CP-GCP-030, CP-PUL-030"""

    def test_identity_platform_is_wired(self):
        assert "IdentityPlatform(" in APP_MAIN.read_text()

    def test_all_four_sign_in_origins_are_authorised(self):
        # A missing origin fails only at runtime, with redirect_uri_mismatch. Build the list from
        # the same values Hosting uses rather than typing them out again.
        src = APP_MAIN.read_text()
        for origin in ("localhost", ".web.app", ".firebaseapp.com", "custom_domain"):
            assert origin in src, origin

    def test_client_secret_comes_from_encrypted_config(self):
        # require_secret, not require — otherwise the value lands in state in plaintext.
        assert 'config.require_secret("googleOauthClientSecret")' in APP_MAIN.read_text()

    def test_client_secret_is_never_exported(self):
        src = APP_MAIN.read_text()
        assert "googleOauthClientSecret" not in src.split("# ── Outputs")[-1]

    def test_issuer_and_audience_are_exported_for_phase_6(self):
        # The backend's %gcp profile hard-codes these shapes; a mismatch 401s every request.
        src = APP_MAIN.read_text()
        assert 'pulumi.export("identity_platform_issuer"' in src
        assert 'pulumi.export("identity_platform_audience"' in src

    @pulumi.runtime.test
    def test_issuer_matches_the_shape_the_backend_expects(self):
        from components.identity_platform import IdentityPlatform

        idp = IdentityPlatform(
            "issuer-shape",
            project=PROJECT_ID,
            authorized_domains=["localhost"],
            google_client_id="cid",
            google_client_secret="sec",
        )
        return idp.issuer.apply(
            lambda i: _assert(i == f"https://securetoken.google.com/{PROJECT_ID}", i)
        )

    @pulumi.runtime.test
    def test_phone_number_is_declared_even_though_disabled(self):
        # The server populates this block regardless. Omitting it made `pulumi refresh` report
        # drift on every run, and a check that always fires masks the drift it exists to catch.
        from components.identity_platform import IdentityPlatform

        idp = IdentityPlatform(
            "phone-decl",
            project=PROJECT_ID,
            authorized_domains=["localhost"],
            google_client_id="cid",
            google_client_secret="sec",
        )
        return idp.config.sign_in.apply(
            lambda s: _assert(s.get("phone_number", {}).get("enabled") is False, s)
        )
