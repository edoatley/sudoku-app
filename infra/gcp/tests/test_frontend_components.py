"""Hosting, DNS and Identity Platform.

@spec CP-GCP-030, CP-GCP-040, CP-PUL-030, CP-PUL-050
"""

from __future__ import annotations

import pulumi
import pytest

from components.dns_zone import DnsZone, RecordSpec
from components.identity_platform import IdentityPlatform
from components.static_site import StaticSite


class TestStaticSite:
    @pulumi.runtime.test
    def test_default_url_is_derivable_before_apply(self):
        # Known ahead of apply from the site id, which is what lets it seed the backend's CORS
        # list without a dependency cycle.
        site = StaticSite("s", project="p", site_id="sudoku-eo-2026")
        return site.default_url.apply(lambda u: _assert(u == "https://sudoku-eo-2026.web.app", u))

    def test_no_custom_domain_by_default(self):
        site = StaticSite("s-nodomain", project="p", site_id="sudoku-eo-rcg-x")
        assert site.custom_domain_resource is None
        assert site.custom_domain_url is None

    @pulumi.runtime.test
    def test_custom_domain_does_not_block_the_first_apply(self):
        # Cert issuance is asynchronous (minutes to ~24h); waiting on the first apply would hang
        # the pipeline. False for bring-up, true thereafter.
        site = StaticSite(
            "s-domain",
            project="p",
            site_id="sudoku-eo-2026",
            custom_domain="sudoku.gcp.edoatley.co.uk",
            wait_dns_verification=False,
        )
        assert site.custom_domain_resource is not None
        return site.custom_domain_resource.wait_dns_verification.apply(
            lambda v: _assert(v is False, v)
        )


class TestDnsZone:
    def test_rejects_an_unqualified_dns_name(self):
        # A missing trailing dot is silently accepted by some tooling and produces records under
        # the wrong suffix. Fail loudly instead.
        with pytest.raises(ValueError, match="trailing dot"):
            DnsZone("z", project="p", zone_name="gcp-edoatley", dns_name="gcp.edoatley.co.uk")

    @pulumi.runtime.test
    def test_name_servers_are_exported_for_the_parent_zone_delegation(self):
        zone = DnsZone("z-ns", project="p", zone_name="gcp-zone", dns_name="gcp.edoatley.co.uk.")
        return zone.name_servers.apply(
            lambda ns: _assert(len(ns) == 4 and all(n.endswith(".") for n in ns), ns)
        )

    @pulumi.runtime.test
    def test_records_are_created_with_a_short_ttl(self):
        # 300s keeps a cutover or rollback quick.
        zone = DnsZone(
            "z-rr",
            project="p",
            zone_name="gcp-zone",
            dns_name="gcp.edoatley.co.uk.",
            records=[
                RecordSpec(
                    name="sudoku.gcp.edoatley.co.uk.",
                    type="CNAME",
                    rrdatas=("sudoku-eo-2026.web.app.",),
                )
            ],
        )
        assert len(zone.records) == 1
        return zone.records[0].ttl.apply(lambda t: _assert(t == 300, t))


class TestIdentityPlatform:
    @pulumi.runtime.test
    def test_issuer_and_audience_match_the_backend_oidc_config(self):
        # The %gcp profile hard-codes these shapes; a mismatch 401s every request.
        idp = IdentityPlatform(
            "idp",
            project="sudoku-eo-2026",
            authorized_domains=["localhost"],
            google_client_id="cid",
            google_client_secret="secret",
        )

        def check(args):
            issuer, audience = args
            assert issuer == "https://securetoken.google.com/sudoku-eo-2026"
            assert audience == "sudoku-eo-2026"

        return pulumi.Output.all(idp.issuer, idp.audience).apply(check)

    @pulumi.runtime.test
    def test_google_is_the_configured_provider(self):
        idp = IdentityPlatform(
            "idp-g",
            project="p",
            authorized_domains=["localhost"],
            google_client_id="cid",
            google_client_secret="secret",
        )
        return idp.google_idp.idp_id.apply(lambda i: _assert(i == "google.com", i))

    @pulumi.runtime.test
    def test_authorized_domains_are_passed_through(self):
        # An origin missing here fails sign-in with redirect_uri_mismatch at runtime.
        domains = ["localhost", "sudoku-eo-2026.web.app", "sudoku.gcp.edoatley.co.uk"]
        idp = IdentityPlatform(
            "idp-d",
            project="p",
            authorized_domains=domains,
            google_client_id="cid",
            google_client_secret="secret",
        )
        return idp.config.authorized_domains.apply(lambda d: _assert(d == domains, d))


def _assert(condition, detail="") -> None:
    assert condition, detail
