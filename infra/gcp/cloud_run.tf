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
      env {
        name  = "COACH_BEDROCK_API_MODE"
        value = local.coach_bedrock_api_mode
      }

      # Cross-cloud Bedrock credentials for the AI coach (gap D). Mounted as the standard AWS SDK
      # env vars so BedrockClientProducer's default credential chain resolves them with no code
      # change; region stays eu-west-2 (its default). Only wired when enable_coach = true so a
      # backend-only deploy without the secrets still applies. See runbook §6 + CP-GCP-085.
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

  # checkov:skip=CKV_GCP_102: Public API — the app enforces auth in-app via JWT validation; roles/run.invoker for allUsers is granted below for non-default (RC) workspaces, manually for prod (see runbook)
  # checkov:skip=CKV_GCP_119: Binary Authorization not warranted for a single-developer project
}

# Public network reachability for RC (non-default) workspaces so a browser (local or hosted UI) can
# reach the backend without a manual grant on every ephemeral deploy; the app still validates the
# JWT in-app. Prod (default) invoker stays manual (gap G1). @spec CP-GCP-014
resource "google_cloud_run_v2_service_iam_member" "backend_public" {
  count = var.deploy_cloud_run && !local.is_default ? 1 : 0

  project  = var.project_id
  location = var.region
  name     = google_cloud_run_v2_service.backend[0].name
  role     = "roles/run.invoker"
  member   = "allUsers"
}
