# Arrow: Cloud Platform

All AWS infrastructure: Lambda, API Gateway, DynamoDB, Cognito, Amplify, Route53, IAM, ECR.

## Status

**AUDITED** - 2026-04-18. All Terraform files read and documented. No apply/drift audit performed (no Terratest or equivalent exists).

## References

### HLD
- docs/high-level-design.md — "CORS Circular Dependency Resolution", "Multi-Workspace Infrastructure" sections

### LLD
- docs/llds/cloud-platform.md

### EARS
- docs/specs/cloud-platform-specs.md (18 specs, all [x])

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
| API Gateway | CP-INFRA-001 to 006 | 6 | 0 | 0 |
| Lambda | CP-INFRA-010 to 012 | 3 | 0 | 0 |
| CORS Lifecycle | CP-INFRA-020 to 022 | 3 | 0 | 0 |
| Cognito | CP-INFRA-030 to 032 | 3 | 0 | 0 |
| Amplify & Frontend Delivery | CP-INFRA-040 to 042 | 3 | 0 | 0 |
| DynamoDB | CP-INFRA-050 to 051 | 2 | 0 | 0 |
| Workspace Isolation | CP-INFRA-060 to 061 | 2 | 0 | 0 |

**Summary:** 22 of 22 active specs implemented; 0 deferred; 0 gaps.

## Key Findings

1. **ECR outside Terraform** — The `sudoku-image-recognition` ECR repository is created by `scripts/bootstrap.sh`, not Terraform. A first-time deploy will fail without it. (CP-INFRA-012)
2. **No CloudWatch alarms** — Lambda errors produce log entries but trigger no alert. Silent failures are possible in production.
3. **`ignore_changes` drift** — `ignore_changes` on CORS and Cognito callback URLs means `terraform plan` always shows "no changes" for these fields even when live config differs from baseline. Actual config only visible via AWS console or CLI.
4. **`/games/current` reserved path** — The string "current" is implicitly reserved as a gameId value due to API Gateway route precedence. No enforcement exists to prevent a UUID colliding with this string (astronomically unlikely but undocumented).
5. **Required variables undocumented** — `google_client_id`, `google_client_secret`, and `github_token` have no defaults and no source documentation in `variables.tf`.

## Work Required

### Done

1. ~~ECR bootstrap prerequisite~~: `infra/README.md` Bootstrap section already documents the `scripts/infra/bootstrap.sh` prerequisite and the ECR repository it creates. (CP-INFRA-012)
2. ~~Variable source documentation~~: Added source location notes to `github_token`, `google_client_id`, and `google_client_secret` descriptions in `infra/variables.tf`.

### Deferred

1. Add CloudWatch alarms on Lambda error rate and throttle metrics with SNS notification. (separate feature work)
