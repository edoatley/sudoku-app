# GCP Manual Setup Runbook

Everything in this runbook is provisioned **by hand**, not by Terraform. This is a deliberate
choice: IAM, service accounts, Workload Identity Federation, Identity Platform, and networking are
the parts of GCP most worth understanding directly (and the parts most dangerous to let a tool
abstract). `infra/gcp/*.tf` references what you create here by value — service-account emails and
the Identity Platform issuer/audience — and provisions only application resources.

Run this **once per project**, after `scripts/infra/gcp/bootstrap.sh` (which creates the GCS state
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

> **Now automated:** `scripts/infra/gcp/bootstrap.sh` step `[5/5]` creates these three service
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
    --role="roles/datastore.user" --condition=None
done

# Secret Manager access for the interim cross-cloud Bedrock credential
# (grant at the secret level once the secret exists — see section 6)
```

### Public invocation of the Cloud Run services

The app is a public web API (auth is enforced in-app by JWT validation, not at the platform edge),
so `allUsers` may invoke the services. Non-default (`rcg-*`) workspaces get this binding from
Terraform (CP-GCP-014); **prod (`default`) is deliberately kept out of Terraform** and applied by an
idempotent script **after** the services first exist:

```bash
# Prod (default) services — run after the first prod `terraform apply`:
PROJECT_ID=<gcp-project-id> scripts/infra/gcp/grant-prod-invoker.sh
```

The script grants `allUsers` `roles/run.invoker` on the prod `sudoku` + `sudoku-image-recognition`
services (skipping any not deployed). The equivalent by hand:

```bash
for SVC in sudoku sudoku-image-recognition; do
  gcloud run services add-iam-policy-binding "${SVC}" \
    --region="${REGION}" \
    --member="allUsers" \
    --role="roles/run.invoker"
done
```

> Note: on non-default workspaces the service names carry the `-<workspace>` suffix (handled in TF).

### Deploy SA

Scoped to what CI actually does: build/push images, deploy Cloud Run, manage Firestore/Hosting/DNS/
budget, read/write the Terraform state bucket, and act-as the runtime SAs.

```bash
# roles/firebase.admin covers both adding Firebase to the project
# (google_firebase_project) and Hosting; roles/firebasehosting.admin alone cannot
# create the project resource. --condition=None avoids the interactive condition prompt.
for ROLE in \
  roles/run.admin \
  roles/artifactregistry.writer \
  roles/datastore.owner \
  roles/firebase.admin \
  roles/dns.admin \
  roles/billing.costsManager \
  roles/pubsub.admin \
  roles/iam.serviceAccountUser \
  roles/serviceusage.serviceUsageAdmin ; do
  gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
    --member="serviceAccount:${DEPLOY_SA}" \
    --role="${ROLE}" --condition=None
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

> **Now automated:** `scripts/infra/gcp/github-bootstrap.sh` performs this section, the deploy-SA
> project roles (from §2's *Deploy SA*), and sets the three GitHub secrets — idempotently and with
> inline explanations of each resource. The commands below are kept for reference / hand-running.

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

Add these GitHub Actions secrets:

| Secret | Value |
| --- | --- |
| `GCP_PROJECT_ID` | `${PROJECT_ID}` |
| `GCP_WIF_PROVIDER` | the provider resource name from 3d |
| `GCP_DEPLOY_SA_EMAIL` | `${DEPLOY_SA}` |
| `VITE_FIREBASE_API_KEY` | Firebase Web API key (public client id) — from the Identity Platform / Firebase console (§4). Only needed for the `deploy_frontend` job. |

The first three are set automatically by `scripts/infra/gcp/github-bootstrap.sh`; add `VITE_FIREBASE_API_KEY`
by hand once §4 is done. The frontend also uses `VITE_FIREBASE_AUTH_DOMAIN`
(`${PROJECT_ID}.firebaseapp.com`) and `VITE_FIREBASE_PROJECT_ID` (`${PROJECT_ID}`), which the
deploy workflow derives from `GCP_PROJECT_ID` — no extra secrets required.

---

## 4. Identity Platform + Google sign-in

The Cognito equivalent: social-only Google login for end users. Terraform consumes only the
resulting issuer/audience.

> **Mostly scripted.** `scripts/infra/gcp/identity-platform-bootstrap.sh` (also invoked by
> `scripts/infra/gcp/bootstrap.sh` step [6/6] when `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET` are set) enables
> Email/Password, adds the Google IdP from your OAuth client, and merges the authorized domains via
> the Identity Toolkit Admin API — `localhost`, `<project>.firebaseapp.com`, `<project>.web.app`, and
> the prod custom domain `${CUSTOM_DOMAIN:-sudoku-gcp.edoatley.co.uk}` (override/skip via
> `CUSTOM_DOMAIN`). Only steps **1** (the one-time "Enable Identity Platform"
> entitlement) and the **OAuth redirect-URI** paste in step 2 have no reliable API — the script
> **guides you through both interactively** (it waits and re-polls the API until Enable is detected,
> then asks you to confirm the redirect URI). The steps below are the full manual walkthrough /
> reference.

1. Enable Identity Platform: Console → **Identity Platform** → *Enable* (one-time, project-level).
2. Add the **Google** provider:
   - Console → Identity Platform → *Providers* → *Add a provider* → **Google**.
   - Reuse the existing Google OAuth client (the same `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`
     used for the AWS Cognito federation) or create a new OAuth client. Add the Identity Platform
     handler to that client's **Authorized redirect URIs** in the Google Cloud Console → *APIs &
     Services → Credentials*.
   - **Custom domain note:** the handler URI is always `https://${PROJECT_ID}.firebaseapp.com/__/auth/handler`
     — it does **not** change for the prod custom domain, and the app uses `signInWithPopup` (no
     `signInWithRedirect`), so **no extra OAuth redirect URI / JavaScript origin is needed** for
     `sudoku-gcp.edoatley.co.uk`. The only per-host requirement is that the domain is in Identity
     Platform's **authorized domains** — which `scripts/infra/gcp/identity-platform-bootstrap.sh`
     merges automatically (re-run it after the custom domain is live).
3. **Enable only the Google and Email/Password providers** — leave **Anonymous** (and every
   other provider) disabled. Email/Password exists solely for the CI smoke-test user (§5); the app
   UI offers Google only.
4. **Disable public sign-up.** Console → Identity Platform → *Settings* → *Security* / *User
   actions* → turn off **"Enable create (sign-up)"** for email/password. Now the `password`
   provider can only *sign in* pre-provisioned users — nobody can `createUserWithEmailAndPassword`.
   This is the primary control that makes the backend's `password` fallback safe. Defence-in-depth
   (all enforced regardless): the resolver accepts `password` only via a strict provider allow-list
   and namespaces it as `firebase:<uid>` (disjoint from Google `sub`s), and `AllowedUsersFilter`
   still requires the email to be on `app.allowed.emails`. See
   `docs/llds/user-management.md` — *Canonical userId = Google sub (hardened resolver)*.

The backend validates tokens against:

- **Issuer:** `https://securetoken.google.com/${PROJECT_ID}`
- **Audience:** `${PROJECT_ID}`
- **JWKS:** `https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com`

These are the values Terraform passes to Cloud Run as `identity_platform_issuer` /
`identity_platform_audience` variables (see `infra/gcp/variables.tf`).

---

## 5. CI smoke-test user (Identity Platform)

Cognito's `USER_PASSWORD_AUTH` has no direct equivalent; CI signs in via the Identity Platform REST
API. Because public sign-up is disabled (§4.4), the test user must be created **server-side by an
admin** — this is the only way a `password` account can exist:

```bash
# Create the test user server-side (admin) — Console → Identity Platform → Users → Add user,
# or the Admin SDK / accounts:signUp with an admin credential. Its email MUST be on
# app.allowed.emails (the authorization gate), and it is never used for real logins.
#
# CI then obtains an ID token by SIGNING IN (not signing up):
curl -s "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=<WEB_API_KEY>" \
  -H 'Content-Type: application/json' \
  -d '{"email":"<smoke-user>","password":"<smoke-pass>","returnSecureToken":true}'
```

The resulting token's `firebase.sign_in_provider` is `password`, so the backend resolves its
`userId` to `firebase:<uid>` (namespaced away from Google users). Create/reset the user and write the
creds to `scripts/.env.local` with `scripts/infra/gcp/create-smoke-user.sh` (CP-GCP-032).

**Automated post-deploy smoke.** `deploy-gcp.yml`'s `smoke` job mints a token (via
`scripts/github/gcp-smoke-token.sh`) and asserts `GET /players/me` → 200 and `POST /games` → 201
against the deployed backend, so a run isn't "green" until the env actually serves. It needs three
GitHub **secrets**:

| Secret | Value |
| --- | --- |
| `VITE_FIREBASE_API_KEY` | Firebase Web API key (already used by the frontend deploy) |
| `SMOKE_TEST_USER_EMAIL` | the smoke user's email (on `app.allowed.emails`) |
| `SMOKE_TEST_USER_PASSWORD` | the smoke user's password |

Controls: `workflow_dispatch` has a `run_smoke` input (default **true**); `rcg-*` pushes run it only
when repo var `GCP_RUN_SMOKE=true`. The smoke needs the `allUsers` invoker granted first — automatic
for `rcg-*` (Terraform), manual for prod. **On the very first prod deploy, dispatch with
`run_smoke=false`** (the service doesn't exist yet, so the invoker can't be granted), then run
`scripts/infra/gcp/grant-prod-invoker.sh` and re-dispatch with `run_smoke=true` to validate.

---

## 6. Interim cross-cloud Bedrock credential (path A)

Until AI inference migrates to Vertex AI (deferred), the GCP backend and image-recognition services
call AWS Bedrock cross-cloud using a **least-privilege** AWS access key (Bedrock `InvokeModel` only).
This spans both clouds (create the AWS IAM user + key, then store the key in GCP Secret Manager and
grant the runtime SAs read access), so it is handled by a single idempotent script rather than by
hand or in Terraform:

```bash
# Needs BOTH an authenticated aws (IAM-capable) and gcloud (Owner/Editor) session.
PROJECT_ID=<gcp-project-id> scripts/infra/gcp/bedrock-cross-cloud.sh
```

It creates the IAM user `sudoku-bedrock-cross-cloud` (scoped to `bedrock:InvokeModel` on the model
list), mints an access key, stores it as the two Secret Manager secrets `bedrock-aws-access-key-id`
and `bedrock-aws-secret-access-key`, and grants `roles/secretmanager.secretAccessor` on both to the
`sudoku-run`, `sudoku-image-recognition-run`, and `sudoku-github-deploy` service accounts. Rotate
later with `ROTATE=y PROJECT_ID=<id> scripts/infra/gcp/bedrock-cross-cloud.sh`.

Terraform only *mounts* those secrets as `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` on the Cloud
Run services when `enable_coach = true` (the AWS SDK's default credential chain then resolves them
with no application code change); it does **not** manage the secrets or their IAM. Then apply with
`-var enable_coach=true` (rcg-* pushes set this automatically; the two secret ids are overridable via
`bedrock_access_key_secret_id` / `bedrock_secret_key_secret_id`).

> Security note: this is a long-lived key — the exact thing WIF avoids — and is the main reason the
> Vertex AI migration (CP-GCP-090) exists. It is scoped to Bedrock only; rotate it.

---

## 6b. Custom domain DNS delegation (prod)

The GCP frontend's custom domain `sudoku-gcp.edoatley.co.uk` is served by a Cloud DNS managed zone
that Terraform creates in the **default** workspace when `enable_custom_domain=true`. The parent zone
`edoatley.co.uk` lives in **AWS Route53**, so the subdomain must be delegated to the Cloud DNS
nameservers — a one-time, cross-cloud step (needs both `gcloud` and `aws` sessions + `PARENT_ZONE_ID`):

```bash
# After the prod apply (workspace=default, enable_custom_domain=true) has created the Cloud DNS zone:
PARENT_ZONE_ID=<edoatley.co.uk Route53 zone id> PROJECT_ID=<gcp-project-id> \
  scripts/infra/gcp/delegate-dns.sh
```

It reads the Cloud DNS nameservers (`gcloud`) and UPSERTs the `NS` delegation record in Route53 (via
the generic `shared/delegate-dns.sh`).

Then add the Firebase-required **A/AAAA + TXT** records *inside* the Cloud DNS zone. Firebase computes
these asynchronously (surfaced by the `custom_domain_required_dns_records` Terraform output), so they
are applied by a script rather than Terraform — `for_each` over a value Firebase hasn't computed yet
would fail the first apply, and the exact record/TXT format is best verified live:

```bash
# After the prod apply; re-run if the terraform output was still empty (records populate async):
PROJECT_ID=<gcp-project-id> scripts/infra/gcp/apply-custom-domain-dns.sh
```

It reads the terraform output and UPSERTs each A/AAAA/TXT record set into the `sudoku-gcp` zone
(idempotent). Firebase then issues the managed TLS cert out of band; verify with
`dig +short sudoku-gcp.edoatley.co.uk` and the Firebase console (custom domain shows "Connected").

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

## 8. RC environments on GCP (`rcg-*` branches)

Pushing a branch named `rcg-*` triggers `deploy-gcp.yml` to build+push the backend image and
`terraform apply` a **per-branch workspace** (name derived by
`scripts/github/gcp-workspace-name.sh`, length-capped so the Firebase site_id stays ≤ 30 chars).
This is the GCP analogue of the AWS `rc-*` flow; AWS is untouched.

- **Backend + infra** deploy automatically on every push.
- **Frontend** deploys only when the repo **variable** `GCP_DEPLOY_FRONTEND` is set to `true`
  (Settings → Secrets and variables → Actions → Variables). It needs the `VITE_FIREBASE_API_KEY`
  secret and Identity Platform (§4) in place first; leave it unset to deploy backend-only.
- **Public invocation:** each new workspace's Cloud Run service needs the `allUsers` invoker
  binding (§2 *public invocation*) before it is reachable — the app still enforces auth in-app.
- **Post-deploy smoke:** set repo var `GCP_RUN_SMOKE=true` to have `rcg-*` pushes run the `smoke`
  job (mint token → assert the backend serves — §5). Needs the smoke secrets; off by default.
- **Teardown:** deleting the `rcg-*` branch triggers `teardown-gcp.yml`, which
  `terraform destroy`s that workspace and deletes it (or run it via **workflow_dispatch** with an
  explicit `workspace` input to tear down any non-`default` workspace manually). Shared Artifact
  Registry repos and service accounts are left intact. `delete`- and `workflow_dispatch`-triggered
  workflows only run from the default branch, so **teardown is active only once
  `deploy-gcp.yml`/`teardown-gcp.yml` are on `main`.**

## Ordering summary

1. `scripts/infra/gcp/bootstrap.sh` (state bucket, APIs, Artifact Registry)
2. `scripts/infra/gcp/github-bootstrap.sh` (deploy-SA roles incl. `artifactregistry.writer`, WIF, secrets)
3. This runbook §1–§4 (SAs, IAM, WIF, Identity Platform) — **before** the first apply
4. `cd infra/gcp && terraform init && terraform apply` (or push an `rcg-*` branch — §8)
5. `scripts/infra/gcp/grant-prod-invoker.sh` — §2 *public invocation* for prod (needs the services first)
6. §5–§6 as the smoke tests / AI features are wired up
7. **Prod custom domain:** apply with `enable_custom_domain=true`, then
   `scripts/infra/gcp/delegate-dns.sh` (§6b) to delegate `sudoku-gcp.edoatley.co.uk`
