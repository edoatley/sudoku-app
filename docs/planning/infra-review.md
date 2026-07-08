# Infrastructure Review — `infra/` Terraform

**Created**: 2026-07-08
**Status**: Findings report — H1–H4 fixed (H1: PR #115, 2026-07-08; H2–H4: 2026-07-08); M1–L5 not yet actioned
**Scope**: All 15 `infra/*.tf` files, cross-checked against `infra/README.md`, `docs/llds/cloud-platform.md`, `.github/workflows/`, and backend code where a claim depended on it.

Each finding lists what/where, impact, recommended fix, and effort (S/M/L).

---

## High priority

### H1 — `/dev/data/*` full-table-dump endpoints are publicly reachable in production — **FIXED 2026-07-08 (PR #115)**

**Status**: Was verified exploitable against production. Unauthenticated `GET https://7xv70er20f.execute-api.eu-west-2.amazonaws.com/api/v1/dev/data/players` returned **HTTP 200** with the full SudokuPlayers table — real display names, email addresses, and user IDs for every registered user. No token supplied. (`/api/v1/health` returned 503 at the same time, confirming the leak is specific to the ungated `/dev/*` resources, not a broadly open API.) `DevDataResource` is now deleted; `/api/v1/dev/data/*` 404s in every deployed environment, verified by `backend/src/test/java/com/sudoku/developer/DevDataRouteRemovedTest.java` (re-runs on every `mvn test`) and `scripts/github/api-smoke-tests.sh` (re-runs the literal exploit URL against the live API on every deploy).

**Where**: `infra/api_gateway.tf:8` (`$default` route, no auth) + `backend/src/main/java/com/sudoku/developer/DevDataResource.java`

**What**: The `$default` API Gateway route is unauthenticated and proxies every unmatched path to the Java Lambda. `DevDataResource` is compiled into the production bundle — unlike `DevUserFilter`, it has **no `@IfBuildProfile` guard** — and with `quarkus.http.root-path=/api/v1` it serves:

- `GET /api/v1/dev/data/games` — scans and returns the **entire SudokuGames table**
- `GET /api/v1/dev/data/players` — scans and returns the **entire SudokuPlayers table** (emails/names of every user)

Its javadoc claims the paths are "intentionally not registered in any production API Gateway route", but `$default` catches them. The checkov skip on `CKV_AWS_309` ("$default catches only public routes (/puzzles/*, /health)") is likewise incorrect. `DevResource` (`/dev/hint-demo`) is exposed the same way (lower impact — no user data).

**Impact**: Unauthenticated disclosure of all user game data and player profiles in production. Also an unbounded-Scan cost/DoS vector.

**Fix**: Verify with a live probe first (`curl https://<api>/api/v1/dev/data/players`). Then defence in depth:
1. Backend: add `@IfBuildProfile(anyOf = {"dev", "it", "test"})` to `DevDataResource` and `DevResource` so they are compiled out of the production bundle (mirrors `DevUserFilter`).
2. Terraform (optional second layer): the IAM Scan grants on Games/Players in `iam.tf` exist only for these endpoints — once compiled out, drop `dynamodb:Scan` from `SudokuDynamoDBPolicy` and `SudokuPlayersPolicy` (Leaderboard legitimately scans via `DynamoDbLeaderboardRepository`).

**Effort**: S (backend annotation) + S (IAM tightening)

**Fix implemented**: see `docs/planning/old/admin-namespace-security-fix.md` — the data browser was promoted to an admin-group-gated `/admin/data/*` namespace (kept in prod for admins) rather than compiled out, so the Games/Players Scan grants are retained; see `docs/llds/user-management.md` — Admin Authorization. The `hint-demo` dev aid is deliberately **not** compiled out of prod (a first attempt at that broke RC smoke tests — the Lambda is built once and shared by every Terraform workspace, so it would have removed hint-demo from RC/beta too); it stays reachable everywhere, which is an accepted trade-off since it carries no user data. See `docs/llds/cloud-platform.md` — API Gateway.

### H2 — Bedrock budget kill-switch does not cover the image recognition Lambda — **FIXED 2026-07-08**

**Fix implemented**: `aws_iam_role.image_recognition_lambda_exec.name`/`.arn` added to the budget action's `roles` list and the budgets execution role's `iam:AttachRolePolicy`/`iam:DetachRolePolicy` resource list (`infra/budgets.tf`). Confirmed `image_recognition/handler.py` already degrades gracefully on Bedrock `ClientError` (including `AccessDeniedException`), falling back through remaining models and ultimately raising a handled `ValueError` — no backend change needed.


**Where**: `infra/budgets.tf:104-124` (`aws_budgets_budget_action.deny_bedrock_on_limit`)

**What**: When Bedrock spend hits 100% of the $25 cap, the budget action attaches `SudokuBedrockDeny` **only to `SudokuLambdaExecRole`** (the coach). `SudokuImageRecognitionExecRole` keeps its `bedrock:InvokeModel` allow, so the image-to-puzzle feature continues spending after the hard cap fires.

**Fix**: Add `aws_iam_role.image_recognition_lambda_exec.name` to `definition.iam_action_definition.roles`, and its ARN to the budgets execution role's `iam:AttachRolePolicy`/`iam:DetachRolePolicy` resource list (`budgets.tf:58`). Verify the image recognition handler degrades gracefully on `AccessDeniedException` the way `BedrockCoachClient` does.

**Effort**: S

### H3 — `required_version = ">= 1.5"` is below what the backend config needs — **FIXED 2026-07-08**

**Fix implemented**: `infra/terraform.tf` now requires `>= 1.10`. CI's `hashicorp/setup-terraform@v4` installs latest by default (no pinned older version), and local dev Terraform is v1.15.5, so both satisfy the new constraint.


**Where**: `infra/terraform.tf:2` vs `infra/terraform.tf:15`

**What**: The S3 backend uses `use_lockfile = true` (native S3 locking), which requires Terraform ≥ 1.10. A 1.5–1.9 binary satisfies the version constraint but fails (or silently skips locking) on the backend config.

**Fix**: `required_version = ">= 1.10"`.

**Effort**: S

### H4 — No deletion protection on production stateful resources — **FIXED 2026-07-08**

**Fix implemented**: `deletion_protection_enabled = local.is_default` added to the Games, Players, and Leaderboard tables (rate-limits table left unprotected — ephemeral by design); `lifecycle { prevent_destroy = true }` added to both Route53 zones in `infra/domain.tf`. `teardown.yml` already requires a typed `DESTROY` confirmation input for any workspace, including default, so the "consider a typed confirmation" suggestion was left as-is.


**Where**: `infra/dynamodb.tf` (all four tables), `infra/domain.tf:5-19` (both Route53 zones)

**What**: `teardown.yml` can run `terraform destroy` against the **default (production) workspace**. Nothing stops it deleting the Games/Players/Leaderboard tables (user data — PITR does not survive table deletion by itself without a restore) or the Route53 zones. Destroying a zone regenerates its nameservers on recreate, silently breaking the manually-configured NS delegation in the parent account.

**Fix**:
- `deletion_protection_enabled = local.is_default` on the Games, Players, and Leaderboard tables (rate-limits table is ephemeral).
- `lifecycle { prevent_destroy = true }` on both zones (they are already `count`-gated to the default workspace).
- Consider requiring a typed confirmation input on `teardown.yml` when the target is `default`.

**Effort**: S

---

## Medium priority

### M1 — CORS origins disagree between API Gateway and the Lambda

**Where**: `infra/api_gateway.tf:105-116` vs `infra/lambda.tf:85`

**What**: The Lambda's `CORS_ALLOWED_ORIGINS` includes the raw `https://<branch>.<app>.amplifyapp.com` URL, but the API Gateway `cors_configuration` deliberately excludes it (referencing Amplify would create a dependency cycle). Browsers loading the app from the raw Amplify URL fail CORS preflight at the gateway even though the Lambda would allow them. Additionally, a non-default, non-`rc-*` workspace (e.g. a feature workspace) gets the **beta** origins — wrong for an environment with no custom domain.

**Fix**: Either accept and document that the raw Amplify URL is unsupported (and remove it from the Lambda env var so the two layers agree), or restore a post-apply CORS tightening step for the raw URL only. Add an explicit `else` branch (or precondition) for non-default/non-rc workspaces.

**Effort**: M

### M2 — Bedrock IAM policy duplicated verbatim across two files

**Where**: `infra/iam.tf:123-142` (`SudokuCoachBedrockPolicy`) and `infra/image_recognition_lambda.tf:39-58` (`SudokuImageRecognitionBedrockPolicy`)

**What**: The two policies are byte-identical, including the subtle `replace(model, "/^(eu|us|ap)\\./", "")` foundation-model ARN derivation. A future model change edited in one place only would leave the other Lambda broken or over-permitted.

**Fix**: Hoist the statement's `Resource` list into a `local.bedrock_invoke_resources` next to `local.bedrock_models` in `main.tf` (single source of truth, as that comment already promises), or share one `aws_iam_policy` attached to both roles.

**Effort**: S

### M3 — Main Lambda log-group retention unmanaged in production

**Where**: `infra/lambda.tf:96` (`use_existing_cloudwatch_log_group = local.is_default`)

**What**: On the default workspace the module adopts the auto-created log group, and no `cloudwatch_logs_retention_in_days` is set — the production `/aws/lambda/sudoku` group likely retains logs forever (unbounded storage cost), while the image recognition Lambda sets 7/3 days.

**Fix**: Set `cloudwatch_logs_retention_in_days = local.is_default ? 7 : 3` on `module.lambda` to match; confirm the module applies retention to an adopted group.

**Effort**: S

### M4 — `Environment` tag defaults to `prod` on every workspace

**Where**: `infra/variables.tf:19-23`, `infra/terraform.tf:27`

**What**: `default_tags` uses `var.environment` (default `"prod"`). Any apply that doesn't override it — including local RC applies — tags RC resources `Environment = prod`, undermining cost attribution (the README claims RC resources are tagged with the workspace name).

**Fix**: Drop the variable and derive: `Environment = local.is_default ? "prod" : terraform.workspace`. (Requires moving `default_tags` wiring since locals aren't available in the provider block — compute via `terraform.workspace` directly, which is.)

**Effort**: S

### M5 — "Bedrock" anomaly monitor is actually account-wide

**Where**: `infra/budgets.tf:132-161`

**What**: `aws_ce_anomaly_monitor.bedrock` is `DIMENSIONAL`/`SERVICE`, which monitors **every AWS service independently**; the ≥$5 subscription fires for anomalies in any service, not just Bedrock. The naming (`SudokuBedrockAnomalyMonitor`) is misleading.

**Fix**: Either rename to reflect account-wide scope (arguably more useful — keep it) and fix the comments, or switch to a `CUSTOM` monitor with a Bedrock service filter.

**Effort**: S

### M6 — Smoke tests use the public web client, making `ALLOW_USER_PASSWORD_AUTH` load-bearing on it

**Where**: `infra/cognito.tf:181`, `infra/cognito-rc-shared.tf:100`, `.github/workflows/smoke-tests.yml:170-186`, `scripts/github/amplify-post-deploy.sh:109`

**What**: The web SPA client enables `ALLOW_USER_PASSWORD_AUTH` "for smoke-test CI token acquisition" — and CI genuinely uses it (`initiate-auth --auth-flow USER_PASSWORD_AUTH` against the web client; the comment notes it was chosen because it needs no `SECRET_HASH`). Meanwhile the purpose-built `sudoku-smoke-test` client (with secret) appears **unused** by the smoke workflow. Password auth on a public, secretless client weakens the social-only posture: anyone can attempt password auth against the pool with just the public client ID (mitigated by admin-only user creation and `prevent_user_existence_errors`, but still the pool's weakest edge).

**Fix**: Point `smoke-tests.yml` at the smoke-test client (compute `SECRET_HASH = HMAC-SHA256(username + clientId, clientSecret)` — a few lines of openssl), then remove `ALLOW_USER_PASSWORD_AUTH` from both web clients and from `amplify-post-deploy.sh:109`. Alternatively, if the secretless flow is deliberate, delete the unused smoke-test clients and their outputs/variables/secrets.

**Effort**: M

### M7 — Completed `moved{}` migrations linger in `migrations.tf`

**Where**: `infra/migrations.tf` (all 133 lines)

**What**: The file header says blocks should be removed once applied to all workspaces (`rc-iac-updates`, `rc-shared`, `default`). The module refactor merged in `c22b98f`; if all live workspaces have since applied, the file is dead weight and the chained route renames (Phase 3 → Phase 4) are a comprehension hazard.

**Fix**: For each live workspace, confirm `terraform state list` shows only module addresses, then delete the file.

**Effort**: S (verification is the work)

---

## Low priority / polish

### L1 — `aws_s3_object.lambda_zip` is not consumed by the Lambda

**Where**: `infra/lambda.tf:47-52`

The module deploys from `local_existing_package` (local file), not from this S3 object — the upload exists only as the deploy-script download fallback (`deploy-local.sh`). Add a comment saying so, or remove it if the fallback is obsolete. Note `etag` on the object forces re-upload detection but the object plays no role in Lambda versioning.

### L2 — Region hardcoded in ~6 places

`eu-west-2` appears in the provider, both Cognito issuer URLs (`main.tf` via `api_gateway.tf:149`, `lambda.tf:89`), `VITE_COGNITO_DOMAIN` (`amplify.tf:30`), `AWS_REGION_NAME` (`image_recognition_lambda.tf:88`), and `domain.tf` remote-state config. Hoist to `data.aws_region.current.name` / a local so a region move is one edit.

### L3 — No validation on `image_recognition_image_uri`

Default `""` produces an opaque apply-time error from the Lambda module. Add a `validation` block (non-empty, ECR URI shape) or a `precondition` with a clear message pointing at the CI build step.

### L4 — `enable_auto_branch_creation = local.is_default` looks inverted

**Where**: `infra/amplify.tf:39`

Auto branch creation is enabled only on the **production** app — meaning any pushed git branch creates an Amplify branch on the prod app (with auto-build off, so inert, but noisy and unexpected). RC apps, where branch churn actually happens, have it off. Confirm intent; likely should be `false` everywhere.

### L5 — Documentation drift (fixes are out of scope here; see `docs/architecture.md` for current-state truth)

| Doc                                                                    | Stale claim                                              | Actual                                                                                                                      |
| ---------------------------------------------------------------------- | -------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| `infra/README.md:3`                                                    | AWS provider `~> 5.0`                                    | `~> 6.39`                                                                                                                   |
| `infra/README.md:100`                                                  | Runtime `java21`                                         | `java25`                                                                                                                    |
| `infra/README.md:90-96`, `docs/llds/cloud-platform.md` (CORS Two-Step) | CORS tightened post-deploy with `ignore_changes`         | API GW CORS now fully Terraform-managed (`api_gateway.tf:88-92`); only Cognito callback URLs still use the two-step pattern |
| `infra/README.md:349-352`                                              | Workflows `ci-main.yml` / `ci-rc.yml`                    | `ci-deploy.yml` handles both                                                                                                |
| `docs/llds/cloud-platform.md` (Storage)                                | 2 DynamoDB tables                                        | 4 (adds Leaderboard, CoachRateLimits)                                                                                       |
| `docs/llds/cloud-platform.md` (IAM)                                    | Games: no Scan; Players: 3 actions                       | Both include `Scan`; Leaderboard + CoachRateLimits policies missing entirely                                                |
| `docs/llds/cloud-platform.md` (Amplify env vars)                       | —                                                        | `VITE_AI_COACH` missing                                                                                                     |
| `infra/outputs.tf:7`                                                   | "used by the deploy workflow to tighten CORS post-apply" | CORS tightening step no longer exists                                                                                       |

---

## What is working well

Recorded so the review is balanced — these should be preserved through any refactor:

- **Layered Bedrock cost controls** (per-user toggle → token budget → rate limit table → route throttle → budget alerts → automatic IAM deny → anomaly detection) is genuinely defence-in-depth, and the deny-policy trick (deny overrides allow, auto-detached next month) needs no application code.
- **Checkov skips are individually justified with cost reasoning** rather than blanket-suppressed.
- **Workspace strategy** (default / rc-shared / rc-*) with `local.suffix` naming, shared zip bucket and Cognito pool, and remote-state zone lookup is coherent and well-commented — most non-obvious decisions (auto-build off, `wait_for_verification = false`, alias-qualified permissions, ECR outside Terraform) carry comments explaining *why*.
- **`moved{}` migration discipline** achieved a bespoke→module refactor with zero resource destruction.
- Registry modules are version-pinned; state is encrypted with native S3 locking; all sensitive variables are marked `sensitive`.
