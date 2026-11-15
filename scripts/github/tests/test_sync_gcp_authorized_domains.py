"""Unit tests for `scripts/github/sync_gcp_authorized_domains.py`."""

from __future__ import annotations

import sync_gcp_authorized_domains as sync


class TestComputeAuthorizedDomains:
    def test_includes_base_domains_and_live_hosting_sites(self):
        result = sync.compute_authorized_domains(
            project_id="sudoku-eo-2026",
            hosting_site_ids=["sudoku-eo-2026", "sudoku-dev-rcg-p7-13b8"],
            existing_domains=["localhost", "sudoku-eo-2026.web.app"],
        )
        assert result == [
            "localhost",
            "sudoku-eo-2026.web.app",
            "sudoku-eo-2026.firebaseapp.com",
            "sudoku.gcp.edoatley.co.uk",
            "sudoku-dev-rcg-p7-13b8.web.app",
        ]

    def test_prunes_stale_ephemeral_sites_that_were_destroyed(self):
        result = sync.compute_authorized_domains(
            project_id="sudoku-eo-2026",
            hosting_site_ids=["sudoku-eo-2026", "sudoku-dev-rcg-live-9a0b"],
            existing_domains=[
                "localhost",
                "sudoku-eo-2026.web.app",
                "sudoku-eo-2026.firebaseapp.com",
                "sudoku.gcp.edoatley.co.uk",
                "sudoku-dev-rcg-deleted-1111.web.app",
                "sudoku-dev-rcg-live-9a0b.web.app",
            ],
        )
        assert "sudoku-dev-rcg-deleted-1111.web.app" not in result
        assert "sudoku-dev-rcg-live-9a0b.web.app" in result

    def test_preserves_custom_non_ephemeral_domains(self):
        result = sync.compute_authorized_domains(
            project_id="sudoku-eo-2026",
            hosting_site_ids=["sudoku-eo-2026"],
            existing_domains=["localhost", "extra.edoatley.co.uk"],
        )
        assert "extra.edoatley.co.uk" in result
