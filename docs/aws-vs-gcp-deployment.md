# AWS ↔ GCP: deploying and choosing a target

How the one codebase is deployed to each cloud, and **how you control which cloud a change goes to**.
This is the *operational / pipeline* comparison — for the *architectural* differences (auth edge,
persistence, CORS, Bedrock, identity derivation) see [`aws-vs-gcp-comparison.md`](aws-vs-gcp-comparison.md).
For the step-by-step GCP prod stand-up see the runbook's *Prod bring-up* section
(`docs/runbooks/gcp-manual-setup.md`).

## TL;DR — the target is chosen by branch name + workflow

There is **no runtime toggle**; the target is decided by *where you push* and *which workflow runs*.
The two clouds are independent deployments (one user is served from one cloud at a time), so a normal
change deploys to **one** of them:

| You do this | Cloud | Env | Trigger | Auto? |
|---|---|---|---|---|
| push to `main` | **AWS** | prod | `ci-deploy.yml` | ✅ automatic |
| push to `rc-*` | **AWS** | RC (per-branch workspace) | `ci-deploy.yml` | ✅ automatic |
| push to `rcg-*` | **GCP** | RC (per-branch workspace) | `deploy-gcp.yml` | ✅ automatic |
| run *Deploy GCP* `workflow_dispatch`, `workspace=default` | **GCP** | prod | `deploy-gcp.yml` | ▶️ manual |

**Key asymmetry:** AWS prod is **push-to-`main`** (continuous); **GCP prod is manual only**
(`workflow_dispatch`). A push to `main` does **not** deploy GCP. This is deliberate — AWS is the
established home; GCP prod is brought up intentionally (custom domain, invoker, DNS are manual steps).

Mnemonic: **`rc-` = AWS, `rcg-` = GCP** (the extra **g** is for Google). Both are ignored by the
other cloud's workflow (`ci-deploy.yml` watches `main`/`rc-*`; `deploy-gcp.yml` watches `rcg-*`).

## Deploy pipelines side by side

| Dimension | AWS (`ci-deploy.yml`) | GCP (`deploy-gcp.yml`) |
|---|---|---|
| Trigger | push `main`, `rc-*` | push `rcg-*`; `workflow_dispatch` (any workspace) |
| Prod deploy | automatic on `main` | manual dispatch, `workspace=default` |
| RC env | Terraform workspace per `rc-*` branch | Terraform workspace per `rcg-*` branch (name via `scripts/github/gcp-workspace-name.sh`, ≤30-char site id) |
| Auth to cloud | GitHub OIDC → assume-role | Workload Identity Federation → impersonate deploy SA |
| Backend build | container image → ECR | container image → Artifact Registry |
| What deploys | backend Lambda + image-rec Lambda + Amplify build (Terraform) | backend Cloud Run + image-rec Cloud Run + Firestore/Hosting (Terraform); frontend + smoke are separate jobs |
| Frontend | Amplify build hook | `deploy-frontend` job (`firebase deploy`); gated by `deploy_frontend` input / `GCP_DEPLOY_FRONTEND` var |
| Public reachability | API Gateway stage (always public) | `allUsers` `run.invoker` — Terraform for RC, **manual `grant-prod-invoker.sh` for prod** |
| Post-deploy smoke | `smoke-tests.yml` (Cognito `USER_PASSWORD_AUTH`) | `smoke` job (Identity Platform `signInWithPassword`); `run_smoke` input / `GCP_RUN_SMOKE` var |
| Custom domain | Amplify + Route53 (automatic on `main`) | Firebase Hosting + Cloud DNS, **manual** (`enable_custom_domain`, delegate + records scripts) |
| Teardown | `teardown-rc.yml` (delete `rc-*`), `teardown.yml` (dispatch) | `teardown-gcp.yml` (delete `rcg-*`, or dispatch) |

## Controlling a GCP deploy (the dispatch inputs)

GCP's `workflow_dispatch` (*Actions → Deploy GCP → Run workflow*) is phased — you choose exactly what
runs, so prod can be brought up in stages:

| Input | Default | Purpose |
|---|---|---|
| `workspace` | `default` | Terraform workspace (`default` = prod) |
| `deploy_cloud_run` | `false` | Build + deploy the backend service |
| `deploy_image_recognition` | `false` | Build + deploy the image-rec service |
| `enable_coach` | `false` | Mount the cross-cloud Bedrock secrets (needs runbook §6) |
| `deploy_frontend` | `false` | Build + `firebase deploy` the SPA (needs `VITE_FIREBASE_API_KEY`) |
| `enable_custom_domain` | `false` | Attach the Firebase custom domain (needs DNS delegated) |
| `run_smoke` | `true` | Post-deploy smoke; assert the env serves (needs the invoker granted) |

`rcg-*` pushes resolve these automatically (backend + image-rec + coach on; frontend/smoke opt-in via
`GCP_DEPLOY_FRONTEND` / `GCP_RUN_SMOKE` repo vars; custom domain off).

## What's shared vs per-cloud

- **Shared:** the application code, the single backend container image (`Dockerfile.jvm-lwa`), the UI
  source (built per-cloud with different `VITE_*`), and the `docs/`/`scripts/` tree. `scripts/infra`
  is namespaced `aws/` · `gcp/` · `shared/`.
- **Per-cloud:** `infra/aws/*.tf` vs `infra/gcp/*.tf`; the workflows; the identity/edge stack
  (Cognito+API Gateway vs Identity Platform+in-app); the persistence adapter (selected at runtime by
  `sudoku.persistence`, set to `firestore` by the `%gcp` profile).

## Running a deploy locally

- **AWS:** `scripts/infra/aws/deploy-local.sh [branch]` — mirrors `ci-deploy.yml` (Terraform
  plan+apply against the branch's workspace).
- **GCP:** no full local equivalent; use `workflow_dispatch`. Individual manual steps are scripted
  under `scripts/infra/gcp/` (see `scripts/infra/README.md`).

## See also

- **Architecture differences:** [`aws-vs-gcp-comparison.md`](aws-vs-gcp-comparison.md)
- **GCP prod stand-up (ordered):** `docs/runbooks/gcp-manual-setup.md` — *Prod bring-up* + *Ordering summary*
- **HLD multi-cloud section:** `docs/high-level-design.md` — *Multi-Cloud Deployment (AWS + GCP)*
