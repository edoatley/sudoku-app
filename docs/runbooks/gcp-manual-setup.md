# GCP Manual Setup Runbook

Everything in this runbook is provisioned **by hand**, not by Terraform. This is a deliberate
choice: IAM, service accounts, Workload Identity Federation, Identity Platform, and networking are
the parts of GCP most worth understanding directly (and the parts most dangerous to let a tool
abstract). `infra/gcp/*.tf` references what you create here by value — service-account emails and
the Identity Platform issuer/audience — and provisions only application resources.

Run this **once per project**, after `scripts/infra/gcp-bootstrap.sh` (which creates the GCS state
bucket, enables APIs, and creates the Artifact Registry repos) and **before** `terraform apply`.

## Prerequisites

```bash
export PROJECT_ID="<your-gcp-project-id>"
export PROJECT_NUMBER="$(gcloud projects describe "${PROJECT_ID}" --format='value(projectNumber)')"
export REGION="us-central1"
export GITHUB_ORG="edoatley"
export GITHUB_REPO="sudoku-app"

gcloud config set project "${PROJECT_ID}"
```

Enabled APIs are a bootstrap prerequisite. If you skipped the bootstrap script, enable them by hand:

```bash
gcloud services enable \
  run.googleapis.com artifactregistry.googleapis.com firestore.googleapis.com \
  firebase.googleapis.com firebasehosting.googleapis.com identitytoolkit.googleapis.com \
  dns.googleapis.com billingbudgets.googleapis.com pubsub.googleapis.com \
  secretmanager.googleapis.com iamcredentials.googleapis.com sts.googleapis.com \
  cloudresourcemanager.googleapis.com
```

---

## 1. Service accounts

> **Now automated:** `scripts/infra/gcp-bootstrap.sh` step `[5/5]` creates these three service
> accounts (and the §2 `roles/datastore.user` binding). The commands below are kept for reference /
> for running by hand. The broader deploy-SA roles, `run.invoker`, WIF, and Identity Platform below
> are still manual.

Three service accounts: two runtime SAs (one per Cloud Run service, least privilege) and one deploy
SA that GitHub Actions impersonates via Workload Identity Federation.

```bash
# Backend Cloud Run runtime SA
gcloud iam service-accounts create sudoku-run \
  --display-name="Sudoku backend Cloud Run runtime"

# Image-recognition Cloud Run runtime SA
gcloud iam service-accounts create sudoku-image-recognition-run \
  --display-name="Sudoku image-recognition Cloud Run runtime"

# CI/CD deploy SA (impersonated from GitHub via WIF)
gcloud iam service-accounts create sudoku-github-deploy \
  --display-name="Sudoku GitHub Actions deploy"
```

Capture the emails (these become Terraform variables / GitHub secrets):

```bash
export RUN_SA="sudoku-run@${PROJECT_ID}.iam.gserviceaccount.com"
export IR_SA="sudoku-image-recognition-run@${PROJECT_ID}.iam.gserviceaccount.com"
export DEPLOY_SA="sudoku-github-deploy@${PROJECT_ID}.iam.gserviceaccount.com"
```

---

## 2. IAM role bindings (least privilege)

### Runtime SAs

Both runtime SAs need Firestore access; the backend and image-recognition SAs additionally read the
interim Bedrock credential from Secret Manager.

```bash
# Firestore data access for both runtime SAs
for SA in "${RUN_SA}" "${IR_SA}"; do
  gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
    --member="serviceAccount:${SA}" \
    --role="roles/datastore.user"
done

# Secret Manager access for the interim cross-cloud Bedrock credential
# (grant at the secret level once the secret exists — see section 6)
```

### Public invocation of the Cloud Run services

The app is a public web API (auth is enforced in-app by JWT validation, not at the platform edge),
so `allUsers` may invoke the services. Grant this **after** the services exist (post first
`terraform apply`), or Terraform's plan will show the binding as external drift:

```bash
for SVC in sudoku sudoku-image-recognition; do
  gcloud run services add-iam-policy-binding "${SVC}" \
    --region="${REGION}" \
    --member="allUsers" \
    --role="roles/run.invoker"
done
```

> Note: on non-default workspaces the service names carry the `-<workspace>` suffix.

### Deploy SA

Scoped to what CI actually does: build/push images, deploy Cloud Run, manage Firestore/Hosting/DNS/
budget, read/write the Terraform state bucket, and act-as the runtime SAs.

```bash
for ROLE in \
  roles/run.admin \
  roles/artifactregistry.writer \
  roles/datastore.owner \
  roles/firebasehosting.admin \
  roles/dns.admin \
  roles/billing.costsManager \
  roles/pubsub.admin \
  roles/iam.serviceAccountUser \
  roles/serviceusage.serviceUsageConsumer ; do
  gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
    --member="serviceAccount:${DEPLOY_SA}" \
    --role="${ROLE}"
done

# Terraform state bucket access (bucket-scoped, not project-wide)
gcloud storage buckets add-iam-policy-binding "gs://sudoku-tf-state-gcp" \
  --member="serviceAccount:${DEPLOY_SA}" \
  --role="roles/storage.objectAdmin"
```

> `roles/iam.serviceAccountUser` lets the deploy SA set the runtime SAs on the Cloud Run services.
> Keep this list as tight as the deploys require — widen only when an apply fails on a missing
> permission, and record why.

---

## 3. Workload Identity Federation (GitHub → GCP)

The GCP counterpart to the AWS GitHub OIDC provider + assume-role. Establishes trust so GitHub
Actions tokens can impersonate the deploy SA — **no long-lived keys**.

```bash
# 3a. Pool
gcloud iam workload-identity-pools create github-pool \
  --location="global" \
  --display-name="GitHub Actions pool"

export WIF_POOL_ID="$(gcloud iam workload-identity-pools describe github-pool \
  --location=global --format='value(name)')"

# 3b. OIDC provider for GitHub's token issuer.
# The attribute-condition restricts the trust to THIS repo — the analogue of the
# AWS trust policy's `sub` StringLike on repo:edoatley/sudoku-app:*.
gcloud iam workload-identity-pools providers create-oidc github-provider \
  --location="global" \
  --workload-identity-pool="github-pool" \
  --display-name="GitHub OIDC" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository == '${GITHUB_ORG}/${GITHUB_REPO}'"

# 3c. Let identities from this repo impersonate the deploy SA
gcloud iam service-accounts add-iam-policy-binding "${DEPLOY_SA}" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/${WIF_POOL_ID}/attribute.repository/${GITHUB_ORG}/${GITHUB_REPO}"

# 3d. The provider resource name — this is the GCP_WIF_PROVIDER GitHub secret
gcloud iam workload-identity-pools providers describe github-provider \
  --location=global --workload-identity-pool=github-pool \
  --format='value(name)'
```

Add three GitHub Actions secrets:

| Secret | Value |
| --- | --- |
| `GCP_PROJECT_ID` | `${PROJECT_ID}` |
| `GCP_WIF_PROVIDER` | the provider resource name from 3d |
| `GCP_DEPLOY_SA_EMAIL` | `${DEPLOY_SA}` |

---

## 4. Identity Platform + Google sign-in

The Cognito equivalent: social-only Google login for end users. Provisioned by hand; Terraform
consumes only the resulting issuer/audience.

1. Enable Identity Platform: Console → **Identity Platform** → *Enable* (one-time, project-level).
2. Add the **Google** provider:
   - Console → Identity Platform → *Providers* → *Add a provider* → **Google**.
   - Reuse the existing Google OAuth client (the same `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`
     used for the AWS Cognito federation) or create a new OAuth client. Add the Identity Platform
     handler to that client's **Authorized redirect URIs** in the Google Cloud Console → *APIs &
     Services → Credentials*.
3. Enable **Email/Password** as a provider **only** to support the CI smoke-test user (section 5).
   The app UI still offers Google only.

The backend validates tokens against:

- **Issuer:** `https://securetoken.google.com/${PROJECT_ID}`
- **Audience:** `${PROJECT_ID}`
- **JWKS:** `https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com`

These are the values Terraform passes to Cloud Run as `identity_platform_issuer` /
`identity_platform_audience` variables (see `infra/gcp/variables.tf`).

---

## 5. CI smoke-test user (Identity Platform)

Cognito's `USER_PASSWORD_AUTH` has no direct equivalent; CI signs in via the Identity Platform REST
API. Create a dedicated test user (never used for real logins):

```bash
# Create an email/password user via the Admin API (or Console → Identity Platform → Users → Add user)
# Then CI obtains an ID token with:
curl -s "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=<WEB_API_KEY>" \
  -H 'Content-Type: application/json' \
  -d '{"email":"<smoke-user>","password":"<smoke-pass>","returnSecureToken":true}'
```

Store `<WEB_API_KEY>`, `<smoke-user>`, `<smoke-pass>` as GitHub secrets for the GCP smoke test.

---

## 6. Interim cross-cloud Bedrock credential (path A)

Until AI inference migrates to Vertex AI (deferred), the GCP backend calls AWS Bedrock cross-cloud.
Store a **least-privilege** AWS access key (Bedrock `InvokeModel` only) in Secret Manager and grant
the runtime SAs read access:

```bash
printf '%s' '<aws-access-key-json>' | gcloud secrets create bedrock-aws-credentials \
  --data-file=- --replication-policy=automatic

for SA in "${RUN_SA}" "${IR_SA}"; do
  gcloud secrets add-iam-policy-binding bedrock-aws-credentials \
    --member="serviceAccount:${SA}" \
    --role="roles/secretmanager.secretAccessor"
done
```

> Security note: this is a long-lived key — the exact thing WIF avoids — and is the main reason the
> Vertex AI migration (CP-GCP-090) exists. Rotate it, and scope the AWS IAM user to Bedrock only.

---

## 7. Optional: private networking (NOT built by default)

Cloud Run reaches Firestore over Google's managed public API at zero cost, so no VPC is required.
To make egress private later (a learning exercise), create a VPC + Serverless VPC Access connector
and attach it to the Cloud Run services:

```bash
gcloud compute networks create sudoku-vpc --subnet-mode=custom
gcloud compute networks subnets create sudoku-subnet \
  --network=sudoku-vpc --region="${REGION}" --range=10.8.0.0/28
gcloud compute networks vpc-access connectors create sudoku-connector \
  --region="${REGION}" --subnet=sudoku-subnet
# Then set the connector on each Cloud Run service (vpc_access block) and route egress through it.
```

This adds a small ongoing connector cost and is intentionally left unbuilt (CP-GCP-091).

---

## Ordering summary

1. `scripts/infra/gcp-bootstrap.sh` (state bucket, APIs, Artifact Registry)
2. This runbook §1–§4 (SAs, IAM, WIF, Identity Platform) — **before** the first apply
3. `cd infra/gcp && terraform init && terraform apply`
4. This runbook §2 *public invocation* binding (needs the services to exist first)
5. §5–§6 as the smoke tests / AI features are wired up
