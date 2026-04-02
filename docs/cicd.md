# CI/CD Pipeline

## Overview

The pipeline uses GitHub Actions with two primary workflow files — one for feature/PR/Dependabot branches and one unified deploy workflow for `main` and `rc-*` branches.

**Key design principles:**
- **Deploy is inside the same workflow as CI** — `needs:` chaining guarantees deployment cannot happen if any test fails.
- **Dependabot branches get a lightweight path** — only the ecosystem that changed is tested; integration tests are always skipped.
- **Integration tests run natively** — `quarkus:dev` + `npm run dev` instead of Docker builds, saving ~5 minutes per run.
- **Significant shell logic lives in `scripts/github/`** — scripts are runnable locally for faster debugging.

---

## Workflow Files

```
.github/
  workflows/
    ci.yml             # All non-main/rc-* branches + pull requests
    ci-deploy.yml      # main and rc-* branches: CI gate → build → deploy → smoke tests
    smoke-tests.yml    # Reusable: API probes + Playwright against live Amplify
    teardown.yml       # Manual environment teardown (workflow_dispatch)
    teardown-rc.yml    # Auto teardown on rc-* branch deletion
    security-audit.yml # Weekly Trivy vulnerability scan
    claude.yml         # Claude Code integration (issue/PR comments)
  actions/
    configure-aws-oidc/         # Assumes IAM role via OIDC
    setup-node/                 # Node 22 + npm ci [+ Playwright]
    setup-java/                 # Java 21 (Temurin) + Maven cache
    build-lambda-zip/           # Quarkus package + reproducible zip
    create-localstack-dynamodb/ # Creates DynamoDB tables in LocalStack
    terraform-validate/         # fmt check, init (no backend), validate
    integration-tests/          # Native quarkus:dev + npm dev + Playwright

scripts/github/
    amplify-post-deploy.sh    # Post-Terraform: CORS, Cognito, Amplify build trigger
    api-smoke-tests.sh        # HTTP probes against the live API Gateway
    resolve-environment.sh    # Derives workspace/environment/is_main from branch name
    terraform-plan.sh         # Parameterised terraform plan (phase 1 & 2, RC vars)
```

---

## Trigger Matrix

| Event | Branch | Workflow |
|-------|--------|----------|
| `push` | feature / Dependabot branches (not `main`, not `rc-*`) | `ci.yml` |
| `pull_request` | any | `ci.yml` |
| `push` | `main` or `rc-*` | `ci-deploy.yml` |
| `workflow_dispatch` | any | `teardown.yml` |
| `delete` (branch) | `rc-*` | `teardown-rc.yml` |
| schedule (weekly Mon) | — | `security-audit.yml` |

---

## Job Dependency Chains

### Feature branches and PRs (`ci.yml`)

```
detect-changes
  ├── ci-npm  (skipped for Dependabot unless ui/** changed)
  ├── ci-maven (skipped for Dependabot unless backend/** changed)
  ├── ci-infra (skipped for Dependabot unless infra/** or .github/** changed)
  └── integration (ALWAYS skipped for Dependabot; needs ci-npm + ci-maven)
        └── report (PR comment — only on pull_request events)
```

**Dependabot branches:** `detect-changes` checks `github.actor == 'dependabot[bot]'`. Only the affected ecosystem's job runs; `integration` is always skipped. A Dependabot npm PR runs only `ci-npm` (~3 min vs ~15 min previously).

### Main and RC branches (`ci-deploy.yml`)

```
gate-ui ──┐
           ├── changes
gate-backend ──┘    ├── build-backend (if backend changed)
           │        │
           └── build-image-recognition
                         │
                    deploy (Terraform 2-phase → Lambda → Amplify → Cognito)
                         │
                    smoke-test (API probes + Playwright vs live Amplify)
                         │
                    notify (step summary + artifact cleanup)
```

No integration tests pre-deploy — they ran on the PR that was merged.

---

## Integration Tests — Native Approach

Integration tests (`.github/actions/integration-tests/action.yml`) no longer use Docker Compose. Instead:

1. **LocalStack** runs as a GitHub Actions service container (same as unit tests)
2. **Backend:** `./mvnw quarkus:dev` started in background — uses Maven cache, ready in ~60–90s
3. **Frontend:** `npm run dev --port 5174` started in background with `VITE_MOCK_API=false VITE_SKIP_AUTH=true`
4. **Playwright:** runs against `http://localhost:5174`

The Quarkus `%dev` profile already configures LocalStack at `localhost:4566` and disables OIDC auth, so no backend config changes are needed.

**To run locally (matching CI exactly):**
```bash
# 1. Start LocalStack
docker run -d -p 4566:4566 -e SERVICES=dynamodb localstack/localstack

# 2. Create DynamoDB tables
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
  bash .github/actions/create-localstack-dynamodb/run.sh   # or use the action manually

# 3. Start backend in dev mode
cd backend
CORS_ALLOWED_ORIGINS=http://localhost:5174 \
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
  ./mvnw quarkus:dev -Dquarkus.http.host=0.0.0.0 -Ddebug=false &

# 4. Start UI dev server
cd ../ui
VITE_API_URL=http://localhost:8080/api/v1 VITE_MOCK_API=false VITE_SKIP_AUTH=true VITE_DEV_TOOLS=true \
  npm run dev -- --port 5174 &

# 5. Run integration tests
npx playwright test --config playwright.integration.config.js
```

---

## Deployment Steps (main and rc-* branches)

1. **CI gate** — `gate-ui` (lint + unit) and `gate-backend` (Maven + LocalStack) run in parallel
2. **Detect changes** — `dorny/paths-filter` determines if backend (`backend/**`, `openapi.yaml`) or frontend (`ui/**`) files changed
3. **Build backend** (if backend changed) — Quarkus Lambda zip; uploaded as a 1-day artifact
4. **Build image recognition** — Docker build + ECR push with branch-prefixed tag
5. **Terraform deploy** (two phases):
   - `scripts/github/resolve-environment.sh` — derives workspace/environment from branch
   - Phase 1: all resources except domain association (`exclude_amplify_domain=true` / `exclude_amplify_beta_domain=true`)
   - Phase 2: full plan including domain (may take up to 40 min for ACM cert)
   - `scripts/github/terraform-plan.sh` handles both phases with RC var-file injection
   - `scripts/github/amplify-post-deploy.sh` — tightens CORS/Cognito, triggers Amplify build
6. **Smoke tests** — `smoke-tests.yml` reusable workflow (see below)
7. **Notify** — step summary with re-run instructions; deletes lambda-zip artifact

---

## Smoke Tests (`smoke-tests.yml`)

Reusable workflow called after every deploy. Also dispatchable manually via `workflow_dispatch`.

Steps:
1. Lambda warm-up (RC only — handles cold starts)
2. **API smoke tests** — `scripts/github/api-smoke-tests.sh` (health, generate, 401 rejections)
3. Wait for Amplify deployment to complete
4. Wait for custom domain to resolve
5. AWS OIDC auth + Cognito token acquisition
6. Image recognition smoke test (POST `/api/v1/puzzles/import` with real fixture)
7. Playwright integration tests against live Amplify URL

**To run API smoke tests locally against the deployed environment:**
```bash
# Get the API Gateway URL from the last deployment's step summary, then:
bash scripts/github/api-smoke-tests.sh https://<api-id>.execute-api.eu-west-2.amazonaws.com/prod
```

---

## Terraform Workspace Mapping

| Branch | Workspace | Environment var |
|--------|-----------|-----------------:|
| `main` | `default` | `prod` |
| `rc-*` | sanitized branch name (max 32 chars) | sanitized branch name |

Sanitization: `tr '/' '-' | tr '.' '-' | cut -c1-32`

`teardown-rc.yml` uses identical sanitization — workspace names must match exactly.

To derive the workspace name for any branch locally:
```bash
bash scripts/github/resolve-environment.sh
# or for a specific branch:
GITHUB_REF_NAME=rc-my-feature bash scripts/github/resolve-environment.sh
```

---

## Secrets Required

| Secret | Used by |
|--------|---------|
| `AWS_DEPLOY_ROLE_ARN` | `ci-deploy`, `teardown`, `teardown-rc` — OIDC role assumption |
| `AMPLIFY_GITHUB_TOKEN` | Terraform plan — GitHub token for Amplify source connection |
| `GOOGLE_CLIENT_ID` | Terraform plan — Cognito social identity provider |
| `GOOGLE_CLIENT_SECRET` | Terraform plan — Cognito social identity provider |
| `SMOKE_TEST_USER_EMAIL` | Terraform plan + smoke-test — Cognito test user |
| `SMOKE_TEST_USER_PASSWORD` | Terraform plan + smoke-test — Cognito test user |
| `LOCALSTACK_AUTH_TOKEN` | `ci.yml`, `ci-deploy.yml` — LocalStack service auth |
| `RC_COGNITO_WEB_CLIENT_ID` | `ci-deploy.yml` — RC Terraform var |
| `RC_COGNITO_SMOKE_CLIENT_ID` | `ci-deploy.yml` — RC Terraform var |

---

## Local Testing Guide

### Unit tests (backend)
```bash
# Requires LocalStack running
docker run -d -p 4566:4566 -e SERVICES=dynamodb localstack/localstack
cd backend
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
  ./mvnw verify -DskipITs=false
```

### Unit tests (frontend)
```bash
cd ui
npm run test:coverage
```

### E2E tests (frontend, mocked backend)
```bash
cd ui
npm run test:e2e
```

### Integration tests (full-stack, locally)
See the **Integration Tests — Native Approach** section above.

### API smoke tests (against deployed environment)
```bash
bash scripts/github/api-smoke-tests.sh <api-gateway-url>
```

### Terraform plan (dry-run, no apply)
```bash
# Set required env vars first
export TF_VAR_github_token=...
export TF_VAR_google_client_id=...
# etc.

# Resolve your workspace
bash scripts/github/resolve-environment.sh

# Then plan
bash scripts/github/terraform-plan.sh \
  --environment prod \
  --is-main     true \
  --phase       1 \
  --out         /tmp/tfplan-dry-run
```

---

## Integrity Rules

### 1. Deploy must always be inside the same workflow as CI

Never create a separate workflow that deploys on `push` to `main` or `rc-*`. The safety guarantee comes from `needs:` chaining — if CI and deploy are in separate workflows, they race and broken code can be deployed.

### 2. The gate jobs must be in `needs:` for the deploy job

In `ci-deploy.yml`, the `deploy` job must list `gate-ui` and `gate-backend` in its `needs:` array (directly or transitively). Currently:
```yaml
needs: [gate-ui, gate-backend, changes, build-backend, build-image-recognition]
```

### 3. The `if: always() && !failure() && !cancelled()` condition on deploy is load-bearing

The deploy job uses `always()` so it runs even when `build-backend` is skipped (no backend changes). But `!failure()` ensures it is skipped if any upstream job failed. Do not change this condition without understanding both sides.

### 4. Workspace name sanitization must stay in sync

`ci-deploy.yml` and `teardown-rc.yml` both call `resolve-environment.sh` (or must use the same sanitization logic). If they diverge, teardown will attempt to destroy a workspace with a different name than the one created during deploy.

### 5. Integration tests are skipped for Dependabot

The `integration` job in `ci.yml` conditions on `needs.detect-changes.outputs.is_dependabot == 'false'`. This must remain — removing it will restore the expensive Docker build path for all Dependabot PRs.

### 6. JWT tokens must not be passed as shell arguments

Cognito JWT ID tokens exceed the OS `ARG_MAX` limit. In `smoke-tests.yml`, the Authorization header is written to a temp file and passed via `curl -H @/tmp/auth-header.txt`. Never revert this to `-H "Authorization: Bearer ${TOKEN}"`.

---

## Branch Protection (GitHub Settings)

The `main` branch should have branch protection configured with:

| Setting | Value |
|---------|-------|
| Require a pull request before merging | Yes |
| Required status checks | `CI / UI — Lint & Unit Tests`, `CI / Backend — Unit Tests` |
| Require branches to be up to date | Yes |

Note: required status check names correspond to the job `name:` fields in `ci.yml`. Update these in GitHub settings if job names change.

---

## Adding a New Deployment Environment

To add a new environment type (e.g., `staging-*`):

1. `resolve-environment.sh` already handles any non-main branch — no changes needed there
2. Add the new branch pattern to `ci-deploy.yml`'s `on.push.branches`
3. Create `.github/workflows/teardown-staging.yml` following `teardown-rc.yml`, using `resolve-environment.sh` for consistent workspace naming
4. If staging needs different Terraform vars than RC, extend `terraform-plan.sh` with a `--phase` or a new `--mode` argument
