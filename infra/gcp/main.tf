data "google_project" "current" {}

locals {
  is_default = terraform.workspace == "default"
  is_rc      = startswith(terraform.workspace, "rc-")
  suffix     = local.is_default ? "" : "-${terraform.workspace}"

  # GCP label values must be lowercase, match [a-z0-9_-], and be <= 63 chars.
  # Sanitize the workspace name before using it as a label value. @spec CP-GCP-070
  workspace_label   = substr(lower(replace(terraform.workspace, "/[^a-z0-9_-]/", "-")), 0, 63)
  environment_label = local.is_default ? "prod" : local.workspace_label

  # Firestore isolates per workspace by named database: the (default) database on
  # the default workspace, a named sudoku{suffix} database otherwise.
  firestore_database_id = local.is_default ? "(default)" : "sudoku${local.suffix}"

  # This workspace's Firebase Hosting origin (static, known ahead of deploy). Included in CORS for
  # non-default workspaces so a hosted RC UI can call its backend; localhost stays for a locally-run
  # UI (the supported RC test path until per-workspace Hosting targets land — gap G3).
  hosting_origin = "https://${var.project_id}${local.suffix}.web.app"

  # CORS / frontend origins per workspace type (parity with the AWS facet's
  # static custom-domain approach). The GCP custom domain is sudoku-gcp.edoatley.co.uk.
  cors_allowed_origins = local.is_default ? "https://${var.custom_domain},http://localhost:5173" : "${local.hosting_origin},http://localhost:5173"

  # Bedrock API mode for the AI coach — mirrors the AWS facet (rc A/B-tests converse).
  coach_bedrock_api_mode = local.is_rc ? "converse" : "invoke"

  # Bedrock inference profiles used by the image-recognition service — single source of truth for
  # the BEDROCK_MODELS env var (mirrors infra/aws/main.tf). Cross-cloud region for Bedrock.
  bedrock_models = [
    "eu.anthropic.claude-haiku-4-5-20251001-v1:0",
  ]
  bedrock_region = "eu-west-2"
}
