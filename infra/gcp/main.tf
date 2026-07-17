data "google_project" "current" {}

locals {
  is_default = terraform.workspace == "default"
  is_rc      = startswith(terraform.workspace, "rc-")
  suffix     = local.is_default ? "" : "-${terraform.workspace}"

  # GCP label values must be lowercase, match [a-z0-9_-], and be <= 63 chars.
  # Sanitize the workspace name before using it as a label value.
  workspace_label   = substr(lower(replace(terraform.workspace, "/[^a-z0-9_-]/", "-")), 0, 63)
  environment_label = local.is_default ? "prod" : local.workspace_label

  # Firestore isolates per workspace by named database: the (default) database on
  # the default workspace, a named sudoku{suffix} database otherwise.
  firestore_database_id = local.is_default ? "(default)" : "sudoku${local.suffix}"

  # CORS / frontend origins per workspace type (parity with the AWS facet's
  # static custom-domain approach). The GCP custom domain is sudoku-gcp.edoatley.co.uk.
  cors_allowed_origins = local.is_default ? "https://${var.custom_domain},http://localhost:5173" : "http://localhost:5173"

  # Bedrock API mode for the AI coach — mirrors the AWS facet (rc A/B-tests converse).
  coach_bedrock_api_mode = local.is_rc ? "converse" : "invoke"

  # AI coach feature flag — disabled on the default (production) workspace until proven.
  ai_coach_enabled = !local.is_default
}
