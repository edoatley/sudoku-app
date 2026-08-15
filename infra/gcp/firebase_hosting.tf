# ---------------------------------------------------------------------------
# Firebase Hosting — Amplify equivalent for the React SPA.
# CI runs `firebase deploy` for ui/dist AFTER terraform apply (VITE_* values are
# baked at build time and must reflect the applied infrastructure), mirroring the
# AWS "trigger build after apply" ordering. SPA rewrites live in ui/firebase.json.
# ---------------------------------------------------------------------------

resource "google_firebase_project" "default" {
  provider = google-beta
  project  = var.project_id
}

resource "google_firebase_hosting_site" "frontend" {
  provider = google-beta
  project  = var.project_id
  # Firebase Hosting site IDs are GLOBALLY unique (like project IDs), so a bare
  # "sudoku" is taken. Derive from the project ID, which is already unique.
  # (site_id must be <= 30 chars — watch this for long rc-* workspace suffixes.)
  site_id = "${var.project_id}${local.suffix}"

  depends_on = [google_firebase_project.default]
}

# Custom domain (production only). Firebase provisions and renews a Google-managed TLS certificate.
# For a subdomain, Firebase verifies + routes via a single CNAME to the default site — add
# `CNAME ${var.custom_domain} -> <site>.web.app` in the PARENT DNS zone (edoatley.co.uk, on Route53).
# No Cloud DNS zone / delegation / A-AAAA-TXT records: GCP Cloud DNS has no apex-alias (unlike Route53
# ALIAS), so the AWS-style delegated-subzone approach doesn't apply here. See the custom_domain_cname_target
# output + runbook §6b.
resource "google_firebase_hosting_custom_domain" "frontend" {
  provider      = google-beta
  count         = local.is_default && var.enable_custom_domain ? 1 : 0
  project       = var.project_id
  site_id       = google_firebase_hosting_site.frontend.site_id
  custom_domain = var.custom_domain

  # Don't block apply on certificate/DNS verification (parity with Amplify's
  # wait_for_verification=false — verification completes out of band).
  wait_dns_verification = false
}
