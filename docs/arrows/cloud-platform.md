# Arrow: Cloud Platform

All AWS infrastructure: Lambda, API Gateway, DynamoDB, Cognito, Amplify, Route53, IAM, ECR.

## Status

**OK** - 2026-07-08. Admin data-browser JWT routes added (`/admin/data/games`, `/admin/data/players`); `administrators` Cognito group provisioned. All Terraform files read and documented. No apply/drift audit performed (no Terratest or equivalent exists).

## References

### HLD
- docs/high-level-design.md — "CORS Circular Dependency Resolution", "Multi-Workspace Infrastructure" sections

### LLD
- docs/llds/cloud-platform.md

### EARS
- docs/specs/cloud-platform-specs.md (23 specs, all [x])

### Tests
- No infrastructure tests exist (no Terratest or equivalent). This is an accepted gap.

### Code
- infra/main.tf, infra/terraform.tf, infra/variables.tf, infra/outputs.tf
- infra/lambda.tf, infra/api_gateway.tf, infra/dynamodb.tf
- infra/cognito.tf, infra/cognito-rc-shared.tf
- infra/amplify.tf, infra/domain.tf, infra/iam.tf
- infra/image_recognition_lambda.tf

## Architecture

**Purpose:** Provision and connect all AWS services required to run the application across multiple environment workspaces.

**Key Components:**
1. API Gateway HTTP v2 — single API routing to two Lambdas; JWT authorizer; throttling
2. Java Lambda — SnapStart, "live" alias, S3 ZIP deployment
3. Image Recognition Lambda — container image, 60s timeout
4. DynamoDB — two tables (Games, Players), PAY_PER_REQUEST, PITR in production only
5. Cognito — social-only (Google OAuth); shared RC pool; smoke-test user for CI
6. Amplify — manual build trigger; VITE_* env injection at build time
7. Route53 — production and beta zones; NS delegation from parent account

## EARS Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
| --- | --- | --- | --- | --- |
| API Gateway | CP-INFRA-001 to 007 | 7 | 0 | 0 |
| Lambda | CP-INFRA-010 to 012 | 3 | 0 | 0 |
| CORS Lifecycle | CP-INFRA-020 to 022 | 3 | 0 | 0 |
| Cognito | CP-INFRA-030 to 032 | 3 | 0 | 0 |
| Amplify & Frontend Delivery | CP-INFRA-040 to 042 | 3 | 0 | 0 |
| DynamoDB | CP-INFRA-050 to 051 | 2 | 0 | 0 |
| Workspace Isolation | CP-INFRA-060 to 061 | 2 | 0 | 0 |

**Summary:** 23 of 23 active specs implemented; 0 deferred; 0 gaps.

## Key Findings

1. **ECR outside Terraform** — The `sudoku-image-recognition` ECR repository is created by `scripts/bootstrap.sh`, not Terraform. A first-time deploy will fail without it. Documented in `infra/README.md` Bootstrap section. (CP-INFRA-012)
2. **No CloudWatch alarms** — Lambda errors produce log entries but trigger no alert. Silent failures are possible in production. (deferred — separate feature work)
3. **`ignore_changes` drift** — `ignore_changes` on CORS and Cognito callback URLs means `terraform plan` always shows "no changes" for these fields even when live config differs from baseline. Actual config only visible via AWS console or CLI.
4. **`/games/current` routing** — `GET /api/v1/games/current` is an explicit JWT-protected route at API Gateway. JAX-RS also routes `/current` to its own method before `/{gameId}` can match. The string "current" is fully protected at both layers; no backend guard is needed.
5. **`/dev/*` PII leak closed (2026-07-08)** — `$default` previously forwarded `/dev/data/*` to a Lambda handler (`DevDataResource`) with no build-profile guard and no gateway auth, exposing the full Games/Players tables unauthenticated in production. Fixed by deleting `DevDataResource` (moved to JWT+group-gated `/admin/data/*`) and compiling `DevResource` (hint-demo) out of every non-dev/it/test build. See `docs/planning/infra-review.md` finding H1 and `docs/llds/user-management.md` — Admin Authorization. (CP-INFRA-001, CP-INFRA-007)
6. **Admin group check is Lambda-only** — API Gateway's JWT authorizer has no concept of Cognito groups; it only proves the caller is authenticated. The `administrators` group membership check happens entirely in `AdminAuthorizationFilter` on the Lambda side.

## Work Required

### Deferred

1. Add CloudWatch alarms on Lambda error rate and throttle metrics with SNS notification. (separate feature work)
