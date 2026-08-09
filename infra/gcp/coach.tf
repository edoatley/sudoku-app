# ---------------------------------------------------------------------------
# AI coach — cross-cloud Bedrock credential access (gap D / CP-GCP-085).
#
# The coach runs the identical application code on GCP; it reaches AWS Bedrock
# cross-cloud using a least-privilege AWS access key (Bedrock InvokeModel only)
# stored in Secret Manager. The secrets are created MANUALLY (runbook §6) — this
# file only grants the backend runtime service account permission to read them,
# so the values never enter Terraform state. cloud_run.tf mounts them as the
# standard AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY env vars.
#
# Rate-limit state lives in the Firestore coachRateLimits collection (TTL already
# provisioned in firestore.tf per CP-GCP-022); no extra infra needed here.
# ---------------------------------------------------------------------------

resource "google_secret_manager_secret_iam_member" "bedrock_access_key" {
  count = var.enable_coach ? 1 : 0

  project   = var.project_id
  secret_id = var.bedrock_access_key_secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${var.run_service_account_email}"
}

resource "google_secret_manager_secret_iam_member" "bedrock_secret_key" {
  count = var.enable_coach ? 1 : 0

  project   = var.project_id
  secret_id = var.bedrock_secret_key_secret_id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${var.run_service_account_email}"
}
