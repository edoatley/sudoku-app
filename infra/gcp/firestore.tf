# ---------------------------------------------------------------------------
# Firestore (Native) — DynamoDB equivalent.
# One database per workspace: the (default) database on the default workspace,
# a named sudoku{suffix} database otherwise. Data lives in collections
# games / players / leaderboard / coachRateLimits (created implicitly by the
# app on first write — Firestore collections are schemaless).
# ---------------------------------------------------------------------------
# @spec CP-GCP-020, CP-GCP-021, CP-GCP-023, CP-GCP-024

resource "google_firestore_database" "main" {
  project     = var.project_id
  name        = local.firestore_database_id
  location_id = var.region
  type        = "FIRESTORE_NATIVE"

  # PITR + delete protection on the production database only (parity with the
  # AWS PITR/deletion-protection split).
  point_in_time_recovery_enablement = local.is_default ? "POINT_IN_TIME_RECOVERY_ENABLED" : "POINT_IN_TIME_RECOVERY_DISABLED"
  delete_protection_state           = local.is_default ? "DELETE_PROTECTION_ENABLED" : "DELETE_PROTECTION_DISABLED"

  # Leave the production database in place on `terraform destroy`; allow RC/dev
  # databases to be torn down with their workspace.
  deletion_policy = local.is_default ? "ABANDON" : "DELETE"
}

# TTL on coachRateLimits.expiresAt — ephemeral per-user rate-limit counters
# auto-expire (parity with the AWS SudokuCoachRateLimits TTL). @spec CP-GCP-022
resource "google_firestore_field" "coach_rate_limit_ttl" {
  project    = var.project_id
  database   = google_firestore_database.main.name
  collection = "coachRateLimits"
  field      = "expiresAt"

  ttl_config {}
}

# Composite index on games (userId, status, endedAt) serving both game-lookup
# queries the app issues, so they never hit an unindexed-query error at runtime:
#   - findInProgress: userId == ? AND status == IN_PROGRESS   (uses the (userId, status) prefix)
#   - findHistory:    userId == ? AND status IN (SOLVED, ABANDONED) ORDER BY endedAt DESC
# Provisioned with the database (not gated on deploy_cloud_run) so it finishes
# building before Cloud Run serves its first query.
# @spec GL-GCP-007
resource "google_firestore_index" "games_user_status_ended_at" {
  project    = var.project_id
  database   = google_firestore_database.main.name
  collection = "games"

  fields {
    field_path = "userId"
    order      = "ASCENDING"
  }
  fields {
    field_path = "status"
    order      = "ASCENDING"
  }
  fields {
    field_path = "endedAt"
    order      = "DESCENDING"
  }
}
