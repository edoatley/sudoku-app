# CI/CD Pipeline

## Overview

The pipeline uses GitHub Actions with a **consolidated workflow per branch type** pattern. Each workflow file contains all its jobs — CI checks and deployment — chained via `needs:` dependencies within a single workflow run. This guarantees that deployment is impossible if any CI check fails.

The critical design principle: **no cross-workflow dependencies**. Deployment is a job inside the same workflow as CI, not a separate workflow triggered independently.

---

## Workflow Files

```
.github/
  workflows/
    ci-checks.yml      # Reusable CI jobs (called by other workflows)
    ci-feature.yml     # Feature branch pushes
    ci-main.yml        # Main branch: CI → deploy → smoke tests
    ci-rc.yml          # RC branches: CI → integration → deploy → smoke tests
    ci-pr.yml          # Pull requests: full test suite + PR comment
    teardown.yml       # Manual environment teardown (workflow_dispatch)
    teardown-rc.yml    # Auto teardown on rc-* branch deletion
  actions/
    configure-aws-oidc/   # Assumes IAM role via OIDC
    setup-node/           # Node 22 + npm ci [+ Playwright]
    setup-java/           # Java 21 (Temurin) + Maven cache
    build-lambda-zip/     # Quarkus package + reproducible zip
    create-localstack-dynamodb/  # Creates DynamoDB tables in LocalStack
    terraform-validate/   # fmt check, init (no backend), validate
```

---

## Trigger Matrix

| Event | Branch | Workflow |
|-------|--------|----------|
| `push` | feature branches (not `main`, not `rc-*`) | `ci-feature.yml` |
| `push` | `main` | `ci-main.yml` |
| `push` | `rc-*` | `ci-rc.yml` |
| `pull_request` | any | `ci-pr.yml` |
| `workflow_dispatch` | any | `teardown.yml` |
| `delete` (branch) | `rc-*` | `teardown-rc.yml` |

---

## Job Dependency Chains

### Feature branches (`ci-feature.yml`)

```
ci (calls ci-checks.yml — ui-lint, backend-unit, infra — parallel)
  └── integration (Docker Compose + Playwright)
```

### Main branch (`ci-main.yml`)

```
ci (calls ci-checks.yml — ui-lint, backend-unit, infra — parallel)
  └── changes (detect backend/frontend file changes)
        └── build-backend (if backend changed)
              └── deploy (Terraform → Lambda → API Gateway → Cognito → Amplify)
                    └── smoke-test (API probes + Playwright vs live Amplify)
                          └── notify (step summary + artifact cleanup)
```

### RC branches (`ci-rc.yml`)

```
ci (calls ci-checks.yml — ui-lint, backend-unit, infra — parallel)
  └── integration (Docker Compose + Playwright)
        └── changes (detect backend/frontend file changes)
              └── build-backend (if backend changed)
                    └── deploy (Terraform → Lambda → API Gateway → Cognito → Amplify)
                          └── smoke-test (API probes + Playwright vs live Amplify)
                                └── notify (step summary + artifact cleanup)
```

### Pull requests (`ci-pr.yml`)

```
ui (lint + E2E Playwright) ─┐
backend (full Maven: unit   ├─ parallel ──► integration (Docker Compose + Playwright)
  + API + IT + coverage)   ─┘                    │
security (npm audit +                             └─► report (PR comment with results)
  OWASP dependency check) ──────────────────────────────────────────────────────────────► report
infra (Terraform validate) ─────────────────────────────────────────────────────────────► report
```

---

## Reusable Workflow: `ci-checks.yml`

Called by `ci-feature.yml`, `ci-main.yml`, and `ci-rc.yml` via `workflow_call`. Contains three parallel jobs:

| Job | What it does |
|-----|-------------|
| `ui-lint` | `npm run lint` in `ui/` |
| `backend-unit` | Maven verify with LocalStack DynamoDB service; posts JaCoCo + Surefire summary |
| `infra` | Terraform fmt check, init (no backend), validate |

**Rules for this file:**
- Never add a `push:` or `pull_request:` trigger — it must remain `workflow_call` only
- Callers must pass `secrets: inherit` so secrets are available if ever needed
- Adding a new shared CI check here automatically applies it to feature, main, and RC workflows

---

## Terraform Workspace Mapping

| Branch | Workspace | Environment var |
|--------|-----------|-----------------|
| `main` | `default` | `prod` |
| `rc-*` | sanitized branch name (max 32 chars) | sanitized branch name |

Sanitization: `tr '/' '-' \| tr '.' '-' \| cut -c1-32`

The `teardown-rc.yml` workflow uses identical sanitization — workspace names must match exactly for auto-teardown to work.

---

## Deployment Steps (main and rc-* branches)

1. **Detect changes** — `dorny/paths-filter` determines if backend (`backend/**`, `openapi.yaml`) or frontend (`ui/**`) files changed
2. **Build backend** (if backend changed) — Quarkus Lambda zip with normalized timestamps for reproducibility; uploaded as a 1-day artifact
3. **Terraform deploy**:
   - Assume IAM role via OIDC (`AWS_DEPLOY_ROLE_ARN` secret)
   - Download fresh Lambda zip, or fall back to S3-stored zip, or build on demand
   - `terraform init` → workspace select/create → `plan` → `apply`
   - Tighten API Gateway CORS to exact Amplify URL (post-apply, because the URL is only known after apply)
   - Tighten Cognito callback/logout URLs to exact Amplify URL
   - Trigger Amplify build (if frontend changed) and wait up to 10 minutes
4. **Smoke tests**:
   - API probes: health, puzzle generation, difficulty param, JWT rejection
   - Playwright integration tests against live Amplify URL with real Cognito tokens
5. **Notify** — post step summary; delete the lambda-zip artifact

---

## Secrets Required

| Secret | Used by |
|--------|---------|
| `AWS_DEPLOY_ROLE_ARN` | `ci-main`, `ci-rc`, `teardown`, `teardown-rc` — OIDC role assumption |
| `AMPLIFY_GITHUB_TOKEN` | Terraform plan — GitHub token for Amplify source connection |
| `GOOGLE_CLIENT_ID` | Terraform plan — Cognito social identity provider |
| `GOOGLE_CLIENT_SECRET` | Terraform plan — Cognito social identity provider |
| `SMOKE_TEST_USER_EMAIL` | Terraform plan + smoke-test — Cognito test user |
| `SMOKE_TEST_USER_PASSWORD` | Terraform plan + smoke-test — Cognito test user |
| `NVD_API_KEY` | `ci-pr` — OWASP dependency check NVD rate limit key |

---

## Integrity Rules

These rules must be followed to preserve the safety guarantees of this pipeline. Violating them reintroduces the race condition where broken code can be deployed.

### 1. Deploy must always be inside the same workflow as CI

Never create a separate workflow that deploys on `push` to `main` or `rc-*`. The safety guarantee comes from `needs:` chaining within a single workflow run — if CI and deploy are in separate workflows, they race.

### 2. The `ci` job must always be in `needs:` for the deploy job

In `ci-main.yml` and `ci-rc.yml`, the `deploy` job must list `ci` in its `needs:` array. Removing `ci` from `needs:` on the deploy job means CI failure would no longer block deployment.

Current deploy job needs in `ci-main.yml`:
```yaml
needs: [ci, changes, build-backend]
```

Current deploy job needs in `ci-rc.yml`:
```yaml
needs: [ci, integration, changes, build-backend]
```

### 3. The `if: always() && !failure() && !cancelled()` condition on deploy is load-bearing

The deploy job uses `always()` so it runs even when `build-backend` is skipped (no backend changes). But `!failure()` ensures it is skipped if any upstream job failed. Do not change this condition without understanding both sides.

### 4. Never add a `push` trigger to `ci-checks.yml`

`ci-checks.yml` must only have `on: workflow_call`. Adding a `push` trigger would cause it to run as a standalone workflow on every push, separate from the deploy chain — restoring the race condition.

### 5. Workspace name sanitization must stay in sync

The workspace name derivation in `ci-rc.yml` and `teardown-rc.yml` must use identical logic (`tr '/' '-' | tr '.' '-' | cut -c1-32`). If they diverge, teardown will attempt to destroy a workspace that doesn't match the one created during deploy.

### 6. Adding a new shared CI check

To add a check that applies to all branch types (feature, main, rc):
1. Add the job to `ci-checks.yml`
2. The job will automatically run in all three callers

To add a check only for PRs: add it to `ci-pr.yml`.

To add a check only before production deploys: add it after the `ci` job and before `deploy` in `ci-main.yml`, and add it to `needs:` on the `deploy` job.

### 7. OIDC permissions must be at workflow level, not job level

The `permissions: id-token: write` declaration in `ci-main.yml` and `ci-rc.yml` is at the top-level workflow scope. If moved to the job level, the reusable `ci-checks.yml` call will lose that permission context. Keep permissions at the workflow level for `ci-main.yml` and `ci-rc.yml`.

---

## Branch Protection (GitHub Settings)

The `main` branch should have branch protection configured with:

| Setting | Value |
|---------|-------|
| Require a pull request before merging | Yes |
| Required status checks | `CI + Deploy (main) / CI Checks` |
| Require branches to be up to date | Yes |
| Restrict who can push to matching branches | Appropriate team/bot restrictions |

The required status check name corresponds to the `ci` job in `ci-main.yml`. This means any direct push to `main` that fails CI will be visible as a failed required check — and since deploy is inside the same run, it will also not deploy.

---

## Adding a New Deployment Environment

To add a new environment type (e.g., `staging` on a `staging-*` branch pattern):

1. Create `.github/workflows/ci-staging.yml` following the same structure as `ci-rc.yml`
2. Set the trigger branch pattern to `staging-*`
3. Adjust workspace/environment naming logic as needed
4. Create `.github/workflows/teardown-staging.yml` following `teardown-rc.yml` with the same sanitization logic
5. Ensure workspace name sanitization matches between the two files

Do not modify `ci-checks.yml` or the existing workflows — the new environment is entirely self-contained in its own workflow files.
