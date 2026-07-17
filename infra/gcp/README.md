# Sudoku — GCP Infrastructure (Terraform)

The GCP facet of the Cloud Platform, at behavioural parity with `infra/aws/`, on the GCP free tier
(`us-central1`). See the design in `docs/llds/cloud-platform.md` (GCP Resources) and
`docs/high-level-design.md` (Multi-Cloud Deployment).

> **Scope: infrastructure scaffolding.** This provisions the GCP platform and keeps it
> `validate`/`plan`-clean. It does **not** make the app run end-to-end on GCP — the backend
> Firestore adapter, frontend Firebase Auth path, and cross-cloud Bedrock wiring are separate
> future arrows. See the LLD *Scope of the GCP facet* note.

## Architecture

```text
GitHub ──WIF──> Cloud Run (backend: Quarkus)  ──> Firestore (Native)
                Cloud Run (image-recognition)  ──> (Bedrock cross-cloud, interim)
Firebase Hosting (React SPA) ── Cloud DNS (sudoku-gcp.edoatley.co.uk) + Google-managed TLS
Identity Platform (Google IdP) ── manual
Cloud Billing budget ── Pub/Sub alerts
```

| AWS                          | GCP                                               |
| ---------------------------- | ------------------------------------------------- |
| Lambda                       | Cloud Run                                         |
| DynamoDB                     | Firestore (Native)                                |
| API Gateway + JWT authorizer | Cloud Run direct + in-app JWT                     |
| Cognito                      | Identity Platform (manual)                        |
| Amplify                      | Firebase Hosting                                  |
| Route53 + ACM                | Cloud DNS + Google-managed TLS                    |
| AWS Budgets                  | Cloud Billing budget + Pub/Sub                    |
| S3 backend / ECR             | GCS backend / Artifact Registry                   |
| GitHub OIDC → IAM role       | Workload Identity Federation → deploy SA (manual) |

## Files

| File                   | Contents                                                                  |
| ---------------------- | ------------------------------------------------------------------------- |
| `terraform.tf`         | `google` / `google-beta` providers, GCS backend, `default_labels`         |
| `main.tf`              | Workspace locals (`is_default` / `is_rc` / `suffix`), project data source |
| `variables.tf`         | All input variables                                                       |
| `outputs.tf`           | Service URLs, Firestore db, Hosting site, DNS name servers                |
| `cloud_run.tf`         | Backend Cloud Run service                                                 |
| `image_recognition.tf` | Image-recognition Cloud Run service                                       |
| `firestore.tf`         | Firestore database (named per workspace) + TTL                            |
| `firebase_hosting.tf`  | Firebase project, Hosting site, custom domain                             |
| `dns.tf`               | Cloud DNS managed zone                                                    |
| `budgets.tf`           | Billing budget + Pub/Sub alert topic                                      |

**No `iam.tf` and no `identity_platform.tf`** — service accounts, IAM bindings, Workload Identity
Federation, and Identity Platform are provisioned by hand: **`docs/runbooks/gcp-manual-setup.md`**.

## First-time setup

```bash
# 1. One-time prerequisites (state bucket, APIs, Artifact Registry repos)
PROJECT_ID=<your-project> bash scripts/infra/gcp-bootstrap.sh

# 2. Manual identity/network layer (SAs, IAM, WIF, Identity Platform) — the learning surface
#    Follow docs/runbooks/gcp-manual-setup.md

# 3. Provision
cd infra/gcp
terraform init
terraform plan \
  -var "project_id=<your-project>" \
  -var "backend_image=us-central1-docker.pkg.dev/<project>/sudoku-backend/backend:latest" \
  -var "image_recognition_image_uri=us-central1-docker.pkg.dev/<project>/sudoku-image-recognition/ir:latest" \
  -var "run_service_account_email=sudoku-run@<project>.iam.gserviceaccount.com" \
  -var "image_recognition_service_account_email=sudoku-image-recognition-run@<project>.iam.gserviceaccount.com"
terraform apply
```

## Workspaces

Same model as AWS: `default` = production; non-default workspaces carry a `-<workspace>` suffix and
an isolated named Firestore database. There is **no `rc-shared`** — Identity Platform is a single
manual per-project config and Firestore isolates per workspace. GCP has no request-rate throttle;
load is bounded by Cloud Run max-instances × concurrency (see the LLD's deliberate-deviation note).

## Cost

Everything targets the free tier: Cloud Run scale-to-zero, Firestore free daily quota (per-project
— shared across named databases), Firebase Hosting free tier, Cloud DNS (one zone). The billing
budget (default workspace, when a billing account is supplied) alerts at 80% actual / 100% forecast.
