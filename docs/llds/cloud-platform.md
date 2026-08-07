# Cloud Platform

**Created**: 2026-04-18
**Status**: Complete

## Context and Current State

The Cloud Platform component is the infrastructure substrate that provisions, wires, and delivers all runtime services. It contains no domain logic — its job is to make everything else accessible and connected. The platform is realised on two clouds at behavioural parity: **AWS** (Terraform in `infra/aws/`) and **GCP** (Terraform in `infra/gcp/`). The sections below are organised as two facets — the AWS facet (spec prefix `CP-INFRA-*`) and the GCP facet (spec prefix `CP-GCP-*`). Shared multi-cloud intent (parity, one-cloud-at-a-time / cutover-not-active-active, the AWS→GCP service mapping) lives in the HLD's *Multi-Cloud Deployment* section; cross-cloud identity continuity is detailed under *GCP Resources → Cross-Cloud Identity Continuity* below.

The React frontend is a separate component; see `docs/llds/react-frontend.md`. The coupling point between the two is the set of `VITE_*` environment variables that Terraform injects into the frontend build (Amplify on AWS, Firebase Hosting on GCP) — documented in both LLDs.

Files: all `infra/aws/*.tf` and `infra/gcp/*.tf`.

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
| Runtime | Container image (`Dockerfile.jvm-lwa` — Java 25 HTTP fast-jar + AWS Lambda Web Adapter) |
| Memory | 512 MB |
| Timeout | 8 seconds |
| Architecture | x86_64 |
| SnapStart | Not used — unsupported for container-image Lambda functions |
| Deployment | ECR (`sudoku-backend:{branch}-{sha}`) |

This is the same image Cloud Run runs on GCP; the Lambda Web Adapter extension bridges the Runtime API to the same HTTP server, inert outside a Lambda execution environment. The Lambda is published on every deploy; the `live` alias always points to the latest published version. API Gateway invokes the alias, not the function directly.

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

### CORS / Callback URL Configuration

API Gateway CORS origins are set directly in `api_gateway.tf` and the Lambda's `CORS_ALLOWED_ORIGINS` env var to the known static custom domains (`sudoku.edoatley.co.uk` / `sudoku-beta.edoatley.co.uk` / `localhost`) — fully Terraform-managed, no post-apply tightening step, no `ignore_changes`. The raw `*.amplifyapp.com` URL is intentionally unsupported at both layers (referencing `aws_amplify_app`/`aws_amplify_branch` from `api_gateway.tf` would create a dependency cycle, since Amplify's `VITE_API_URL` depends on the API Gateway endpoint).

Cognito callback/logout URLs are the one remaining two-step case, since the exact Amplify branch URL is only known after Amplify creates it:

1. Terraform applies a baseline URL list (`http://localhost:5173`, custom domain)
2. Post-apply CI workflow (`amplify-post-deploy.sh`) calls `aws cognito-idp update-user-pool-client` to add the exact Amplify branch URL
3. `ignore_changes = [callback_urls, logout_urls]` prevents the next `terraform apply` from reverting the added values

If a workspace is torn down and recreated, step 2 must be re-run — the added callback URLs are not stored in Terraform state.

`teardown-rc.yml` mirrors this for removal: before running `terraform destroy`, it captures the deleted branch's `amplify_default_url` (the branch-unique `*.amplifyapp.com` URL — **not** `amplify_app_url`, which for `rc-*` resolves to the shared `sudoku-beta.edoatley.co.uk` domain that every other active rc branch also depends on) and calls `scripts/github/amplify-remove-rc-urls.sh` to remove just that one URL from the shared `rc-shared` Cognito client. This step is best-effort (`continue-on-error: true`) so a Cognito API hiccup never blocks the actual resource teardown. The same script is reusable for a manual one-off sweep of already-stale entries that accumulated before this automation existed.

### Storage

**DynamoDB:**

| Table | Partition Key | Sort Key | PITR |
| --- | --- | --- | --- |
| `SudokuGames{suffix}` | `userId` (String) | `gameId` (String) | Default workspace only |
| `SudokuPlayers{suffix}` | `userId` (String) | — | Default workspace only |
| `SudokuLeaderboard{suffix}` | `userId` (String) | — | Default workspace only |
| `SudokuCoachRateLimits{suffix}` | `userId` (String) | `window` (String) | Not enabled (ephemeral, TTL-based expiry) |

All four tables use `PAY_PER_REQUEST` (on-demand) billing. AWS-managed encryption (no CMK).

**ECR:**

- Repositories `sudoku-backend` and `sudoku-image-recognition`, both created by
  `scripts/infra/bootstrap.sh` (outside Terraform)
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
| `VITE_AI_COACH` | `"false"` (default), `"true"` (all others) — AI coach feature flag |

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
- DynamoDB Leaderboard: `GetItem, UpdateItem, Scan` (Scan used by `DynamoDbLeaderboardRepository`, not admin-gated)
- DynamoDB CoachRateLimits: `GetItem, UpdateItem` (no Scan — per-user rate-limit counters only)
- `bedrock:InvokeModel` on the same Bedrock inference-profile/foundation-model ARNs as the image recognition role (AI coach feature)

`Scan` on Games/Players is used solely by `AdminDataResource` (the admin data browser, `docs/llds/user-management.md` — Admin Authorization). These grants live on the same role as every other game/player operation — there is no separate, more restricted role for admin-only actions, so a bug in the admin group check is the last line of defence against a full table scan by any authenticated user. Isolating this would require a dedicated admin Lambda; out of scope for now.

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
| `dynamodb.tf` | SudokuGames, SudokuPlayers, SudokuLeaderboard, SudokuCoachRateLimits tables |
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
      Environment = local.is_default ? "prod" : terraform.workspace
    }
  }
}
```

### Infrastructure Standards

- **API Gateway:** Always use API Gateway v2 (HTTP API). Never use REST API v1.
- **CORS:** API Gateway CORS is fully Terraform-managed (see the CORS / Callback URL Configuration section) — only Cognito callback/logout URLs use the two-step pattern.
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
    build-backend-image/        # Quarkus fast-jar + Dockerfile.jvm-lwa build/push to ECR
    create-localstack-dynamodb/ # Creates DynamoDB tables in LocalStack
    terraform-validate/         # fmt check, init, validate
    integration-tests/          # Native quarkus:dev + npm dev + Playwright

scripts/github/
    amplify-post-deploy.sh     # Post-Terraform: CORS, Cognito, Amplify build trigger
    amplify-remove-rc-urls.sh  # Removes URL(s) from the shared rc Cognito client (teardown + manual sweep)
    api-smoke-tests.sh         # HTTP probes against the live API Gateway
    resolve-environment.sh     # Derives workspace/environment/is_main from branch name
    terraform-plan.sh          # Parameterised terraform plan (phase 1 & 2, RC vars)
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
3. **Build backend** (if backend changed) — Quarkus HTTP fast-jar, `Dockerfile.jvm-lwa` build + ECR
   push with `{branch}-{sha}` tag; if unchanged, the deploy job reuses the currently-deployed image
   (falling back to a fresh build if none is found, e.g. a brand-new workspace)
4. **Build image recognition** — Docker build + ECR push with `{branch}-{sha}` tag
5. **Terraform deploy** (two phases):
   - Phase 1: all resources except domain association (avoids 40-min ACM cert wait on every deploy)
   - Phase 2: full plan including domain
   - `scripts/github/resolve-environment.sh` derives workspace from branch name
   - `scripts/github/terraform-plan.sh` handles both phases with RC var-file injection
   - `scripts/github/amplify-post-deploy.sh` tightens CORS/Cognito, triggers Amplify build
6. **Smoke tests** — API probes + Playwright against live Amplify URL
7. **Notify** — step summary with re-run instructions

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

## GCP Resources

The GCP facet provisions the same runtime platform as the AWS facet, on GCP-native services, in region `us-central1` (chosen for free-tier coverage). It reuses the AWS workspace model: the same `is_default` / `is_rc` / `suffix` locals drive per-workspace naming, and workspace state is isolated by GCS backend prefix rather than S3 key.

Two parts of the AWS facet are **deliberately absent from GCP Terraform** and provisioned by hand instead — see *GCP Manual Setup* below: (a) all identity/IAM (service accounts, role bindings, Workload Identity Federation) and (b) the Identity Platform auth config. Terraform references the runtime service accounts and the Identity Platform issuer/audience by variable.

**Scope of the GCP facet: infrastructure scaffolding.** This facet provisions the GCP platform (Cloud Run, Firestore, Firebase Hosting, DNS, budget) and makes it `terraform validate`/`plan`-clean and CI-wired. It does **not** make the application run end-to-end on GCP: the Java backend persists to DynamoDB and authenticates against Cognito today, and the React frontend uses the Cognito/Amplify auth SDK. Running on GCP additionally requires, as **separate per-segment arrows** (not owned here): a backend Firestore persistence profile (Game Lifecycle, User Management, League Table, AI Coach), a frontend Firebase Auth path (React Frontend, User Management), and cross-cloud Bedrock credential wiring (AI Coach, Image Recognition). Until those land, the GCP-facet specs are `[ ]` gaps and the `deploy-gcp` CI path is provisioning-only. The custom domain is `sudoku-gcp.edoatley.co.uk` (distinct from the AWS `sudoku.edoatley.co.uk` / `sudoku-beta.edoatley.co.uk`), and GCP has no `rc-shared` equivalent — Identity Platform is a single manual per-project config and Firestore isolates per workspace by named database.

### GCP Workspace Strategy

Identical `terraform.workspace` model to AWS. Firestore is isolated per workspace by **named database**: `(default)` database on the `default` workspace, a `sudoku{suffix}` named database otherwise. Cloud Run services, the Firebase Hosting site, and Cloud DNS records all carry `local.suffix`. Labels (see *Tagging*) replace AWS tags.

### Compute (Cloud Run)

**Backend service (`sudoku{suffix}`):**

| Property | Value |
| --- | --- |
| Image | Artifact Registry container of the Quarkus app (`var.backend_image`) |
| CPU / Memory | 1 vCPU / 512 MiB |
| Timeout | 8 seconds |
| Concurrency | container concurrency capped (throttle guard) |
| Scaling | `min_instance_count = 0` (scale to zero); `max_instance_count` capped (throttle / cost guard) |
| Runtime SA | referenced by `var.run_service_account_email` (created manually) |
| Ingress | all (public); public invocation via `roles/run.invoker` for `allUsers` granted manually |

The Quarkus backend runs as a container (parity with the AWS Lambda's Quarkus app, packaged for Cloud Run instead of the Lambda runtime), pushed to an Artifact Registry `sudoku-backend` repository (created by the bootstrap script, alongside `sudoku-image-recognition`). It validates Identity Platform JWTs **in-app** rather than at an API-gateway edge — issuer `https://securetoken.google.com/{project_id}`, audience `{project_id}`. Note: Firebase/Identity Platform ID tokens are **not** fully standard OIDC (JWKS served from `https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com`, no OIDC discovery document, `sub` = Firebase UID with the Google identity under `firebase.identities`). The backend therefore needs explicit key/issuer/audience configuration, not Quarkus OIDC auto-discovery — verified in the code phase (tracked in *Open Questions*). CORS is applied in-app via the same `CORS_ALLOWED_ORIGINS` env var as AWS.

**Image-recognition service (`sudoku-image-recognition{suffix}`):**

| Property | Value |
| --- | --- |
| Image | Artifact Registry `sudoku-image-recognition:{branch}-{sha}` (`var.image_recognition_image_uri`) |
| CPU / Memory | 1 vCPU / 512 MiB |
| Timeout | 60 seconds |
| Scaling | `min_instance_count = 0`; `max_instance_count` capped |
| Runtime SA | `var.image_recognition_service_account_email` (created manually) |

### Edge / Auth / Throttle

There is **no API Gateway** on GCP. Clients call the Cloud Run service URLs directly. This replaces three AWS responsibilities:

- **Routing:** Cloud Run serves all application paths from the container; there is no gateway route table. The two services have distinct URLs; the frontend targets the backend URL and (for imports) the image-recognition URL.
- **Auth:** the JWT is validated in-app by the Quarkus backend (Identity Platform issuer/audience above), not by an edge authorizer. The `administrators` group used for `/admin/data/*` on AWS has no Identity Platform group equivalent — admin authorization is enforced in-app via a custom claim or configured allowlist (see `docs/llds/user-management.md`; tracked in *Open Questions*).
- **Throttle / DoS guard:** enforced by Cloud Run `max_instance_count` × `container_concurrency` (a hard ceiling on concurrent work) plus the per-request timeout, rather than an API-Gateway request-rate throttle. This is a **deliberate deviation** from the AWS facet's per-request rate limit (`security-standards.md`: 25 rps / 50 burst). Cloud Run exposes no native request-rate throttle; a true one would require an external HTTPS load balancer + Cloud Armor (ongoing, non-free-tier cost), contradicting the near-zero-cost goal. The accepted GCP guard is layered: (a) `max_instance_count × container_concurrency × timeout` caps the maximum compute burn rate, and scale-to-zero keeps idle cost at zero; (b) the only per-call-costly path, the AI coach, is rate-limited **in-app per user** (monthly token budget + per-user/per-minute limiter in the `coachRateLimits` Firestore collection — identical to the AWS DynamoDB guard, independent of the edge); (c) the billing budget alert (below) is the backstop. What this gives up: no per-client edge rate limit, so a burst of cheap requests could erode free-tier request quota faster than a 25 rps cap would. Cloud Armor is the documented unbuilt escape hatch if real abuse appears.

### Storage (Firestore)

Firestore in **Native mode**, one database per workspace (see *Workspace Strategy*). Collections mirror the DynamoDB tables:

| Collection | Document key | Notes |
| --- | --- | --- |
| `games` | `userId` + `gameId` (composite doc path) | mirrors `SudokuGames` |
| `players` | `userId` | mirrors `SudokuPlayers` |
| `leaderboard` | `userId` | mirrors `SudokuLeaderboard` |
| `coachRateLimits` | `userId` + `window` | TTL policy on `expiresAt`; mirrors `SudokuCoachRateLimits` |

Location `us-central1` (single-region, cheapest, free-tier eligible). Delete protection / point-in-time recovery enabled on the `default` database only, mirroring the AWS PITR split. No composite indexes are required for the current single-key access patterns; add `google_firestore_index` if a query needs one.

**Free-tier caveat:** Firestore's free daily quota is per-project, not per-database, so many `rc-*` named databases share one quota. This is acceptable at this project's traffic, but a burst of concurrent RC environments could erode the free tier — a reason the *project-per-environment* alternative (rejected above for billing simplicity) exists.

### Identity (Identity Platform) — MANUAL, not Terraform

Identity Platform with a Google sign-in provider is the Cognito equivalent (social-only Google login). It is **provisioned by hand** (runbook or bootstrap script), not Terraform, for the same reason IAM is manual — it is the primary learning surface. Terraform consumes the resulting issuer and audience via variables and injects the frontend `VITE_*` auth config. The AWS smoke-test username/password client has an Identity Platform equivalent (a test user); its creation is also manual.

### Frontend Hosting (Firebase Hosting)

`google_firebase_hosting_site` (`sudoku{suffix}`) with a single-page-app rewrite (`ui/firebase.json`: all paths → `/index.html`). As on AWS, the frontend is **not** auto-deployed on push: CI runs `firebase deploy` for `ui/dist` **after** `terraform apply`, because the `VITE_*` values (backend URL, Identity Platform config) are baked into the Vite bundle at build time and must reflect the just-applied infrastructure.

`VITE_*` variables mirror the AWS set, retargeted: `VITE_API_URL` → backend Cloud Run URL + `/api/v1`; the `VITE_COGNITO_*` trio → Identity Platform equivalents; `VITE_MOCK_API` / `VITE_DEV_TOOLS` / `VITE_AI_COACH` unchanged.

### DNS & TLS

Cloud DNS managed zone for `sudoku-gcp.edoatley.co.uk`, created once in the `default` workspace (parity with the Route53 zone). Records point the custom domain at Firebase Hosting (or a Cloud Run domain mapping). TLS is Google-managed — no manual certificate handling. NS delegation from the parent zone `edoatley.co.uk` is a manual one-time step, as on AWS.

### Cost Guardrail (Billing Budget)

`google_billing_budget` sets a monthly cap with threshold alerts delivered to a Pub/Sub topic (parity with AWS Budgets + Cost Anomaly alerts). **Alert-only:** unlike the AWS budget action that auto-attaches a Bedrock-deny IAM policy, GCP budgets cannot enforce a hard cap directly. Automated enforcement (e.g. a Cloud Function on the budget Pub/Sub topic that disables billing or scales services to zero) is deferred — see *Open Questions*.

### Cross-Cloud Identity Continuity

For `bob@gmail.com` to retain profile, games, and leaderboard across an AWS→GCP cutover, user data must be keyed on a cloud-independent identifier. The stable choice is the **Google OAuth `sub`**, identical across both IdPs because both federate the same Google OAuth client:

- Cognito ID token: exposed in the `identities` claim (`{providerName:"Google", userId:<google-sub>}`).
- Identity Platform ID token: exposed in `firebase.identities["google.com"][0]`.

The AWS deployment currently keys user data on the Cognito subject (a Cognito UUID), so adopting the Google `sub` as the canonical `userId` requires a one-time re-key migration on the AWS side. Continuity follows the **cutover model** (one-time DynamoDB→Firestore export keyed by the Google `sub`); there is no live cross-cloud replication. The canonical-`userId` decision and its migration are cross-segment (they touch User Management and Game Lifecycle) and are **not** implemented by this LLD — recorded as an *Open Question* pending its own arrow.

## GCP Manual Setup (not Terraform-managed)

The following are provisioned by hand from `docs/runbooks/gcp-manual-setup.md` (some optionally by `scripts/infra/gcp-bootstrap.sh`), not by `infra/gcp/*.tf`:

| Item | What | Why manual |
| --- | --- | --- |
| Service accounts | `sudoku-run@`, `sudoku-image-recognition-run@` (runtime), `sudoku-github-deploy@` (CI) | **Now created by the bootstrap script** (once the pattern was understood); listed here for reference |
| IAM role bindings | `roles/datastore.user` (runtime → Firestore) **is scripted in bootstrap**; `roles/run.invoker` (public invoke), `roles/secretmanager.secretAccessor` (Bedrock credential), and the deploy-SA project roles remain hand-run | Least privilege authored by hand for the sensitive/broad grants; the narrow runtime Firestore grant is automated |
| Workload Identity Federation | Pool + OIDC provider for `token.actions.githubusercontent.com`, attribute condition on `repository == edoatley/sudoku-app`, impersonation binding to the deploy SA | GCP-native external-identity trust; the counterpart to the AWS GitHub OIDC provider |
| Identity Platform | Auth config + Google IdP + smoke-test user | primary learning surface |
| Networking (optional) | VPC + Serverless VPC Access connector for private Firestore/egress | unbuilt; Cloud Run uses Google's managed public API |

Terraform depends on these only by value: runtime SA emails, Identity Platform issuer/audience, and (path A) the Bedrock credential in Secret Manager are passed as variables. Rationale: keeping identity/network out of automation is a project tenet (HLD *Tenets*).

## GCP Terraform Project Structure

### File Organisation

| File | Contents |
| --- | --- |
| `terraform.tf` | `google` + `google-beta` providers (region `us-central1`), GCS backend (`bucket = "sudoku-tf-state-gcp"`, `prefix = "sudoku"`), `default_labels` |
| `main.tf` | `local` values (`is_default` / `is_rc` / `suffix`, sanitized workspace label), project data source |
| `variables.tf` | All input variables (project id, region, SA emails, image URIs, Identity Platform issuer/audience, budget, AI env) |
| `outputs.tf` | Backend/image-recognition URLs, Hosting URL, Firestore database name, DNS name servers |
| `cloud_run.tf` | Backend Cloud Run service |
| `image_recognition.tf` | Image-recognition Cloud Run service |
| `firestore.tf` | Firestore database (named per workspace) + any indexes |
| `firebase_hosting.tf` | Firebase Hosting site |
| `dns.tf` | Cloud DNS zone, records, custom-domain mapping |
| `budgets.tf` | Billing budget + Pub/Sub alert topic |
| `README.md` | Architecture, file map, link to the manual runbook, bootstrap + workspace + cost notes |

**No `iam.tf` and no `identity_platform.tf`** — those live in the manual runbook.

### Naming & Labels

Resource names use `local.suffix`, as on AWS. Labels replace tags and must be lowercase (`[a-z0-9_-]`, ≤63 chars); the workspace name is sanitized before use as a label value:

```hcl
provider "google" {
  region = "us-central1"
  default_labels = {
    project     = "sudoku"
    managed_by  = "terraform"
    environment = local.is_default ? "prod" : local.workspace_label
  }
}
```

## GCP CI/CD

The GCP facet extends the existing pipeline in tiers.

**Live (infrastructure-scaffolding arrow):**

- **Validate gate:** the `terraform-validate` action is parameterised by `working-directory` and `ci.yml`'s `ci-infra` job runs it as a matrix over `infra/aws` and `infra/gcp`; `ci.yml`'s path filter adds `infra/gcp/**` (CP-GCP-081).
- **Bootstrap:** `scripts/infra/gcp-bootstrap.sh` (project + billing, GCS state bucket, API enablement, two Artifact Registry repos, runtime + deploy SAs with `roles/datastore.user`) and `scripts/infra/gcp-github-bootstrap.sh` (Workload Identity Federation pool/provider, deploy-SA project roles, the three GitHub secrets).
- **Deploy pipeline:** the **`deploy-gcp` workflow** (`.github/workflows/deploy-gcp.yml`, `workflow_dispatch`) authenticates via WIF (`GCP_WIF_PROVIDER` impersonating `GCP_DEPLOY_SA_EMAIL`, no keys — the counterpart to `configure-aws-oidc`). It runs three jobs: **build-image** (build the HTTP fast-jar, `docker build` `Dockerfile.jvm-lwa`, push to Artifact Registry `sudoku-backend`), **terraform** (`apply` on `infra/gcp`, passing `backend_image`), and **deploy-frontend** (build the Firebase-provider UI, `firebase deploy --only hosting`). Phased flags `deploy_cloud_run` / `deploy_frontend` / `enable_custom_domain` (default off) let it stand up Firestore + Firebase Hosting + Cloud DNS before the app container and DNS delegation exist. Because `workflow_dispatch` requires the workflow on the default branch, `deploy-gcp.yml` is also on `main` (dispatch-only, inert); runs target the working branch via `--ref`.

**Games-slice arrow (landed):**

- **Unified backend image:** the Quarkus backend builds as a plain HTTP fast-jar (the only Maven build — the `aws-lambda` profile that used to produce `quarkus-amazon-lambda-rest`'s zip has been retired) and is containerised via hand-written `Dockerfile.jvm-lwa` with the AWS Lambda Web Adapter baked in — the identical image runs on Cloud Run (HTTP) and AWS Lambda (container, via `AWS_LWA_READINESS_CHECK_PATH`). Persistence is selected at **runtime** by the `sudoku.persistence` property (CDI producer over `@LookupIfProperty` adapters); the `%gcp` profile (`QUARKUS_PROFILE=gcp`, parent `prod`) flips it to Firestore and carries the Firebase issuer/audience (in-app JWT validation) plus `CORS_ALLOWED_ORIGINS`. `deploy_cloud_run=true` then has Terraform create the Cloud Run service pointing at the pushed image.
- **Smoke-test auth:** Identity Platform has no Cognito `USER_PASSWORD_AUTH`; the GCP smoke test obtains a token via the Identity Platform REST endpoint (`identitytoolkit … :signInWithPassword`) using the admin-provisioned test user (runbook §5).

**Out-of-slice parity (see `docs/todo/gcp-aws-parity.md`):** leaderboard + coach-rate-limit Firestore I/O, admin-data Firestore adapter, AI coach on GCP (cross-cloud Bedrock or Vertex), and the image-recognition Cloud Run service (defined behind `deploy_image_recognition`, not yet built/deployed).

**Secrets:** `GCP_PROJECT_ID`, `GCP_WIF_PROVIDER`, `GCP_DEPLOY_SA_EMAIL` (set by `gcp-github-bootstrap.sh`); the existing `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` are reused for the Identity Platform Google provider.

## GCP Design Decisions

| Decision | Chosen | Alternatives Considered | Rationale |
| --- | --- | --- | --- |
| Backend compute | Cloud Run (container) | Cloud Functions 2nd gen | Container parity with the Quarkus app; full control; scale-to-zero keeps cost near zero |
| Persistence | Firestore (Native) | Keep DynamoDB cross-cloud; Cloud SQL | Managed serverless NoSQL parity; free-tier eligible; no server to run |
| Workspace isolation | Named Firestore database per workspace, single project | Project-per-environment | One project keeps billing/free-tier simple; project-per-env documented as the heavier alternative |
| Edge | Cloud Run direct + in-app JWT validation | GCP API Gateway; external HTTPS load balancer | Gateway adds cost/complexity; Quarkus already validates OIDC JWTs; throttle achieved via instance/concurrency caps |
| IAM / SA / WIF / Identity Platform | Manual (runbook), not Terraform | Terraform-managed | Deliberate learning surface; least-privilege authored by hand (HLD tenet) |
| AI inference | Bedrock cross-cloud via Secret Manager | Vertex AI (Gemini) now | No backend code change; stands the platform up fast; Vertex migration deferred |
| Region | `us-central1` | `europe-west2` (London, AWS-parity) | Maximises free-tier coverage; residency shift acceptable under one-cloud-at-a-time |
| Budget enforcement | Alert-only (Pub/Sub) | Hard-cap auto-disable | GCP budgets cannot attach a deny action; automated enforcement deferred to a Pub/Sub Cloud Function |

## Observed Design Decisions

| Decision | Chosen | Alternatives Considered | Rationale |
| --- | --- | --- | --- |
| API Gateway CORS fully Terraform-managed | Static custom-domain origins set directly in `api_gateway.tf` | Post-apply tighten step (former design) | Avoids the Amplify-URL-unknown-at-apply-time cycle by simply not supporting the raw `*.amplifyapp.com` origin |
| Cognito callback URLs two-step | Baseline URL list + post-apply add | Terraform-only | Amplify branch URL unknown at apply time; circular dependency resolved by post-apply script |
| Amplify auto-build disabled | CI triggers `amplify start-job` manually | Amplify webhook auto-build | `VITE_*` vars baked at build time; auto-build uses stale values |
| Java Lambda package type | Container image (parity with GCP Cloud Run) | Zip + SnapStart | Single artifact across both clouds outweighs SnapStart's cold-start benefit, which container images can't use anyway (AWS doesn't support SnapStart for container-image Lambdas) |
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
- `ignore_changes` on Cognito callback/logout URLs means Terraform state drifts from actual configuration after every post-apply addition. `terraform plan` will always show these values as "no changes" even when the live config differs from the baseline.

## Open Questions & Future Decisions (GCP facet)

| Area | Question / gap | Notes |
| --- | --- | --- |
| Cross-cloud identity | Adopt Google `sub` as canonical `userId` + migrate AWS data | Cross-segment (User Management, Game Lifecycle); needs its own arrow before any cutover; not implemented here |
| Admin authorization | Identity Platform has no group concept | `/admin/data/*` group check must move to a custom claim or allowlist in-app — see `docs/llds/user-management.md` |
| Firebase JWT verification | Firebase ID tokens are non-standard OIDC | Backend needs explicit JWKS/issuer/audience config, not Quarkus OIDC auto-discovery; verify in code phase |
| Bedrock credential | Long-lived AWS access key in Secret Manager (path A) | Security tension (WIF avoids long-lived keys) + cross-region latency; accepted interim, resolved by the Vertex AI migration |
| Budget enforcement | Alert-only; no hard cap | Automated enforcement (Pub/Sub → Cloud Function disabling billing / scaling to zero) deferred |
| AI provider | Bedrock cross-cloud interim | Vertex AI (Gemini) migration is the GCP-native end state; deferred |
| Private networking | Cloud Run → Firestore over public managed API | VPC + Serverless VPC Access connector documented but unbuilt |

## References

- AWS facet: `infra/aws/main.tf`, `infra/aws/terraform.tf`, `infra/aws/variables.tf`, `infra/aws/outputs.tf`, `infra/aws/lambda.tf`, `infra/aws/api_gateway.tf`, `infra/aws/dynamodb.tf`, `infra/aws/cognito.tf`, `infra/aws/cognito-rc-shared.tf`, `infra/aws/amplify.tf`, `infra/aws/domain.tf`, `infra/aws/iam.tf`, `infra/aws/image_recognition_lambda.tf`
- GCP facet: `infra/gcp/terraform.tf`, `infra/gcp/main.tf`, `infra/gcp/variables.tf`, `infra/gcp/outputs.tf`, `infra/gcp/cloud_run.tf`, `infra/gcp/image_recognition.tf`, `infra/gcp/firestore.tf`, `infra/gcp/firebase_hosting.tf`, `infra/gcp/dns.tf`, `infra/gcp/budgets.tf`
- GCP manual setup runbook: `docs/runbooks/gcp-manual-setup.md`; bootstrap: `scripts/infra/gcp-bootstrap.sh`
- See also: `docs/llds/react-frontend.md` (frontend app delivered by Amplify on AWS, Firebase Hosting on GCP)
- Depends on: nothing (provisions all other components)
- Depended on by: all components (runtime environment)
