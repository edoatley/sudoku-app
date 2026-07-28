# Arrow: Cloud Platform

Cloud infrastructure across two facets: **AWS** (`infra/aws/` — Lambda, API Gateway, DynamoDB, Cognito, Amplify, Route53, IAM, ECR) and **GCP** (`infra/gcp/` — Cloud Run, Firestore, Identity Platform, Firebase Hosting, Cloud DNS).

## Status

**IN_PROGRESS** - 2026-07-17. GCP facet added as **infrastructure scaffolding** (`infra/gcp/`): Cloud Run, Firestore, Firebase Hosting, Cloud DNS, and a billing budget, plus `scripts/infra/gcp-bootstrap.sh`, the manual runbook `docs/runbooks/gcp-manual-setup.md`, and the CI validate gate. Terraform is `fmt`/`validate`/`checkov`-clean but **not yet applied to a live GCP project** (no project/credentials in this environment — same "no apply/drift audit" caveat as the AWS facet). 18 infra CP-GCP specs implemented; 11 gaps + the `deploy-gcp` CI job land with the app-adapter arrows; 3 deferred. IAM/SA/WIF/Identity Platform are deliberately manual (the ACE-exam learning surface), not Terraform. See the GCP Facet section below.

**AWS facet: OK** - 2026-07-09. Admin data-browser JWT routes added (`/admin/data/games`, `/admin/data/players`); `administrators` Cognito group provisioned. All 19 findings from `docs/planning/infra-review.md` (H1-H4, M1-M7, L1-L5) fixed and verified via live CI/Deploy against the `rc-terraform-review` workspace — see that doc for full detail. All Terraform files read and documented. No apply/drift audit performed (no Terratest or equivalent exists).

## References

### HLD
- docs/high-level-design.md — "CORS Circular Dependency Resolution", "Multi-Workspace Infrastructure" sections

### LLD
- docs/llds/cloud-platform.md

### EARS
- docs/specs/cloud-platform-specs.md (25 specs, all [x])

### Tests
- No infrastructure tests exist (no Terratest or equivalent). This is an accepted gap.

### Code
- infra/aws/main.tf, infra/aws/terraform.tf, infra/aws/variables.tf, infra/aws/outputs.tf
- infra/aws/lambda.tf, infra/aws/api_gateway.tf, infra/aws/dynamodb.tf
- infra/aws/cognito.tf, infra/aws/cognito-rc-shared.tf
- infra/aws/amplify.tf, infra/aws/domain.tf, infra/aws/iam.tf
- infra/aws/image_recognition_lambda.tf
- infra/gcp/terraform.tf, infra/gcp/main.tf, infra/gcp/variables.tf, infra/gcp/outputs.tf
- infra/gcp/cloud_run.tf, infra/gcp/image_recognition.tf, infra/gcp/firestore.tf
- infra/gcp/firebase_hosting.tf, infra/gcp/dns.tf, infra/gcp/budgets.tf
- scripts/infra/gcp-bootstrap.sh
- docs/runbooks/gcp-manual-setup.md (manual SA/IAM/WIF/Identity Platform setup)
- docs/planning/gcp-terraform-infrastructure-plan.md

## Architecture

**Purpose:** Provision and connect all AWS services required to run the application across multiple environment workspaces.

**Key Components:**
1. API Gateway HTTP v2 — single API routing to two Lambdas; JWT authorizer; throttling
2. Java Lambda — container image (`Dockerfile.jvm-lwa`, same artifact as GCP Cloud Run), "live" alias, ECR deployment
3. Image Recognition Lambda — container image, 60s timeout
4. DynamoDB — four tables (Games, Players, Leaderboard, CoachRateLimits), PAY_PER_REQUEST, PITR + deletion protection on Games/Players/Leaderboard in production only
5. Cognito — social-only (Google OAuth) on the public web client; shared RC pool; smoke-test user for CI authenticates via a separate, secret-bearing smoke-test app client (not the web client)
6. Amplify — manual build trigger; VITE_* env injection at build time
7. Route53 — production and beta zones; NS delegation from parent account

## EARS Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
| --- | --- | --- | --- | --- |
| API Gateway | CP-INFRA-001 to 007 | 7 | 0 | 0 |
| Lambda | CP-INFRA-010 to 012 | 3 | 0 | 0 |
| CORS Lifecycle | CP-INFRA-020 to 023 | 4 | 0 | 0 |
| Cognito | CP-INFRA-030 to 033 | 4 | 0 | 0 |
| Amplify & Frontend Delivery | CP-INFRA-040 to 042 | 3 | 0 | 0 |
| DynamoDB | CP-INFRA-050 to 052 | 3 | 0 | 0 |
| Workspace Isolation | CP-INFRA-060 to 061 | 2 | 0 | 0 |

**Summary (AWS facet):** 26 of 26 active specs implemented; 0 deferred; 0 gaps. (Corrects a prior undercount of 25 — the CORS Lifecycle row omitted CP-INFRA-023.)

## GCP Facet (infrastructure scaffolding)

**Scope:** provisions the GCP platform (`infra/gcp/`) and keeps it `validate`/`plan`-clean. Does **not** make the app run end-to-end on GCP — the backend Firestore adapter, frontend Firebase Auth path, and cross-cloud Bedrock wiring are separate future arrows.

### EARS Coverage — CP-GCP

| Category | Spec IDs | Implemented | Deferred | Gaps |
| --- | --- | --- | --- | --- |
| Compute (Cloud Run) | CP-GCP-001 to 004 | 4 | 0 | 0 |
| Edge & Authentication | CP-GCP-010 to 013 | 1 (013) | 0 | 3 (010,011,012) |
| Firestore | CP-GCP-020 to 024 | 4 | 0 | 1 (021) |
| Identity Platform | CP-GCP-030 to 032 | 1 (031) | 0 | 2 (030,032) |
| Firebase Hosting & Frontend | CP-GCP-040 to 043 | 1 (040) | 0 | 3 (041,042,043) |
| DNS & TLS | CP-GCP-050 to 051 | 2 | 0 | 0 |
| Cost Guardrail | CP-GCP-060 to 061 | 1 (060) | 1 (061) | 0 |
| Labels | CP-GCP-070 | 1 | 0 | 0 |
| CI/CD, Bootstrap & WIF | CP-GCP-080 to 083 | 3 (081,082,083) | 0 | 1 (080) |
| AI Inference | CP-GCP-085, 090 | 0 | 1 (090) | 1 (085) |
| Networking | CP-GCP-091 | 0 | 1 | 0 |

**Summary (GCP facet):** 18 infra specs implemented (terraform `fmt`/`validate`/`checkov`-clean, **not yet applied to a live GCP project**); 3 deferred; 11 gaps owned by the app-adapter arrows.

### GCP Key Findings

1. **Infra-only scaffolding.** The 11 gaps are runtime/app behaviours (in-app JWT validation, Firestore document I/O, Firebase Auth consumption, VITE injection + `firebase deploy`, WIF-in-CI, cross-cloud Bedrock) satisfied by the backend/frontend arrows, not this infra arrow.
2. **`deploy-gcp` CI job deferred.** A functional GCP deploy needs a runnable app (backend Firestore + frontend Firebase Auth) and a GCP image-build pipeline; wiring a non-functional/dormant deploy now would be speculative (Rule 2). Only the always-on `terraform-validate` matrix gate is active for `infra/gcp`.
3. **Identity/IAM/WIF manual by design.** No `iam.tf` / `identity_platform.tf`; provisioned by hand per `docs/runbooks/gcp-manual-setup.md`. Terraform references SA emails + Identity Platform issuer/audience by variable.
4. **Throttle deviation.** No request-rate limit on GCP (Cloud Run exposes none); load is bounded by max-instances × concurrency + per-request timeout + the app-layer per-user coach limiter. Accepted departure from `security-standards.md` 25 rps (CP-GCP-013).
5. **Not applied to a live project.** Verification is limited to `fmt`/`validate`/`checkov`; no `terraform apply`/drift audit (no GCP project/credentials available) — same class of gap the AWS facet documents.

## Key Findings

1. **ECR outside Terraform** — The `sudoku-image-recognition` ECR repository is created by `scripts/bootstrap.sh`, not Terraform. A first-time deploy will fail without it. Documented in `infra/aws/README.md` Bootstrap section. (CP-INFRA-012)
2. **No CloudWatch alarms** — Lambda errors produce log entries but trigger no alert. Silent failures are possible in production. (deferred — separate feature work)
3. **`ignore_changes` drift** — `ignore_changes` on Cognito callback/logout URLs means `terraform plan` always shows "no changes" for these fields even when live config differs from baseline. Actual config only visible via AWS console or CLI. (API Gateway CORS no longer has this problem — it's fully Terraform-managed as of the infra-review M1 fix, 2026-07-08.)
4. **`/games/current` routing** — `GET /api/v1/games/current` is an explicit JWT-protected route at API Gateway. JAX-RS also routes `/current` to its own method before `/{gameId}` can match. The string "current" is fully protected at both layers; no backend guard is needed.
5. **`/dev/data/*` PII leak closed (2026-07-08)** — `$default` previously forwarded `/dev/data/*` to a Lambda handler (`DevDataResource`) with no build-profile guard and no gateway auth, exposing the full Games/Players tables unauthenticated in production. Fixed by deleting `DevDataResource` (moved to JWT+group-gated `/admin/data/*`). See `docs/planning/infra-review.md` finding H1 and `docs/llds/user-management.md` — Admin Authorization. (CP-INFRA-001, CP-INFRA-007)
6. **`/dev/hint-demo` intentionally stays universal** — an `@IfBuildProfile` guard was tried and reverted (broke RC smoke tests): the Java Lambda is built once and shared by every Terraform workspace, so the guard removed it from RC/beta too, where `VITE_DEV_TOOLS=true` still depends on it. No PII is involved, so it remains reachable via `$default` everywhere. (CP-INFRA-001)
7. **Admin group check is Lambda-only** — API Gateway's JWT authorizer has no concept of Cognito groups; it only proves the caller is authenticated. The `administrators` group membership check happens entirely in `AdminAuthorizationFilter` on the Lambda side.
8. **Infra review remediation (2026-07-08 to 2026-07-09)** — `docs/planning/infra-review.md` findings H2-L5 fixed: Bedrock budget kill-switch extended to the image recognition Lambda role; `required_version` raised to match native S3 locking; deletion protection added to Games/Players/Leaderboard tables and both Route53 zones; CORS agreement between API Gateway and the Lambda; Bedrock IAM policy deduplicated; main Lambda log-group retention now actually enforced in production (needed a `moved{}`/`import{}` migration, not just a module argument); `Environment` tag derived from the workspace instead of a var that silently defaulted to "prod"; anomaly monitor/subscription renamed to reflect their true account-wide scope; smoke-test CI auth moved off the public web client onto a dedicated secret-bearing client (required also fixing how Playwright seeds its pre-authenticated browser session, since it had been keying off the token's own `aud` claim); an orphaned, cost-accruing RC workspace (`rc-test-cicd`, state-locked since 2026-04-02) found and torn down while verifying `migrations.tf` cleanup; region hardcoding hoisted to `local.aws_region`; validation added to `image_recognition_image_uri`; an inverted Amplify auto-branch-creation flag fixed.

## Work Required

### Deferred

1. Add CloudWatch alarms on Lambda error rate and throttle metrics with SNS notification. (separate feature work)
2. **GCP app-adapter arrows** (make GCP run end-to-end): backend Firestore persistence profile (CP-GCP-021), backend in-app JWT validation + CORS (CP-GCP-010,011,012), frontend Firebase Auth path (CP-GCP-030,042,043), `deploy-gcp` CI job + WIF-in-CI + `firebase deploy` + GCP smoke test (CP-GCP-032,041,080), cross-cloud Bedrock wiring (CP-GCP-085).
3. **GCP `[D]` items:** budget hard-cap enforcement via Pub/Sub Cloud Function (CP-GCP-061); Vertex AI migration (CP-GCP-090); private VPC egress (CP-GCP-091).
4. **Cross-cloud identity re-key** (separate cross-segment arrow): adopt the Google `sub` as canonical `userId` + DynamoDB→Firestore migration for AWS→GCP continuity.
5. **Apply `infra/gcp` to a live GCP project** once created, to validate the plan against real APIs (bootstrap → runbook → `terraform apply`).
