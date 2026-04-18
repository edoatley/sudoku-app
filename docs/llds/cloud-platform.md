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
| Runtime | Java 21 |
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
| Runtime | Container image (Python 3.11 + Pillow) |
| Memory | 512 MB |
| Timeout | 60 seconds |
| Image | ECR `sudoku-image-recognition:{branch}-{sha}` |

Container image required for Pillow (native C extensions). No SnapStart — Python cold starts managed via warmup probe (`GET /api/v1/puzzles/import/warmup`).

### API Gateway (HTTP v2)

One API (`sudoku{suffix}`) routes to both Lambda functions.

**Public routes (no auth):**

| Route | Target |
| --- | --- |
| `$default` | Java Lambda (catches `/puzzles/*`, `/health`, `/dev/*`) |
| `GET /api/v1/puzzles/import/warmup` | Image Recognition Lambda |

**JWT-protected routes:**

| Route | Target |
| --- | --- |
| `POST /api/v1/games` | Java Lambda |
| `POST /api/v1/games/from-image` | Java Lambda |
| `GET /api/v1/games/{gameId}` | Java Lambda |
| `PATCH /api/v1/games/{gameId}` | Java Lambda |
| `GET /api/v1/games/current` | Java Lambda |
| `GET /api/v1/players/me` | Java Lambda |
| `POST /api/v1/puzzles/import` | Image Recognition Lambda |

The JWT authorizer validates Cognito tokens (issuer URL + audience = web client ID). Route precedence: specific routes beat `$default`.

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
| `VITE_ENABLE_IMPORT` | `"true"` |
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
- DynamoDB Games: `GetItem, PutItem, UpdateItem, Query`
- DynamoDB Players: `GetItem, PutItem, UpdateItem` (no Query — single-key access only)

**Image Recognition Lambda role (`SudokuImageRecognitionExecRole{suffix}`):**

- `AWSLambdaBasicExecutionRole`
- `bedrock:InvokeModel` on Claude Haiku, Nova Pro, Nova Lite, Mistral, Nemotron ARNs (direct + cross-region inference profiles)

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

- `GET /api/v1/games/current` could conflict with `GET /api/v1/games/{gameId}` if a gameId were the string `"current"`. API Gateway routes most-specific-first so `/games/current` wins — but `"current"` is implicitly reserved as a gameId value with no enforcement.
- The ECR repository is created by `scripts/bootstrap.sh` outside Terraform. A first-time deploy will fail without it, and this prerequisite is not documented in the Terraform root module.
- `infra/variables.tf` has required variables (`google_client_id`, `google_client_secret`, `github_token`) with no defaults and no documentation of where to source them.
- CloudWatch has no alarms or metric filters on Lambda errors — no automated notification when either Lambda starts returning 5xx responses.
- `VITE_DEV_TOOLS=true` in all non-default workspaces. If an RC workspace is accidentally pointed at production infrastructure, developer tools would be visible to all users.
- IAM `bedrock:InvokeModel` grants are derived from `local.bedrock_models` in Terraform — inference-profile ARNs and the corresponding foundation-model ARNs (required for regional routing) are both generated from the same list.

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
