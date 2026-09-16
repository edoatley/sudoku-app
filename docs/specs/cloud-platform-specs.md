# Cloud Platform — EARS Specifications

## API Gateway

- [x] **CP-INFRA-001**: The system shall route all /puzzles/* and /health requests, plus any other unmatched path, to the Java Lambda via the $default route without JWT validation.
- [x] **CP-INFRA-002**: The system shall require a valid Cognito JWT on all /api/v1/games/*, /players/me, and /ai/* routes, validated by API Gateway before the Lambda or Image Recognition Lambda is invoked.
- [x] **CP-INFRA-003**: The system shall route POST /api/v1/ai/image-to-puzzle to the Image Recognition Lambda with JWT validation.
- [x] **CP-INFRA-004**: The system shall route GET /api/v1/ai/image-to-puzzle/warmup to the Image Recognition Lambda without JWT validation.
- [x] **CP-INFRA-005**: The system shall apply throttling of burst=50 requests and rate=25 requests/second at the API Gateway stage.
- [x] **CP-INFRA-006**: The system shall write API access logs in JSON format to CloudWatch with 7-day retention in the default workspace and 3-day retention in all other workspaces.
- [x] **CP-INFRA-007**: The system shall require a valid Cognito JWT on GET /api/v1/admin/data/games and GET /api/v1/admin/data/players, validated by API Gateway before the Lambda is invoked. (Group-level authorization beyond JWT validity is enforced in the Lambda — see UM-BE-060/061.)

## Lambda

- [x] **CP-INFRA-010**: The system shall deploy the Java Lambda as a container image (the same `Dockerfile.jvm-lwa` image used by Cloud Run) so both clouds run an identical artifact. SnapStart is not used — AWS does not support it for container-image Lambda functions.
- [x] **CP-INFRA-011**: The system shall route API Gateway traffic to the Java Lambda via a "live" alias pointing to the latest published version.
- [x] **CP-INFRA-012**: The system shall deploy the Image Recognition Lambda as a container image to support native Python dependencies.

## CORS Lifecycle

- [x] **CP-INFRA-020**: The system shall set API Gateway CORS allowed origins directly in Terraform to the static custom domain(s) for each workspace type, with no post-apply tightening step.
- [x] **CP-INFRA-021**: The system shall tighten Cognito app client callback/logout URLs via a post-apply CI workflow once the exact Amplify branch URL is known.
- [x] **CP-INFRA-022**: The system shall ignore Cognito callback/logout URL changes in Terraform state to preserve post-apply additions across subsequent applies.
- [x] **CP-INFRA-023**: When an rc-* branch is deleted, the system shall remove that branch's Amplify URL from the shared rc Cognito client's callback/logout URL lists as a best-effort step that does not block the underlying Terraform destroy on failure, and shall never remove the shared beta domain or localhost entries.

## Cognito

- [x] **CP-INFRA-030**: The system shall configure Cognito for social-only login via Google OAuth, with no native username/password sign-up.
- [x] **CP-INFRA-031**: The system shall maintain a single shared Cognito pool for all rc-* workspaces to avoid multiplying Google OAuth redirect URIs.
- [x] **CP-INFRA-032**: The system shall provision a smoke-test Cognito user with username/password auth for use by CI pipelines.
- [x] **CP-INFRA-033**: The system shall NOT enable native username/password auth on the public web app client; CI pipelines shall instead authenticate the smoke-test user via a separate, secret-bearing smoke-test app client.

## Amplify & Frontend Delivery

- [x] **CP-INFRA-040**: The system shall disable Amplify auto-build and trigger builds explicitly via CI after terraform apply.
- [x] **CP-INFRA-041**: The system shall inject VITE_API_URL, VITE_COGNITO_USER_POOL_ID, VITE_COGNITO_CLIENT_ID, VITE_COGNITO_DOMAIN, VITE_MOCK_API, VITE_DEV_TOOLS, and VITE_AI_COACH as Amplify environment variables at build time.
- [x] **CP-INFRA-042**: The system shall set VITE_DEV_TOOLS=false in the default (production) workspace and VITE_DEV_TOOLS=true in all other workspaces.

## DynamoDB

- [x] **CP-INFRA-050**: The system shall provision DynamoDB tables with PAY_PER_REQUEST billing.
- [x] **CP-INFRA-051**: The system shall enable point-in-time recovery on the Games, Players, and Leaderboard tables in the default workspace only (not the ephemeral, TTL-based CoachRateLimits table).
- [x] **CP-INFRA-052**: The system shall enable deletion protection on the Games, Players, and Leaderboard tables in the default workspace only.

## Workspace Isolation

- [x] **CP-INFRA-060**: The system shall append -{workspace} to all resource names in non-default workspaces to prevent naming collisions. (Cloud-general: honoured by both the AWS and GCP facets.)
- [D] **CP-INFRA-061**: ~~The system shall share the Lambda zip S3 bucket across all workspaces, with each workspace uploading to its own key prefix.~~ Superseded by the zip→container-image migration: the backend and image-recognition Lambdas now deploy from ECR container images (`infra/aws/lambda.tf`, `image_recognition_lambda.tf`), so no shared zip bucket is provisioned. The only residual reference is a vestigial IAM grant in `scripts/infra/aws/bootstrap.sh` — a separate optional cleanup, not part of this spec.

---

# Deploy Target Selection (cloud-general)

Which cloud a deployment goes to. Resolved in one place — `.github/workflows/ci-deploy.yml`'s
`select-target` job — and honoured identically by both facets.

- [ ] **CP-CD-001**: The system shall select the deploy target for a push to `main` from the repository variable `DEPLOY_TARGET` (aws | gcp | both), defaulting to aws when the variable is unset.
- [ ] **CP-CD-002**: The system shall deploy `rc-*` branches to AWS and `rcg-*` branches to GCP regardless of `DEPLOY_TARGET`, so release-candidate environments are governed by branch convention alone.
- [ ] **CP-CD-003**: Where a workflow_dispatch supplies a `target` input other than `auto`, the system shall use it in preference to both `DEPLOY_TARGET` and the branch convention.
- [ ] **CP-CD-004**: Where the target is both, the system shall deploy each cloud independently to its own stable hostname and shall report the run as failed if either cloud's deployment or smoke test fails. (Deploy selection only — this is not a DNS failover.)

---

# GCP Facet

Specifications for the GCP realisation of the Cloud Platform (`infra/gcp/`). All are active gaps (`[ ]`) until the GCP facet is implemented; genuinely deferred items are marked `[D]`.

**Scope:** the GCP facet provisions the GCP platform plus the full application runtime slice. It is being **re-platformed from Terraform to Pulumi** (Python + `uv`) in a clean-room GCP project — see `docs/llds/cloud-platform-gcp.md` and `docs/planning/gcp-pulumi-replatform.md`. Specs below split in two: `CP-GCP-*` are cloud-behavioural (what the GCP platform does, tool-agnostic) and `CP-PUL-*` are the Pulumi realisation. Terraform-specific `CP-GCP-*` specs are struck through as they are superseded.

**Struck-through specs still have live implementations.** Intent moves at Phase 0; the Terraform
and bash that implement it are deleted at Phase 9, after the production cutover. So
`infra/gcp/*.tf` and `scripts/infra/gcp/*.sh` still carry `@spec` annotations for
`CP-GCP-014/031/050/081/082/083` — correctly, because that code is what satisfies them today.
Those annotations disappear with the files. The auth/persistence runtime specs for that slice have landed — in-app JWT validation (CP-GCP-010/011), in-app CORS (CP-GCP-012), and the CI image-build + Firebase Hosting deploy (CP-GCP-041, CP-GCP-080). The Identity Platform test user + `signInWithPassword` token minting (CP-GCP-032) have landed. Firestore I/O for leaderboard + coach-rate-limit (CP-GCP-021), cross-cloud Bedrock (CP-GCP-085), image recognition on Cloud Run (gap E), the full VITE_* injection set (CP-GCP-042/043), and non-default public invoker (CP-GCP-014) have landed. Deployment-target readiness (gap G — hosted-UI Hosting targets, prod invoker, Identity Platform authorized domains, per-env smoke) is closed by the Pulumi re-platform. The GCP custom domain moves from `sudoku-gcp.edoatley.co.uk` (a CNAME in the AWS Route53 parent zone) to `sudoku.gcp.edoatley.co.uk` (a record in a Pulumi-managed Cloud DNS zone).

## GCP — Compute (Cloud Run)

- [x] **CP-GCP-001**: The system shall deploy the Quarkus backend as a Cloud Run service (sudoku{suffix}) from an Artifact Registry container image, with a minimum instance count of zero.
- [x] **CP-GCP-002**: The system shall deploy the image-recognition service as a Cloud Run service (sudoku-image-recognition{suffix}) from an Artifact Registry container image, with a minimum instance count of zero.
- [x] **CP-GCP-003**: The system shall run each Cloud Run service as a runtime service account supplied by configuration, not the project default compute service account.
- [x] **CP-GCP-004**: The system shall cap the maximum instance count and container concurrency of both Cloud Run services to bound concurrent execution and spend.

## GCP — Edge & Authentication

- [x] **CP-GCP-010**: The system shall expose the Cloud Run services directly without an API gateway and validate Identity Platform JWTs within the backend application.
- [x] **CP-GCP-011**: The system shall validate backend JWTs against the Identity Platform issuer (https://securetoken.google.com/{project_id}) and audience ({project_id}), using explicit key/issuer/audience configuration rather than OIDC discovery.
- [x] **CP-GCP-012**: The system shall apply CORS allowed origins within the backend application (CORS_ALLOWED_ORIGINS), restricted to the static custom domain(s) and localhost for the workspace type.
- [x] **CP-GCP-013**: The system shall bound backend request load on GCP via Cloud Run maximum-instance and container-concurrency caps in place of an API-Gateway request-rate throttle.
- [x] **CP-GCP-014**: ~~The system shall grant roles/run.invoker to allUsers on the backend and image-recognition Cloud Run services in non-default (RC) workspaces so a browser can reach them without a manual per-deploy grant; the default (prod) workspace's invoker stays manual (gap G1).~~ Superseded by `CP-PUL-023`: the invoker is granted on **every** stack including prod. Terraform withheld it in prod so an apply could not toggle production reachability, but under the two-stack split the app stack already holds `roles/run.admin` and can delete the service outright — so withholding one additive binding buys nothing and costs a manual step on every recreation. This grants network reachability only; each service still validates the caller's JWT in-app.

## GCP — Firestore

- [x] **CP-GCP-020**: The system shall provision a Firestore database in Native mode per environment — the (default) database in the production environment and a named sudoku{suffix} database otherwise. (Environments are Pulumi stacks: `prod`, and ephemeral `rcg-*`.)
- [x] **CP-GCP-021**: The system shall store game, player, leaderboard, and coach-rate-limit data in Firestore collections games, players, leaderboard, and coachRateLimits respectively.
- [x] **CP-GCP-022**: The system shall apply a TTL policy on the coachRateLimits collection keyed on the expiresAt field.
- [x] **CP-GCP-023**: The system shall enable Firestore point-in-time recovery and delete protection on the default-workspace database only.
- [x] **CP-GCP-024**: The system shall locate Firestore in us-central1.

## GCP — Identity Platform

- [x] **CP-GCP-030**: The system shall authenticate end users via Identity Platform with Google as the sole sign-in provider, with no native username/password sign-up offered in the app.
- [x] **CP-GCP-031**: ~~The system shall provision the Identity Platform configuration and Google provider outside Terraform, with Terraform consuming the resulting issuer and audience by configuration.~~ Superseded by `CP-PUL-030`: Identity Platform config, the Google IdP, and the authorized-domain list are provisioned as code. Only the OAuth consent screen and the OAuth 2.0 client stay manual, because no GCP API creates OAuth client IDs.
- [x] **CP-GCP-032**: The system shall provide a manually-created Identity Platform test user that CI authenticates via the Identity Platform signInWithPassword REST endpoint.

## GCP — Firebase Hosting & Frontend Delivery

- [x] **CP-GCP-040**: The system shall host the frontend on a Firebase Hosting site (sudoku{suffix}) with a single-page-app rewrite of all paths to /index.html.
- [x] **CP-GCP-041**: The system shall deploy the frontend to Firebase Hosting via CI after terraform apply, not on push, so build-time VITE_* values reflect the applied infrastructure.
- [x] **CP-GCP-042**: The system shall inject VITE_AUTH_PROVIDER=firebase, VITE_API_URL (backend Cloud Run URL + /api/v1), VITE_IMAGE_RECOGNITION_URL (the image-recognition Cloud Run URL), the Identity Platform equivalents of the VITE_COGNITO_* values (VITE_FIREBASE_*), VITE_MOCK_API, VITE_DEV_TOOLS, and VITE_AI_COACH into the frontend build.
- [x] **CP-GCP-043**: The system shall set VITE_DEV_TOOLS=false in the default workspace and VITE_DEV_TOOLS=true in all other workspaces.

## GCP — DNS & TLS

- [x] **CP-GCP-050**: ~~The system shall point sudoku-gcp.edoatley.co.uk at Firebase Hosting via a single CNAME to the Hosting default site, upserted once in the parent edoatley.co.uk zone (AWS Route53) — no GCP Cloud DNS managed zone.~~ Superseded by `CP-PUL-050`. The stated rationale — *Cloud DNS has no apex-alias equivalent* — only applies at a **zone apex**; the new host `sudoku.gcp.edoatley.co.uk` sits inside a delegated `gcp.edoatley.co.uk` zone, where an ordinary CNAME works. Retaining the Route53 record was also the last AWS dependency in the GCP serving path.
- [x] **CP-GCP-051**: The system shall serve the frontend over Google-managed TLS certificates, with no manual certificate provisioning.

## GCP — Cost Guardrail

- [x] **CP-GCP-060**: The system shall provision a monthly Cloud Billing budget that publishes threshold alerts to a Pub/Sub topic.
- [D] **CP-GCP-061**: The system shall automatically enforce the billing cap (e.g. disabling billing or scaling services to zero) when the budget is exceeded. (Deferred — GCP budgets cannot attach a deny action; requires a Pub/Sub-triggered Cloud Function.)

## GCP — Labels

- [x] **CP-GCP-070**: The system shall apply the labels project=sudoku, managed_by=pulumi, and environment (prod in the production stack, else the sanitized stack name) to GCP resources, using lowercase label values.

## GCP — CI/CD, Bootstrap & Identity Federation

- [x] **CP-GCP-080**: The system shall authenticate GitHub Actions to GCP via Workload Identity Federation impersonating a deploy service account, with no long-lived service-account keys.
- [x] **CP-GCP-081**: ~~The system shall validate infra/gcp with terraform fmt, init, and validate in CI when files under infra/gcp/ change.~~ Superseded by `CP-PUL-081` and `CP-PUL-082` — `infra/gcp` ceases to be Terraform.
- [x] **CP-GCP-082**: ~~The system's bootstrap process shall create the project and link billing, create the GCS Terraform state bucket, enable the required GCP APIs, create the sudoku-backend and sudoku-image-recognition Artifact Registry repositories, and create the runtime + deploy service accounts.~~ Superseded by `CP-PUL-001..003`: the same inventory is declared in the `bootstrap` Pulumi stack rather than a bash script, with **one** Artifact Registry repository (the second has never been used by CI).
- [x] **CP-GCP-083**: ~~The system shall provision GCP service accounts, IAM role bindings, Workload Identity Federation, and Identity Platform outside Terraform; infra/gcp Terraform shall reference them by value only.~~ Superseded by `CP-PUL-010..013`. This retires the HLD's original "manual identity" tenet: identity is now IaC, with the privilege concern met by splitting the deploying identity across two stacks rather than by removing it from automation.

## GCP — AI Inference

- [x] **CP-GCP-085**: *(Slated for retirement — struck through once `CP-GCP-089` lands and image recognition leaves Bedrock; after that GCP holds no long-lived credentials and Secret Manager is removed from the project entirely.)* Where AI features (coach, image recognition) are enabled on GCP, the system shall invoke AWS Bedrock cross-cloud using credentials sourced from Secret Manager. (Coach: when `enable_coach = true`, the backend Cloud Run service mounts the manually-created Bedrock access-key secrets as `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` and the run SA is granted `secretmanager.secretAccessor`; the SDK's default credential chain resolves them with no code change. Image recognition on Cloud Run remains gap E.)
- [ ] **CP-GCP-089**: The system shall perform **image-recognition** inference on GCP via Vertex AI (Gemini vision) authenticated by the image-recognition runtime service account, selected behind an `IMAGE_AI_PROVIDER` switch mirroring the coach's `CoachAiClient` port, retiring the last cross-cloud Bedrock dependency. See `docs/specs/image-recognition-specs.md` IR-AI-001..006.
- [x] **CP-GCP-090**: The system shall perform **coach** AI inference on GCP via Vertex AI (Gemini) authenticated by the runtime service account, replacing cross-cloud Bedrock, selected behind the `CoachAiClient` port (`coach.ai.provider=vertex`). See `docs/specs/sudoku-coach-specs.md` SC-GCP-001..007.

## GCP — Networking

- [D] **CP-GCP-091**: The system shall route Cloud Run egress to Firestore over a private VPC connector. (Deferred — Cloud Run uses Google's managed public API in the interim.)

---

# GCP Facet — Pulumi Realisation

Specifications for the Pulumi (Python + `uv`) realisation of the GCP facet — `infra/gcp/`. Design:
`docs/llds/cloud-platform-gcp.md`. Migration: `docs/planning/gcp-pulumi-replatform.md`.

`CP-PUL` is a new facet token. The realisation surface changed wholesale, and the `CP-GCP` block
(001–091) is crowded; keeping the cloud-behavioural specs (`CP-GCP-*`) separate from the
tool-specific ones (`CP-PUL-*`) is what let the Terraform specs be struck through cleanly.

## Pulumi — Project Foundation & Bootstrap

- [x] **CP-PUL-001**: The system's bootstrap stack shall create the GCP project, link its billing account, and enable the required GCP APIs, with API enablement configured not to disable those APIs when the stack is destroyed.
- [x] **CP-PUL-002**: The system's bootstrap stack shall create the project with automatic default-network creation disabled, so no default VPC or its firewall rules exist.
- [x] **CP-PUL-003**: The system's bootstrap stack shall create a single Artifact Registry Docker repository with cleanup policies retaining the ten most recent versions and deleting untagged versions older than thirty days.

## Pulumi — Identity, Federation & Privilege Split

- [x] **CP-PUL-010**: The system shall provision the backend, image-recognition, and deploy service accounts as code, granting the backend and image-recognition runtime accounts roles/datastore.user and roles/aiplatform.user.
- [x] **CP-PUL-011**: The system shall provision Workload Identity Federation for GitHub Actions as code, with an attribute condition restricting token exchange to the edoatley/sudoku-app repository.
- [x] **CP-PUL-012**: The system shall grant the CI deploy service account only the roles needed to manage application resources, and shall grant it no IAM-administration, Workload-Identity-administration, Secret Manager, or billing-account permission.
- [x] **CP-PUL-013**: The system shall express every IAM grant as an additive per-member binding, and shall never use a role-authoritative or project-authoritative IAM resource; a build-time check shall fail if one is introduced.

## Pulumi — Application Stack

- [ ] **CP-PUL-020**: The system shall deploy both Cloud Run services from a single reusable component, parameterised by image, runtime service account, environment, instance cap and concurrency.
- [x] **CP-PUL-021**: The system shall derive environment names, resource suffixes, label values and the Firebase Hosting site id from a single shared implementation used by both the Pulumi program and CI, so the two cannot drift.
- [x] **CP-PUL-022**: The system shall protect the production Firestore database, Hosting site, DNS zone, KMS key and state bucket against deletion and replacement.
- [ ] **CP-PUL-023**: The system shall grant roles/run.invoker to allUsers on both Cloud Run services in every environment including production, granting network reachability only; each service continues to validate the caller's JWT in-app. (Supersedes `CP-GCP-014`.)

## Pulumi — Identity Platform

- [x] **CP-PUL-030**: The system shall provision the Identity Platform configuration, its Google sign-in provider, and its authorized-domain list as code, with the Google OAuth client secret held as an encrypted stack configuration value. (Supersedes `CP-GCP-031`.)
- [D] **CP-PUL-031**: The system shall provision the Identity Platform smoke-test user as code. (Deferred — no IaC resource exists for an Identity Platform user; `scripts/infra/gcp/create-smoke-user.sh` is retained. A custom dynamic provider calling accounts:signUp is a possible future option.)
- [D] **CP-PUL-032**: The system shall provision the OAuth consent screen and OAuth 2.0 client as code. (Deferred permanently — no GCP API creates OAuth client IDs.)

## Pulumi — State & Secrets

- [x] **CP-PUL-040**: The system shall store Pulumi state in a Cloud Storage bucket with object versioning, uniform bucket-level access, and public access prevention enforced.
- [x] **CP-PUL-041**: The system shall encrypt application-stack secrets with a Cloud KMS key, and shall grant the CI deploy service account decrypt access to that key.
- [x] **CP-PUL-042**: The system shall commit its dependency lock file so provider versions are pinned in CI.

## Pulumi — DNS

- [x] **CP-PUL-050**: The system shall provision a Cloud DNS managed zone for gcp.edoatley.co.uk and serve the frontend at sudoku.gcp.edoatley.co.uk from a record in that zone, with the parent zone requiring only a one-time NS delegation. (Supersedes `CP-GCP-050`.)

## Pulumi — Cost Guardrail

- [x] **CP-PUL-060**: The system shall provision the billing budget in the bootstrap stack rather than the application stack, because a billing budget is scoped to the billing account and the CI identity must hold no billing permission.

## Pulumi — Packaging & CI

- [x] **CP-PUL-070**: The system shall manage the Pulumi program's Python dependencies with uv, declared in pyproject.toml and resolved by the Pulumi runtime toolchain.
- [ ] **CP-PUL-080**: The system shall deploy GCP from a reusable workflow invoked by the single deploy entry point, with no push or workflow_dispatch trigger of its own.
- [x] **CP-PUL-081**: The system shall unit-test every Pulumi component against the Pulumi mock runtime, with no cloud credentials, and shall run those tests plus a linter in CI when files under infra/gcp/ change.
- [x] **CP-PUL-082**: The system shall include the Pulumi lint and unit-test suite in the mandatory pre-push test script.
