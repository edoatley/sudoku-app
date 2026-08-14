output "backend_url" {
  description = "Backend Cloud Run service URL (null until deploy_cloud_run = true)."
  value       = var.deploy_cloud_run ? google_cloud_run_v2_service.backend[0].uri : null
}

output "image_recognition_url" {
  description = "Image-recognition Cloud Run service URL (null until deploy_image_recognition = true)."
  value       = var.deploy_image_recognition ? google_cloud_run_v2_service.image_recognition[0].uri : null
}

output "firestore_database" {
  description = "Firestore database name for this workspace."
  value       = google_firestore_database.main.name
}

output "firebase_hosting_site_id" {
  description = "Firebase Hosting site ID (target for `firebase deploy`)."
  value       = google_firebase_hosting_site.frontend.site_id
}

output "firebase_hosting_default_url" {
  description = "Firebase Hosting default URL."
  value       = "https://${google_firebase_hosting_site.frontend.site_id}.web.app"
}

output "dns_name_servers" {
  description = "Cloud DNS name servers for sudoku-gcp.edoatley.co.uk — delegate these from the parent zone (only when enable_custom_domain = true)."
  value       = local.is_default && var.enable_custom_domain ? google_dns_managed_zone.frontend[0].name_servers : []
}

# The A/AAAA/TXT records Firebase requires INSIDE the Cloud DNS zone to point the custom domain at
# Hosting and prove ownership (distinct from dns_name_servers, which is the parent-zone NS delegation).
# Firebase computes these asynchronously, so this may be [] on the first apply and populate on a later
# refresh once verification has started. PR3 codifies these as google_dns_record_set resources; until
# then they can be added by hand. Flattened to {domain_name,type,rdata,required_action} for easy use.
output "custom_domain_required_dns_records" {
  description = "Firebase-required DNS records to add to the Cloud DNS zone for the custom domain (empty until enable_custom_domain = true and Firebase has computed them)."
  value = local.is_default && var.enable_custom_domain ? flatten([
    for update in google_firebase_hosting_custom_domain.frontend[0].required_dns_updates : [
      for d in update.desired : [
        for r in d.records : {
          domain_name     = r.domain_name
          type            = r.type
          rdata           = r.rdata
          required_action = r.required_action
        }
      ]
    ]
  ]) : []
}

output "backend_service_account" {
  description = "Backend Cloud Run runtime service account email."
  value       = var.run_service_account_email
}
