# Arrow: Cloud Platform

Cloud infrastructure across two facets, each with its own IaC tool: **AWS** (Terraform, `infra/aws/` — Lambda, API Gateway, DynamoDB, Cognito, Amplify, Route53, IAM, ECR) and **GCP** (Pulumi/Python, `infra/gcp/` — Cloud Run, Firestore, Identity Platform, Firebase Hosting, Cloud DNS), plus the cloud-general deploy-target selection (`CP-CD-*`).

## Status

**IN_PROGRESS** - 2026-09-11. The GCP facet is live end-to-end on the `sudoku-app-eo` project (backend + image-rec on Cloud Run, Firestore, Firebase Hosting, custom domain, automated post-deploy smoke) and is now being **re-platformed from Terraform to Pulumi** (Python + `uv`) into a clean-room GCP project. That migration also removes the last two AWS dependencies from the GCP path (cross-cloud Bedrock for image recognition; the Route53 CNAME) and introduces a single `DEPLOY_TARGET` repository variable selecting which cloud a merge to `main` deploys. Design: `docs/llds/cloud-platform-gcp.md`. Plan: `docs/planning/gcp-pulumi-replatform.md`.

The HLD's "manual identity on GCP" tenet is **deliberately revised** by this work — identity becomes IaC, with the privilege concern met by splitting the deploying identity across a human-run `bootstrap` stack and a CI-run `app` stack.

**AWS facet: OK** - 2026-07-09. Admin data-browser JWT routes added (`/admin/data/games`, `/admin/data/players`); `administrators` Cognito group provisioned. All 19 findings from `docs/planning/old/infra-review.md` (H1-H4, M1-M7, L1-L5) fixed and verified via live CI/Deploy against the `rc-terraform-review` workspace — see that doc for full detail. All Terraform files read and documented. No apply/drift audit performed (no Terratest or equivalent exists). **Unaffected by the Pulumi migration** — AWS stays on Terraform.

## References

### HLD
- docs/high-level-design.md — "CORS Circular Dependency Resolution", "Multi-Workspace Infrastructure" sections

### LLD
- docs/llds/cloud-platform.md (AWS facet + cloud-general)
- docs/llds/cloud-platform-gcp.md (GCP facet — Pulumi)

### EARS
- docs/specs/cloud-platform-specs.md — `CP-INFRA-*` (AWS), `CP-GCP-*` (GCP, cloud-behavioural), `CP-PUL-*` (GCP, Pulumi realisation), `CP-CD-*` (deploy-target selection)

### Tests
- AWS facet: no infrastructure tests exist (no Terratest or equivalent). This remains an accepted gap.
- GCP facet: `infra/gcp/tests/` — Pulumi ComponentResource unit tests against `pulumi.runtime.set_mocks()`, plus `test_no_authoritative_iam.py` as a structural guardrail. The first infrastructure test layer in the repo.

### Code
- infra/aws/main.tf, infra/aws/terraform.tf, infra/aws/variables.tf, infra/aws/outputs.tf
- infra/aws/lambda.tf, infra/aws/api_gateway.tf, infra/aws/dynamodb.tf
- infra/aws/cognito.tf, infra/aws/cognito-rc-shared.tf
- infra/aws/amplify.tf, infra/aws/domain.tf, infra/aws/iam.tf
- infra/aws/image_recognition_lambda.tf
- infra/gcp/components/, infra/gcp/bootstrap/, infra/gcp/app/, infra/gcp/tests/ (Pulumi — target)
- infra/gcp/*.tf and scripts/infra/gcp/*.sh (Terraform + bash — outgoing, live until cutover)
- docs/runbooks/gcp-manual-setup.md
- docs/planning/gcp-pulumi-replatform.md (+ per-phase plans)

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

## GCP Facet

**Scope:** provisions the GCP platform and runs the application end-to-end on it. Being re-platformed to Pulumi; `CP-GCP-*` specs are cloud-behavioural and survive the tool change, while Terraform-specific ones are struck through and replaced by `CP-PUL-*`.

### EARS Coverage — CP-GCP

| Category | Spec IDs | Implemented | Deferred | Gaps |
| --- | --- | --- | --- | --- |
| Compute (Cloud Run) | CP-GCP-001 to 004 | 4 | 0 | 0 |
| Edge & Authentication | CP-GCP-010 to 014 | 5 (014 superseded by CP-PUL-023) | 0 | 0 |
| Firestore | CP-GCP-020 to 024 | 5 | 0 | 0 |
| Identity Platform | CP-GCP-030 to 032 | 3 (031 superseded by CP-PUL-030) | 0 | 0 |
| Firebase Hosting & Frontend | CP-GCP-040 to 043 | 4 | 0 | 0 |
| DNS & TLS | CP-GCP-050 to 051 | 2 (050 superseded by CP-PUL-050) | 0 | 0 |
| Cost Guardrail | CP-GCP-060 to 061 | 1 (060) | 1 (061) | 0 |
| Labels | CP-GCP-070 | 1 | 0 | 0 |
| CI/CD, Bootstrap & WIF | CP-GCP-080 to 083 | 4 (081-083 superseded by CP-PUL-*) | 0 | 0 |
| AI Inference | CP-GCP-085, 089, 090 | 2 (085, 090) | 0 | 1 (089) |
| Networking | CP-GCP-091 | 0 | 1 | 0 |

**Summary (GCP facet, cloud-behavioural):** 31 of 34 CP-GCP specs implemented; 1 gap (CP-GCP-089, image recognition on Vertex); 2 deferred (CP-GCP-061, CP-GCP-091). Six specs are superseded by `CP-PUL-*` equivalents as the tool changes.

### EARS Coverage — CP-PUL (Pulumi realisation)

| Category | Spec IDs | Implemented | Deferred | Gaps |
| --- | --- | --- | --- | --- |
| Project Foundation & Bootstrap | CP-PUL-001 to 003 | 0 | 0 | 3 |
| Identity, Federation & Privilege Split | CP-PUL-010 to 013 | 0 | 0 | 4 |
| Application Stack | CP-PUL-020 to 023 | 0 | 0 | 4 |
| Identity Platform | CP-PUL-030 to 032 | 0 | 2 (031, 032) | 1 |
| State & Secrets | CP-PUL-040 to 042 | 0 | 0 | 3 |
| DNS | CP-PUL-050 | 0 | 0 | 1 |
| Cost Guardrail | CP-PUL-060 | 0 | 0 | 1 |
| Packaging & CI | CP-PUL-070, 080 to 082 | 0 | 0 | 4 |

**Summary (Pulumi realisation):** 0 of 21 active specs implemented (all gaps — implementation is phased); 2 deferred (`CP-PUL-031` no IaC resource for an Identity Platform user; `CP-PUL-032` no API creates OAuth client IDs).

### EARS Coverage — CP-CD (deploy-target selection)

| Category | Spec IDs | Implemented | Deferred | Gaps |
| --- | --- | --- | --- | --- |
| Deploy Target Selection | CP-CD-001 to 004 | 0 | 0 | 4 |

### GCP Key Findings

1. **Live and end-to-end.** In-app JWT validation, Firestore I/O, Firebase Auth, VITE injection + `firebase deploy`, WIF-in-CI and the Vertex coach all landed and serve in prod. The one remaining cloud-behavioural gap is `CP-GCP-089` (image recognition on Vertex).
2. **Only 11 resources were ever in Terraform.** The rest of the GCP platform — project, APIs, state bucket, Artifact Registry, three service accounts, every IAM binding, WIF, Identity Platform, Secret Manager — lived in six imperative bash scripts. That split was the original "manual identity" tenet, and it made the least-tested part of the system also the largest.
3. **The manual-identity tenet is revised, not abandoned.** Identity becomes IaC; the privilege concern is met by the two-stack split (human-run `bootstrap` owns identity, CI-run `app` owns application resources and cannot grant itself privilege). Net for the CI identity: +1 role, −all Secret Manager roles, no billing reach.
4. **Throttle deviation.** No request-rate limit on GCP (Cloud Run exposes none); load is bounded by max-instances × concurrency + per-request timeout + the app-layer per-user coach limiter. Accepted departure from `security-standards.md` 25 rps (CP-GCP-013).
5. **Dead local in `infra/gcp/main.tf:3`.** `is_rc = startswith(terraform.workspace, "rc-")` never matches — GCP workspaces derive from `rcg-*` branches, so `coach_bedrock_api_mode` was permanently `"invoke"` on GCP and the converse A/B never ran there. Low impact (the A/B resolved to `invoke` anyway) but the Pulumi port must not reproduce it.
6. **`bootstrap.sh` creates an unused Artifact Registry repository.** CI pushes both images to `sudoku-backend`; `sudoku-image-recognition` has never been used. The Pulumi facet creates one repository.

## Key Findings

1. **ECR outside Terraform** — The `sudoku-image-recognition` ECR repository is created by `scripts/bootstrap.sh`, not Terraform. A first-time deploy will fail without it. Documented in `infra/aws/README.md` Bootstrap section. (CP-INFRA-012)
2. **No CloudWatch alarms** — Lambda errors produce log entries but trigger no alert. Silent failures are possible in production. (deferred — separate feature work)
3. **`ignore_changes` drift** — `ignore_changes` on Cognito callback/logout URLs means `terraform plan` always shows "no changes" for these fields even when live config differs from baseline. Actual config only visible via AWS console or CLI. (API Gateway CORS no longer has this problem — it's fully Terraform-managed as of the infra-review M1 fix, 2026-07-08.)
4. **`/games/current` routing** — `GET /api/v1/games/current` is an explicit JWT-protected route at API Gateway. JAX-RS also routes `/current` to its own method before `/{gameId}` can match. The string "current" is fully protected at both layers; no backend guard is needed.
5. **`/dev/data/*` PII leak closed (2026-07-08)** — `$default` previously forwarded `/dev/data/*` to a Lambda handler (`DevDataResource`) with no build-profile guard and no gateway auth, exposing the full Games/Players tables unauthenticated in production. Fixed by deleting `DevDataResource` (moved to JWT+group-gated `/admin/data/*`). See `docs/planning/old/infra-review.md` finding H1 and `docs/llds/user-management.md` — Admin Authorization. (CP-INFRA-001, CP-INFRA-007)
6. **`/dev/hint-demo` intentionally stays universal** — an `@IfBuildProfile` guard was tried and reverted (broke RC smoke tests): the Java Lambda is built once and shared by every Terraform workspace, so the guard removed it from RC/beta too, where `VITE_DEV_TOOLS=true` still depends on it. No PII is involved, so it remains reachable via `$default` everywhere. (CP-INFRA-001)
7. **Admin group check is Lambda-only** — API Gateway's JWT authorizer has no concept of Cognito groups; it only proves the caller is authenticated. The `administrators` group membership check happens entirely in `AdminAuthorizationFilter` on the Lambda side.
8. **Infra review remediation (2026-07-08 to 2026-07-09)** — `docs/planning/old/infra-review.md` findings H2-L5 fixed: Bedrock budget kill-switch extended to the image recognition Lambda role; `required_version` raised to match native S3 locking; deletion protection added to Games/Players/Leaderboard tables and both Route53 zones; CORS agreement between API Gateway and the Lambda; Bedrock IAM policy deduplicated; main Lambda log-group retention now actually enforced in production (needed a `moved{}`/`import{}` migration, not just a module argument); `Environment` tag derived from the workspace instead of a var that silently defaulted to "prod"; anomaly monitor/subscription renamed to reflect their true account-wide scope; smoke-test CI auth moved off the public web client onto a dedicated secret-bearing client (required also fixing how Playwright seeds its pre-authenticated browser session, since it had been keying off the token's own `aud` claim); an orphaned, cost-accruing RC workspace (`rc-test-cicd`, state-locked since 2026-04-02) found and torn down while verifying `migrations.tf` cleanup; region hardcoding hoisted to `local.aws_region`; validation added to `image_recognition_image_uri`; an inverted Amplify auto-branch-creation flag fixed.

## Work Required

### In progress — GCP Pulumi re-platform

Ten phases, one PR each; full detail in `docs/planning/gcp-pulumi-replatform.md`.

1. **Phase 0** — documentation and drift fixes *(this change)*
2. **Phase 1** — Pulumi scaffolding + component unit tests + CI gate (no cloud resources)
3. **Phase 2** — bootstrap stack: the new GCP project, state, KMS, Artifact Registry, SAs, WIF, budget
4. **Phase 3** — app stack: Firestore, Hosting site, Cloud DNS zone + NS delegation
5. **Phase 4** — Identity Platform as code + the new OAuth client
6. **Phase 5** — de-Bedrock image recognition (Vertex Gemini vision + accuracy harness) *(parallel)*
7. **Phase 6** — app stack: Cloud Run ×2 + frontend, proven on an ephemeral `rcg-*` stack
8. **Phase 7** — unified deploy workflow + `DEPLOY_TARGET`
9. **Phase 8** — production cutover; **8b** — Cognito OAuth-client migration *(parallel)*
10. **Phase 9** — decommission `infra/gcp/*.tf`, six bash scripts, and the cross-cloud AWS key

### Deferred

1. Add CloudWatch alarms on Lambda error rate and throttle metrics with SNS notification. (separate feature work)
2. **GCP `[D]` items:** budget hard-cap enforcement via Pub/Sub Cloud Function (`CP-GCP-061`); private VPC egress (`CP-GCP-091`); an IaC resource for the Identity Platform smoke user (`CP-PUL-031`); OAuth client creation as code (`CP-PUL-032`, permanently — no API exists).
3. **Cross-cloud identity re-key** (separate cross-segment arrow): adopt the Google `sub` as canonical `userId` on AWS for full AWS↔GCP continuity.
4. **Migrating `infra/aws/` to Pulumi.** Explicitly not planned — AWS stays on Terraform.
