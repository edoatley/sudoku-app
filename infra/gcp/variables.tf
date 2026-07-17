variable "project_id" {
  description = "GCP project ID hosting the Sudoku infrastructure."
  type        = string
}

variable "region" {
  description = "GCP region for Cloud Run, Firestore, and Artifact Registry."
  type        = string
  default     = "us-central1"
}

variable "custom_domain" {
  description = "Custom domain for the GCP frontend (Firebase Hosting + Cloud DNS)."
  type        = string
  default     = "sudoku-gcp.edoatley.co.uk"
}

# ── Container images (built and pushed by CI to Artifact Registry) ──────────────

variable "backend_image" {
  description = "Artifact Registry image URI for the Quarkus backend Cloud Run service (e.g. us-central1-docker.pkg.dev/<project>/sudoku-backend/backend:<tag>)."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9-]+-docker\\.pkg\\.dev/.+/.+:.+$", var.backend_image))
    error_message = "backend_image must be a full Artifact Registry image URI (<region>-docker.pkg.dev/<project>/<repo>/<image>:<tag>)."
  }
}

variable "image_recognition_image_uri" {
  description = "Artifact Registry image URI for the image-recognition Cloud Run service."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9-]+-docker\\.pkg\\.dev/.+/.+:.+$", var.image_recognition_image_uri))
    error_message = "image_recognition_image_uri must be a full Artifact Registry image URI (<region>-docker.pkg.dev/<project>/<repo>/<image>:<tag>)."
  }
}

# ── Runtime service accounts (created manually — see docs/runbooks/gcp-manual-setup.md) ──

variable "run_service_account_email" {
  description = "Email of the manually-created backend Cloud Run runtime service account."
  type        = string
}

variable "image_recognition_service_account_email" {
  description = "Email of the manually-created image-recognition Cloud Run runtime service account."
  type        = string
}

# ── Identity Platform (configured manually; consumed here by value) ─────────────

variable "identity_platform_issuer" {
  description = "Identity Platform JWT issuer the backend validates (https://securetoken.google.com/<project_id>)."
  type        = string
  default     = ""
}

variable "identity_platform_audience" {
  description = "Identity Platform JWT audience the backend validates (the GCP project id)."
  type        = string
  default     = ""
}

# ── Cloud Run throttle / cost guards ────────────────────────────────────────────

variable "backend_max_instances" {
  description = "Cloud Run max instance count for the backend service (throttle / cost guard)."
  type        = number
  default     = 4
}

variable "backend_container_concurrency" {
  description = "Cloud Run per-instance request concurrency for the backend service (throttle guard)."
  type        = number
  default     = 40
}

variable "image_recognition_max_instances" {
  description = "Cloud Run max instance count for the image-recognition service."
  type        = number
  default     = 2
}

# ── Cost guardrail (Cloud Billing budget) ───────────────────────────────────────

variable "budget_billing_account" {
  description = "Billing account ID (XXXXXX-XXXXXX-XXXXXX) for the Cloud Billing budget. Leave empty to skip creating budget resources."
  type        = string
  default     = ""
}

variable "monthly_budget_usd" {
  description = "Monthly cost cap in USD for the Cloud Billing budget alert thresholds."
  type        = string
  default     = "25"
}

variable "budget_alert_email" {
  description = "Email address for budget threshold alerts (creates a Monitoring notification channel). Leave empty to alert billing admins + Pub/Sub only."
  type        = string
  default     = ""
}
