# ---------------------------------------------------------------------------
# Image recognition — Python/Bedrock service on Cloud Run.
# Longer timeout for Bedrock inference + container cold start (parity with the
# AWS image-recognition Lambda's 60s). Runs as its own runtime service account.
# ---------------------------------------------------------------------------

resource "google_cloud_run_v2_service" "image_recognition" {
  name     = "sudoku-image-recognition${local.suffix}"
  location = var.region
  ingress  = "INGRESS_TRAFFIC_ALL"

  deletion_protection = local.is_default

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
    }
  }

  # checkov:skip=CKV_GCP_102: Public API — auth enforced in-app; roles/run.invoker for allUsers granted manually (see runbook)
  # checkov:skip=CKV_GCP_119: Binary Authorization not warranted for a single-developer project
}
