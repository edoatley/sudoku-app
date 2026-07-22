# ---------------------------------------------------------------------------
# Backend — Quarkus API on Cloud Run (Lambda equivalent).
# Runs as a manually-created runtime service account; validates Identity
# Platform JWTs in-app. Public invocation (roles/run.invoker for allUsers) is
# granted by hand, not here — see docs/runbooks/gcp-manual-setup.md.
# ---------------------------------------------------------------------------

resource "google_cloud_run_v2_service" "backend" {
  count = var.deploy_cloud_run ? 1 : 0

  name     = "sudoku${local.suffix}"
  location = var.region
  ingress  = "INGRESS_TRAFFIC_ALL"

  deletion_protection = local.is_default

  lifecycle {
    precondition {
      condition     = var.backend_image != "" && var.run_service_account_email != ""
      error_message = "deploy_cloud_run = true requires backend_image and run_service_account_email to be set."
    }
  }

  template {
    service_account                  = var.run_service_account_email
    timeout                          = "8s"
    max_instance_request_concurrency = var.backend_container_concurrency

    scaling {
      min_instance_count = 0
      max_instance_count = var.backend_max_instances
    }

    containers {
      image = var.backend_image

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

      # Activate the %gcp config profile (Firestore persistence + in-app Firebase JWT
      # validation + native CORS) with %prod as parent (email allow-list). See the
      # backend's application.properties %gcp/%prod blocks.
      env {
        name  = "QUARKUS_PROFILE"
        value = "gcp"
      }
      env {
        name  = "QUARKUS_CONFIG_PROFILE_PARENT"
        value = "prod"
      }
      # %gcp OIDC issuer/client-id/audience all interpolate ${GCP_PROJECT_ID}. The Firebase
      # project id equals the GCP project id.
      env {
        name  = "GCP_PROJECT_ID"
        value = var.project_id
      }
      # Firestore client project (Cloud Run ADC also resolves this; set explicitly to be safe).
      env {
        name  = "QUARKUS_GOOGLE_CLOUD_PROJECT_ID"
        value = var.project_id
      }
      # Consumed by %gcp.quarkus.http.cors.origins (native CORS; the custom filter is disabled).
      env {
        name  = "CORS_ALLOWED_ORIGINS"
        value = local.cors_allowed_origins
      }
      # coach.bedrock.api-mode — coach is out of the GCP slice but the key is real; harmless.
      env {
        name  = "COACH_BEDROCK_API_MODE"
        value = local.coach_bedrock_api_mode
      }
    }
  }

  # checkov:skip=CKV_GCP_102: Public API — the app enforces auth in-app via JWT validation; roles/run.invoker for allUsers is granted manually (see runbook)
  # checkov:skip=CKV_GCP_119: Binary Authorization not warranted for a single-developer project
}
