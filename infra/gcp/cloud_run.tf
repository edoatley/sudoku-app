# ---------------------------------------------------------------------------
# Backend — Quarkus API on Cloud Run (Lambda equivalent).
# Runs as a manually-created runtime service account; validates Identity
# Platform JWTs in-app. Public invocation (roles/run.invoker for allUsers) is
# granted by hand, not here — see docs/runbooks/gcp-manual-setup.md.
# ---------------------------------------------------------------------------

resource "google_cloud_run_v2_service" "backend" {
  name     = "sudoku${local.suffix}"
  location = var.region
  ingress  = "INGRESS_TRAFFIC_ALL"

  deletion_protection = local.is_default

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

      env {
        name  = "CORS_ALLOWED_ORIGINS"
        value = local.cors_allowed_origins
      }
      env {
        name  = "FIRESTORE_PROJECT_ID"
        value = var.project_id
      }
      env {
        name  = "FIRESTORE_DATABASE"
        value = google_firestore_database.main.name
      }
      env {
        name  = "GAMES_COLLECTION"
        value = "games"
      }
      env {
        name  = "PLAYERS_COLLECTION"
        value = "players"
      }
      env {
        name  = "LEADERBOARD_COLLECTION"
        value = "leaderboard"
      }
      env {
        name  = "COACH_RATE_LIMIT_COLLECTION"
        value = "coachRateLimits"
      }
      env {
        name  = "COACH_BEDROCK_API_MODE"
        value = local.coach_bedrock_api_mode
      }
      env {
        name  = "OIDC_ISSUER_URL"
        value = var.identity_platform_issuer
      }
      env {
        name  = "OIDC_AUDIENCE"
        value = var.identity_platform_audience
      }
      env {
        name  = "AI_COACH_ENABLED"
        value = local.ai_coach_enabled ? "true" : "false"
      }
    }
  }

  # checkov:skip=CKV_GCP_102: Public API — the app enforces auth in-app via JWT validation; roles/run.invoker for allUsers is granted manually (see runbook)
  # checkov:skip=CKV_GCP_119: Binary Authorization not warranted for a single-developer project
}
