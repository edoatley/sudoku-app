# ---------------------------------------------------------------------------
# AI coach + image recognition — cross-cloud Bedrock credential access
# (gap D / gap E / CP-GCP-085).
#
# Image recognition always reaches AWS Bedrock cross-cloud this way; the coach does too by
# default, but can instead run on Vertex AI (Gemini, ADC, no keys) via coach_ai_provider = "vertex"
# (CP-GCP-090), which suppresses only the backend's mount below (SC-GCP-007).
#
# The secrets AND the runtime service accounts' read access to them are provisioned by
# scripts/infra/gcp/bedrock-cross-cloud.sh (a one-time cross-cloud step, mirroring how
# scripts/infra/gcp/bootstrap.sh owns the runtime SAs' datastore.user IAM) — never in Terraform,
# so the AWS key never enters Terraform state and the deploy SA needs no
# secret-level IAM-admin. Terraform only *mounts* the secrets as
# AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY env vars on the backend (cloud_run.tf, when
# enable_coach = true and coach_ai_provider != "vertex") and image-recognition
# (image_recognition.tf, when enable_coach = true).
#
# Rate-limit state lives in the Firestore coachRateLimits collection (TTL in
# firestore.tf per CP-GCP-022); no extra infra needed here.
# ---------------------------------------------------------------------------
