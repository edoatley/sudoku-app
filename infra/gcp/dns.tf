# ---------------------------------------------------------------------------
# Cloud DNS — Route53 equivalent.
# Managed zone for the GCP frontend domain, created once in the default
# workspace. NS delegation from the parent zone edoatley.co.uk is a manual
# one-time step (as on AWS). The A/AAAA records that point the apex at Firebase
# Hosting come from the google_firebase_hosting_custom_domain resource's
# required-DNS output and are added per the runbook.
# ---------------------------------------------------------------------------

resource "google_dns_managed_zone" "frontend" {
  # Gated with the custom domain: a managed zone carries a small standing charge
  # (~$0.20/zone/month) and is only needed once the custom domain is set up.
  count       = local.is_default && var.enable_custom_domain ? 1 : 0
  name        = "sudoku-gcp"
  dns_name    = "${var.custom_domain}."
  description = "Delegated zone for the Sudoku GCP frontend"

  lifecycle {
    prevent_destroy = true
  }

  # checkov:skip=CKV_GCP_16: DNSSEC requires a matching DS record in the manually-delegated parent zone (edoatley.co.uk); left off to match the AWS Route53 zones and the manual delegation flow
}
