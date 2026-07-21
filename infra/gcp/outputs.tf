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

output "backend_service_account" {
  description = "Backend Cloud Run runtime service account email."
  value       = var.run_service_account_email
}
