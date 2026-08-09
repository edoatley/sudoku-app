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
- [x] **CP-INFRA-061**: The system shall share the Lambda zip S3 bucket across all workspaces, with each workspace uploading to its own key prefix.

---

# GCP Facet

Specifications for the GCP realisation of the Cloud Platform (`infra/gcp/`). All are active gaps (`[ ]`) until the GCP facet is implemented; genuinely deferred items are marked `[D]`.

**Scope:** the current arrow provisions GCP **infrastructure** (Terraform + bootstrap + CI) plus the games + player-profile runtime slice. The auth/persistence runtime specs for that slice have landed — in-app JWT validation (CP-GCP-010/011), in-app CORS (CP-GCP-012), and the CI image-build + Firebase Hosting deploy (CP-GCP-041, CP-GCP-080). The remaining `[ ]` items are the **out-of-slice parity gaps** tracked in `docs/todo/gcp-aws-parity.md`: the full VITE_* injection set (CP-GCP-042/043) and the CI test user (CP-GCP-032). Firestore I/O for leaderboard + coach-rate-limit (CP-GCP-021) and cross-cloud Bedrock for the coach (CP-GCP-085) have landed; image recognition on Cloud Run (gap E) is still pending. The GCP custom domain is `sudoku-gcp.edoatley.co.uk`.

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

## GCP — Firestore

- [x] **CP-GCP-020**: The system shall provision a Firestore database in Native mode per workspace — the (default) database in the default workspace and a named sudoku{suffix} database otherwise.
- [x] **CP-GCP-021**: The system shall store game, player, leaderboard, and coach-rate-limit data in Firestore collections games, players, leaderboard, and coachRateLimits respectively.
- [x] **CP-GCP-022**: The system shall apply a TTL policy on the coachRateLimits collection keyed on the expiresAt field.
- [x] **CP-GCP-023**: The system shall enable Firestore point-in-time recovery and delete protection on the default-workspace database only.
- [x] **CP-GCP-024**: The system shall locate Firestore in us-central1.

## GCP — Identity Platform

- [x] **CP-GCP-030**: The system shall authenticate end users via Identity Platform with Google as the sole sign-in provider, with no native username/password sign-up offered in the app.
- [x] **CP-GCP-031**: The system shall provision the Identity Platform configuration and Google provider outside Terraform, with Terraform consuming the resulting issuer and audience by configuration.
- [ ] **CP-GCP-032**: The system shall provide a manually-created Identity Platform test user that CI authenticates via the Identity Platform signInWithPassword REST endpoint.

## GCP — Firebase Hosting & Frontend Delivery

- [x] **CP-GCP-040**: The system shall host the frontend on a Firebase Hosting site (sudoku{suffix}) with a single-page-app rewrite of all paths to /index.html.
- [x] **CP-GCP-041**: The system shall deploy the frontend to Firebase Hosting via CI after terraform apply, not on push, so build-time VITE_* values reflect the applied infrastructure.
- [ ] **CP-GCP-042**: The system shall inject VITE_API_URL (backend Cloud Run URL + /api/v1), the Identity Platform equivalents of the VITE_COGNITO_* values, VITE_MOCK_API, VITE_DEV_TOOLS, and VITE_AI_COACH into the frontend build.
- [ ] **CP-GCP-043**: The system shall set VITE_DEV_TOOLS=false in the default workspace and VITE_DEV_TOOLS=true in all other workspaces.

## GCP — DNS & TLS

- [x] **CP-GCP-050**: The system shall provision a Cloud DNS managed zone for sudoku-gcp.edoatley.co.uk once in the default workspace and point the custom domain at Firebase Hosting.
- [x] **CP-GCP-051**: The system shall serve the frontend over Google-managed TLS certificates, with no manual certificate provisioning.

## GCP — Cost Guardrail

- [x] **CP-GCP-060**: The system shall provision a monthly Cloud Billing budget that publishes threshold alerts to a Pub/Sub topic.
- [D] **CP-GCP-061**: The system shall automatically enforce the billing cap (e.g. disabling billing or scaling services to zero) when the budget is exceeded. (Deferred — GCP budgets cannot attach a deny action; requires a Pub/Sub-triggered Cloud Function.)

## GCP — Labels

- [x] **CP-GCP-070**: The system shall apply the labels project=sudoku, managed_by=terraform, and environment (prod in the default workspace, else the sanitized workspace name) to GCP resources, using lowercase label values.

## GCP — CI/CD, Bootstrap & Identity Federation

- [x] **CP-GCP-080**: The system shall authenticate GitHub Actions to GCP via Workload Identity Federation impersonating a deploy service account, with no long-lived service-account keys.
- [x] **CP-GCP-081**: The system shall validate infra/gcp with terraform fmt, init, and validate in CI when files under infra/gcp/ change.
- [x] **CP-GCP-082**: The system's bootstrap process shall create the project and link billing (confirmation required only when the link is missing), create the GCS Terraform state bucket, enable the required GCP APIs, create the sudoku-backend and sudoku-image-recognition Artifact Registry repositories, and create the runtime + deploy service accounts with roles/datastore.user bound to the runtime service accounts.
- [x] **CP-GCP-083**: The system shall provision GCP service accounts, IAM role bindings, Workload Identity Federation, and Identity Platform outside Terraform; infra/gcp Terraform shall reference them by value only.

## GCP — AI Inference

- [x] **CP-GCP-085**: Where AI features (coach, image recognition) are enabled on GCP, the system shall invoke AWS Bedrock cross-cloud using credentials sourced from Secret Manager. (Coach: when `enable_coach = true`, the backend Cloud Run service mounts the manually-created Bedrock access-key secrets as `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` and the run SA is granted `secretmanager.secretAccessor`; the SDK's default credential chain resolves them with no code change. Image recognition on Cloud Run remains gap E.)
- [D] **CP-GCP-090**: The system shall perform AI inference via Vertex AI (Gemini), replacing cross-cloud Bedrock. (Deferred — GCP-native end state; requires backend code change.)

## GCP — Networking

- [D] **CP-GCP-091**: The system shall route Cloud Run egress to Firestore over a private VPC connector. (Deferred — Cloud Run uses Google's managed public API in the interim.)
