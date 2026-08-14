# ---------------------------------------------------------------------------
# AI coach + image recognition — cross-cloud Bedrock credential access
# (gap D / gap E / CP-GCP-085).
#
# The coach and image-recognition services run the identical application code on
# GCP; they reach AWS Bedrock cross-cloud using a least-privilege AWS access key
# (Bedrock InvokeModel only) stored in Secret Manager. The secrets AND the runtime
# service accounts' read access to them are provisioned by
# scripts/infra/gcp/bedrock-cross-cloud.sh (a one-time cross-cloud step, mirroring how
# scripts/infra/gcp/bootstrap.sh owns the runtime SAs' datastore.user IAM) — never in Terraform,
# so the AWS key never enters Terraform state and the deploy SA needs no
# secret-level IAM-admin. Terraform only *mounts* the secrets as
# AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY env vars on the backend (cloud_run.tf)
# and image-recognition (image_recognition.tf) Cloud Run services when
# enable_coach = true.
#
# Rate-limit state lives in the Firestore coachRateLimits collection (TTL in
# firestore.tf per CP-GCP-022); no extra infra needed here.
# ---------------------------------------------------------------------------
