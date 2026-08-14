# ---------------------------------------------------------------------------
# Image recognition — Python/Bedrock service on Cloud Run.
# Longer timeout for Bedrock inference + container cold start (parity with the
# AWS image-recognition Lambda's 60s). Runs as its own runtime service account.
# ---------------------------------------------------------------------------

resource "google_cloud_run_v2_service" "image_recognition" {
  count = var.deploy_image_recognition ? 1 : 0

  name     = "sudoku-image-recognition${local.suffix}"
  location = var.region
  ingress  = "INGRESS_TRAFFIC_ALL"

  deletion_protection = local.is_default

  lifecycle {
    precondition {
      condition     = var.image_recognition_image_uri != "" && var.image_recognition_service_account_email != ""
      error_message = "deploy_image_recognition = true requires image_recognition_image_uri and image_recognition_service_account_email to be set."
    }
  }

  template {
    service_account                  = var.image_recognition_service_account_email
    timeout                          = "60s"
    max_instance_request_concurrency = 4

    scaling {
      min_instance_count = 0
      max_instance_count = var.image_recognition_max_instances
    }

    containers {
      image = var.image_recognition_image_uri

      ports {
        container_port = 8080
      }

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
        cpu_idle = true
      }

      env {
        name  = "COACH_BEDROCK_API_MODE"
        value = local.coach_bedrock_api_mode
      }
      # Bedrock model list + region (handler.py reads these; region is the cross-cloud AWS region).
      env {
        name  = "BEDROCK_MODELS"
        value = join(",", local.bedrock_models)
      }
      env {
        name  = "AWS_REGION_NAME"
        value = local.bedrock_region
      }
      # Consumed by the FastAPI front (app.py) for in-app Firebase JWT validation + CORS.
      env {
        name  = "GCP_PROJECT_ID"
        value = var.project_id
      }
      env {
        name  = "CORS_ALLOWED_ORIGINS"
        value = local.cors_allowed_origins
      }
      # Cross-cloud Bedrock credentials (same secrets as the coach; gap D). Only wired when
      # enable_coach = true so an image-rec deploy without the secrets still applies.
      dynamic "env" {
        for_each = var.enable_coach ? [1] : []
        content {
          name = "AWS_ACCESS_KEY_ID"
          value_source {
            secret_key_ref {
              secret  = var.bedrock_access_key_secret_id
              version = "latest"
            }
          }
        }
      }
      dynamic "env" {
        for_each = var.enable_coach ? [1] : []
        content {
          name = "AWS_SECRET_ACCESS_KEY"
          value_source {
            secret_key_ref {
              secret  = var.bedrock_secret_key_secret_id
              version = "latest"
            }
          }
        }
      }
    }
  }

  # checkov:skip=CKV_GCP_102: Public API — auth enforced in-app (Firebase JWT); roles/run.invoker for allUsers granted below for non-default (RC) workspaces only
  # checkov:skip=CKV_GCP_119: Binary Authorization not warranted for a single-developer project
}

# Public network reachability for RC (non-default) workspaces so a browser (local or hosted UI) can
# reach the service; the app still enforces auth in-app (app.py validates the Firebase JWT). Prod
# (default) invoker stays manual (gap G1). @spec CP-GCP-014
resource "google_cloud_run_v2_service_iam_member" "image_recognition_public" {
  count = var.deploy_image_recognition && !local.is_default ? 1 : 0

  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.image_recognition[0].name
  role     = "roles/run.invoker"
  member   = "allUsers"
}
