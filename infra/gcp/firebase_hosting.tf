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
  site_id  = "sudoku${local.suffix}"

  depends_on = [google_firebase_project.default]
}

# Custom domain (production only). Firebase provisions and renews a Google-managed
# TLS certificate. The required DNS records are surfaced by this resource and
# added to the Cloud DNS zone in dns.tf / by the runbook.
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
