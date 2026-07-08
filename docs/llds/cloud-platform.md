# Cloud Platform

**Created**: 2026-04-18
**Status**: Complete

## Context and Current State

The Cloud Platform component is the AWS infrastructure substrate that provisions, wires, and delivers all runtime services. It contains no domain logic — its job is to make everything else accessible and connected. All infrastructure is managed by Terraform in the `infra/` directory.

The React frontend is a separate component; see `docs/llds/react-frontend.md`. The coupling point between the two is the set of `VITE_*` environment variables that Terraform injects into the Amplify build — documented in both LLDs.

Files: all `infra/*.tf`.

## Terraform Workspace Strategy

The infrastructure uses Terraform workspaces for environment isolation with selective resource sharing:

| Workspace | Purpose | Key Differences |
| --- | --- | --- |
| `default` | Production | Owns Lambda zip bucket, Cognito pool, DynamoDB (with PITR), Route53 zones, custom domain |
| `rc-*` | Release candidates | Shares Lambda zip bucket + Cognito pool (`rc-shared`); own DynamoDB; beta subdomain |
| `rc-shared` | Shared RC infrastructure | Owns the Cognito pool referenced by all `rc-*` workspaces |
| Other | Feature/dev branches | Shares Lambda zip bucket; own Cognito pool + DynamoDB; no custom domain |

Resource naming uses `local.suffix` (`""` for default, `-{workspace}` for others) to avoid collisions across workspaces.

Key locals:

```hcl
is_default = terraform.workspace == "default"
is_rc      = startswith(terraform.workspace, "rc-")
suffix     = local.is_default ? "" : "-${terraform.workspace}"
```

## AWS Resources

### Compute

**Java Lambda (`sudoku{suffix}`):**

| Property | Value |
| --- | --- |
| Runtime | Java 25 |
| Handler | `io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest` |
| Memory | 512 MB |
| Timeout | 8 seconds |
| Architecture | x86_64 |
| SnapStart | Enabled (published versions) |
| Deployment | S3 ZIP (`sudoku-lambda-zip-{account_id}/{workspace}/function.zip`) |

SnapStart pre-initializes the JVM snapshot on publish, eliminating Java cold starts. The Lambda is published on every deploy; the `live` alias always points to the latest published version. API Gateway invokes the alias, not the function directly.

**Image Recognition Lambda (`sudoku-image-recognition{suffix}`):**

| Property | Value |
| --- | --- |
| Runtime | Container image (Python 3.14 + Pillow) |
| Memory | 512 MB |
| Timeout | 60 seconds |
| Image | ECR `sudoku-image-recognition:{branch}-{sha}` |

Container image required for Pillow (native C extensions). No SnapStart — Python cold starts managed via warmup probe (`GET /api/v1/ai/image-to-puzzle/warmup`).

### API Gateway (HTTP v2)

One API (`sudoku{suffix}`) routes to both Lambda functions.

**Public routes (no auth):**

| Route | Target |
| --- | --- |
| `$default` | Java Lambda (catches `/puzzles/*`, `/health`, `/dev/hint-demo`) |
| `GET /api/v1/ai/image-to-puzzle/warmup` | Image Recognition Lambda |

`$default` is a catch-all at the gateway level — it forwards any unmatched path to the Java Lambda without JWT validation. `/dev/data/*` used to hit this route too: `DevDataResource` ran unauthenticated full table scans over Games and Players with no build-profile guard, exposing every user's PII — see `docs/planning/infra-review.md` finding H1. That resource is now deleted (moved to JWT+group-gated `/admin/data/*`), so `/dev/data/*` 404s from the Lambda in every deployed environment.

`/dev/hint-demo` (`DevResource`) is **not** profile-guarded and stays reachable through `$default` in every deployed environment, including production. This is deliberate, not an oversight: the Java Lambda is built once (`quarkus.profile=prod`) and that single artifact is shared by every Terraform workspace — there is no per-workspace backend build. An `@IfBuildProfile` guard would remove it everywhere, including RC/beta, where `VITE_DEV_TOOLS=true` still shows the demo-technique menu in the frontend and depends on this endpoint. It carries no user data (a canned puzzle grid per technique), so leaving it universally reachable is an accepted, low-risk trade-off — unlike the Games/Players Scan it replaced no PII is exposed.

**JWT-protected routes:**

| Route | Target |
| --- | --- |
| `POST /api/v1/ai/coach` | Java Lambda |
| `POST /api/v1/ai/image-to-puzzle` | Image Recognition Lambda |
| `POST /api/v1/games` | Java Lambda |
| `POST /api/v1/games/from-image` | Java Lambda |
| `GET /api/v1/games/{gameId}` | Java Lambda |
| `PATCH /api/v1/games/{gameId}` | Java Lambda |
| `GET /api/v1/games/current` | Java Lambda |
| `GET /api/v1/players/me` | Java Lambda |
| `GET /api/v1/admin/data/games` | Java Lambda |
| `GET /api/v1/admin/data/players` | Java Lambda |

The JWT authorizer validates Cognito tokens (issuer URL + audience = web client ID) — this only proves the caller is *some* authenticated user. The `/admin/data/*` routes carry an additional group check (`administrators` Cognito group) enforced in the Lambda by `AdminAuthorizationFilter`; API Gateway has no concept of Cognito groups. See `docs/llds/user-management.md` — Admin Authorization. Route precedence: specific routes beat `$default`.

**Throttling:** burst=50 req, rate=25 req/sec (configurable via variables).

**CloudWatch Logs:** JSON-format access logs, 7-day retention (default workspace), 3-day (others).

### CORS Two-Step Pattern

CORS and Cognito callback URLs cannot be set to exact values at `terraform apply` time because the Amplify URL is only known after Amplify is created — a circular dependency. The solution:

1. Terraform applies **baseline wildcard** origins (`http://localhost:5173`)
2. Post-apply CI workflow calls `aws apigatewayv2 update-api` to tighten to the exact Amplify URL
3. `ignore_changes = [cors_configuration]` (API GW) and `ignore_changes = [callback_urls, logout_urls]` (Cognito) prevent the next `terraform apply` from reverting the tightened values

If a workspace is torn down and recreated, step 2 must be re-run — the tightened values are not stored in Terraform state.

### Storage

**DynamoDB:**

| Table | Partition Key | Sort Key | PITR |
| --- | --- | --- | --- |
| `SudokuGames{suffix}` | `userId` (String) | `gameId` (String) | Default workspace only |
| `SudokuPlayers{suffix}` | `userId` (String) | — | Default workspace only |

Both tables use `PAY_PER_REQUEST` (on-demand) billing. AWS-managed encryption (no CMK).

**S3 (Lambda artifacts):**

- Bucket: `sudoku-lambda-zip-{account_id}` (owned by default workspace, referenced by others)
- 30-day object expiration, no versioning, all public access blocked
- Non-default workspaces reference this bucket via data source; each workspace uploads to its own key prefix

**ECR:**

- Repository `sudoku-image-recognition` created by `scripts/bootstrap.sh` (outside Terraform)
- Shared across all workspaces; referenced by Terraform via data source
- Tag convention: `{branch}-{sha}`, `{branch}-latest`

### Authentication (Cognito)

**User Pool (default and non-RC workspaces):**

- Social-only: Google OAuth identity provider; no native username/password sign-up
- Admin-created users only (smoke-test user created by Terraform)
- Auto-verified: email; schema: email (required), name (optional)
- MFA: off; Cognito domain: `sudoku-auth{suffix}.auth.eu-west-2.amazoncognito.com`
- `aws_cognito_user_group "administrators"` — members may reach `/admin/*` endpoints (see `docs/llds/user-management.md` — Admin Authorization). Provisioned empty; adding the human admin is a manual one-time step (their federated Google username is unknown until first login) — see `scripts/infra/add-admin.sh`.

**RC Shared Pool (`rc-shared` workspace):**

- Single pool `sudoku-rc` shared by all `rc-*` workspaces
- Avoids per-branch Cognito pool proliferation — Google OAuth requires whitelisted redirect URIs, and one pool means one set of URIs to manage

**App Clients:**

| Client | Purpose | Secret | Auth Flows |
| --- | --- | --- | --- |
| `sudoku-web{suffix}` | Browser SPA | None (public) | Authorization code + PKCE |
| `sudoku-smoke-test{suffix}` | CI smoke tests | Generated | Username/password |

Token validity: web — 1 hour access/ID, 30 days refresh. Smoke test — 1 hour access/ID, 1 day refresh.

### Frontend Hosting (Amplify)

**Build spec (inline in Terraform):**

```yaml
version: 1
frontend:
  phases:
    preBuild: cd ui && npm ci
    build: npm run build
  artifacts:
    baseDirectory: ui/dist
    files: '**/*'
  cache:
    paths: ui/node_modules/**/*
```

**Auto-build: disabled.** CI triggers `aws amplify start-job` after `terraform apply`. Required because `VITE_*` variables are baked into the Vite bundle at build time — auto-build on push would use stale values before Terraform has applied the latest infrastructure.

**Environment variables injected at Amplify build time:**

| Variable | Source |
| --- | --- |
| `VITE_API_URL` | API Gateway invoke URL + `/api/v1` |
| `VITE_COGNITO_USER_POOL_ID` | Cognito pool ID (workspace-appropriate) |
| `VITE_COGNITO_CLIENT_ID` | Web client ID |
| `VITE_COGNITO_DOMAIN` | Cognito domain |
| `VITE_MOCK_API` | `"false"` |
| `VITE_DEV_TOOLS` | `"false"` (default), `"true"` (all others) |

### DNS & TLS

| Domain | Workspace | Zone Owner |
| --- | --- | --- |
| `sudoku.edoatley.co.uk` | default | default workspace |
| `sudoku-beta.edoatley.co.uk` | all RC | default workspace |

Route53 zones are created once in the default workspace. RC workspaces read zone IDs via `terraform_remote_state`. NS records delegated manually from parent zone `edoatley.co.uk` (separate AWS account).

TLS certificates provisioned automatically by Amplify via ACM. No manual certificate management.

### IAM

**Java Lambda role (`SudokuLambdaExecRole{suffix}`):**

- `AWSLambdaBasicExecutionRole` (CloudWatch Logs)
- DynamoDB Games: `GetItem, PutItem, UpdateItem, Query, Scan`
- DynamoDB Players: `GetItem, PutItem, UpdateItem, Scan` (no Query — single-key access only)

`Scan` on both tables is used solely by `AdminDataResource` (the admin data browser, `docs/llds/user-management.md` — Admin Authorization). These grants live on the same role as every other game/player operation — there is no separate, more restricted role for admin-only actions, so a bug in the admin group check is the last line of defence against a full table scan by any authenticated user. Isolating this would require a dedicated admin Lambda; out of scope for now.

**Image Recognition Lambda role (`SudokuImageRecognitionExecRole{suffix}`):**

- `AWSLambdaBasicExecutionRole`
- `bedrock:InvokeModel` on `eu.anthropic.claude-haiku-4-5-20251001-v1:0` inference-profile ARN and its corresponding foundation-model ARN (required for regional routing)

## Terraform Project Structure

### File Organisation

| File | Contents |
| --- | --- |
| `main.tf` | Provider config, `default_tags`, `local` values |
| `variables.tf` | All input variables |
| `outputs.tf` | Exported values (zone IDs, API URL, etc.) |
| `lambda.tf` | Java Lambda function, alias, S3 artifact |
| `image_recognition_lambda.tf` | Image Recognition Lambda (container image) |
| `api_gateway.tf` | HTTP API v2, routes, integrations, JWT authorizer, throttling |
| `dynamodb.tf` | SudokuGames and SudokuPlayers tables |
| `cognito.tf` | User Pool, App Clients, Google IdP, Cognito domain |
| `cognito-rc-shared.tf` | Shared RC Cognito pool (rc-shared workspace only) |
| `amplify.tf` | Amplify app, branch, environment variables |
| `iam.tf` | Lambda execution roles and inline policies |
| `domain.tf` | Route53 zones, Amplify domain associations, ACM certificates |

### Naming Convention

All resource names use `local.suffix` (`""` for default, `-{workspace}` for others):

```hcl
resource "aws_lambda_function" "java" {
  function_name = "sudoku${local.suffix}"
  ...
}
```

### Tagging

All resources receive default tags via the provider `default_tags` block:

```hcl
provider "aws" {
  default_tags {
    tags = {
      Project     = "Sudoku"
      ManagedBy   = "Terraform"
      Environment = var.environment
    }
  }
}
```

### Infrastructure Standards

- **API Gateway:** Always use API Gateway v2 (HTTP API). Never use REST API v1.
- **CORS:** Use the two-step pattern described in the CORS Two-Step Pattern section.
- **Amplify auto-build:** Always set `enable_auto_build = false` — CI triggers builds explicitly after Terraform has applied correct `VITE_*` variables.
- **Sensitive variables:** Google credentials and CI secrets are passed as `sensitive = true` Terraform variables, injected via GitHub Actions secrets.

## CI/CD Deployment Pipeline

### Workflow Files

```
.github/
  workflows/
    ci.yml             # Feature/PR/Dependabot branches
    ci-deploy.yml      # main and rc-* branches: CI gate → build → deploy → smoke tests
    smoke-tests.yml    # Reusable: API probes + Playwright against live Amplify
    teardown.yml       # Manual environment teardown (workflow_dispatch)
    teardown-rc.yml    # Auto teardown on rc-* branch deletion
    security-audit.yml # Weekly Trivy vulnerability scan
  actions/
    configure-aws-oidc/         # Assumes IAM role via OIDC
    setup-node/                 # Node 22 + npm ci [+ Playwright]
    setup-java/                 # Java 25 (Temurin) + Maven cache
    build-lambda-zip/           # Quarkus package + reproducible zip
    create-localstack-dynamodb/ # Creates DynamoDB tables in LocalStack
    terraform-validate/         # fmt check, init, validate
    integration-tests/          # Native quarkus:dev + npm dev + Playwright

scripts/github/
    amplify-post-deploy.sh    # Post-Terraform: CORS, Cognito, Amplify build trigger
    api-smoke-tests.sh        # HTTP probes against the live API Gateway
    resolve-environment.sh    # Derives workspace/environment/is_main from branch name
    terraform-plan.sh         # Parameterised terraform plan (phase 1 & 2, RC vars)
```

### Trigger Matrix

| Event | Branch | Workflow |
| --- | --- | --- |
| `push` | feature/Dependabot (not main, not rc-*) | `ci.yml` |
| `pull_request` | any | `ci.yml` |
| `push` | `main` or `rc-*` | `ci-deploy.yml` |
| `workflow_dispatch` | any | `teardown.yml` |
| `delete` (branch) | `rc-*` | `teardown-rc.yml` |
| schedule (weekly Mon) | — | `security-audit.yml` |

### Job Dependency Chain (ci-deploy.yml)

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

### Deployment Steps

1. **CI gate** — `gate-ui` (lint + unit) and `gate-backend` (Maven + LocalStack) run in parallel
2. **Detect changes** — `dorny/paths-filter` determines if backend or image recognition files changed
3. **Build backend** (if backend changed) — Quarkus Lambda zip; uploaded as 1-day artifact
4. **Build image recognition** — Docker build + ECR push with `{branch}-{sha}` tag
5. **Terraform deploy** (two phases):
   - Phase 1: all resources except domain association (avoids 40-min ACM cert wait on every deploy)
   - Phase 2: full plan including domain
   - `scripts/github/resolve-environment.sh` derives workspace from branch name
   - `scripts/github/terraform-plan.sh` handles both phases with RC var-file injection
   - `scripts/github/amplify-post-deploy.sh` tightens CORS/Cognito, triggers Amplify build
6. **Smoke tests** — API probes + Playwright against live Amplify URL
7. **Notify** — step summary with re-run instructions; deletes lambda-zip artifact

### Terraform Workspace Mapping

| Branch | Workspace | Sanitization |
| --- | --- | --- |
| `main` | `default` | — |
| `rc-*` | sanitized branch (max 32 chars) | `tr '/' '-' | tr '.' '-' | cut -c1-32` |

`teardown-rc.yml` uses identical sanitization — workspace names must match exactly or teardown will target the wrong environment.

### Smoke Tests (smoke-tests.yml)

Reusable workflow called after every deploy. Also dispatchable via `workflow_dispatch`.

1. Lambda warm-up (RC only — handles cold starts)
2. API smoke tests (`api-smoke-tests.sh` — health, generate, 401 rejections)
3. Wait for Amplify deployment to complete
4. Wait for custom domain to resolve
5. AWS OIDC auth + Cognito token acquisition
6. Image recognition smoke test (POST `/api/v1/ai/image-to-puzzle` with real fixture)
7. Playwright integration tests against live Amplify URL

### Required Secrets

| Secret | Purpose |
| --- | --- |
| `AWS_DEPLOY_ROLE_ARN` | OIDC role assumption for deploy/teardown |
| `AMPLIFY_GITHUB_TOKEN` | Amplify GitHub source connection |
| `GOOGLE_CLIENT_ID` | Cognito Google IdP |
| `GOOGLE_CLIENT_SECRET` | Cognito Google IdP |
| `SMOKE_TEST_USER_EMAIL` | Cognito smoke-test user |
| `SMOKE_TEST_USER_PASSWORD` | Cognito smoke-test user |
| `LOCALSTACK_AUTH_TOKEN` | LocalStack service auth |
| `RC_COGNITO_WEB_CLIENT_ID` | RC Terraform var |
| `RC_COGNITO_SMOKE_CLIENT_ID` | RC Terraform var |

### Integrity Rules

1. **Deploy must be inside the same workflow as CI.** Never create a separate deploy workflow — `needs:` chaining is the only guarantee broken code cannot be deployed.
2. **Gate jobs must be in `needs:` for the deploy job.** `gate-ui` and `gate-backend` must be directly or transitively required by `deploy`.
3. **The `if: always() && !failure() && !cancelled()` condition on deploy is load-bearing.** `always()` lets deploy run when `build-backend` is skipped (no backend changes); `!failure()` blocks deploy if any gate fails. Do not change without understanding both sides.
4. **Workspace name sanitization must stay in sync** between `ci-deploy.yml` and `teardown-rc.yml`. Divergence causes teardown to target a non-existent workspace.
5. **Integration tests are skipped for Dependabot.** The `integration` job conditions on `is_dependabot == 'false'`; removing this restores expensive Docker builds for all Dependabot PRs.
6. **JWT tokens must not be passed as shell arguments.** Cognito ID tokens exceed OS `ARG_MAX`. In smoke tests, the Authorization header is written to a temp file and passed via `curl -H @/tmp/auth-header.txt`.

### Adding a New Deployment Environment

1. `resolve-environment.sh` already handles any non-main branch — no changes needed.
2. Add the new branch pattern to `ci-deploy.yml`'s `on.push.branches`.
3. Create `.github/workflows/teardown-<type>.yml` following `teardown-rc.yml`.
4. If the new environment needs different Terraform vars, extend `terraform-plan.sh`.

## Observed Design Decisions

| Decision | Chosen | Alternatives Considered | Rationale |
| --- | --- | --- | --- |
| CORS two-step | Baseline wildcard + post-apply tighten | Terraform-only | Amplify URL unknown at apply time; circular dependency resolved by post-apply script |
| Amplify auto-build disabled | CI triggers `amplify start-job` manually | Amplify webhook auto-build | `VITE_*` vars baked at build time; auto-build uses stale values |
| SnapStart for Java Lambda | Enabled on published versions | Provisioned Concurrency | SnapStart is free; Provisioned Concurrency costs per-second even when idle |
| RC Cognito pool sharing | Single `sudoku-rc` pool via `rc-shared` workspace | Per-branch Cognito pool | Google OAuth requires fixed redirect URIs; one pool = one set of URIs |
| Pay-per-request DynamoDB | `BILLING_MODE = "PAY_PER_REQUEST"` | Provisioned throughput | Personal project; bursty/low traffic; cheaper at this scale |
| Lambda `live` alias | API GW targets alias, not function directly | Direct function invocation | Alias enables zero-downtime version promotion and rollback without API GW changes |
| ECR outside Terraform | Bootstrap script creates repository | Terraform manages ECR | Avoids chicken-and-egg: Terraform needs the image URI to create the Lambda, but the image can't exist without a repository |

## Technical Debt & Inconsistencies

- CloudWatch has no alarms or metric filters on Lambda errors — no automated notification when either Lambda starts returning 5xx responses. (Deferred — separate feature work.)

## Behavioral Quirks

- The Amplify branch name defaults to `terraform.workspace` if `var.git_branch` is not set. If the workspace name does not match the actual git branch (e.g., workspace `rc-v2` but branch `release/v2`), Amplify will target the wrong branch.
- RC workspaces share one Cognito pool but each has its own API Gateway and Lambda. A user authenticated against one RC environment's Cognito can present that token to any other RC environment's API — the JWT is valid across all RC APIs.
- The `$default` API Gateway route sends all unmatched paths to the Java Lambda. Unrecognised routes return Java's 404/405, not API Gateway's native response.
- `ignore_changes` on CORS and callback URLs means Terraform state drifts from actual configuration after every post-apply tightening. `terraform plan` will always show these values as "no changes" even when the live config differs from the baseline.

## References

- `infra/main.tf`, `infra/terraform.tf`, `infra/variables.tf`, `infra/outputs.tf`
- `infra/lambda.tf`, `infra/api_gateway.tf`, `infra/dynamodb.tf`
- `infra/cognito.tf`, `infra/cognito-rc-shared.tf`
- `infra/amplify.tf`, `infra/domain.tf`, `infra/iam.tf`
- `infra/image_recognition_lambda.tf`
- See also: `docs/llds/react-frontend.md` (frontend app delivered by Amplify)
- Depends on: nothing (provisions all other components)
- Depended on by: all components (runtime environment)
