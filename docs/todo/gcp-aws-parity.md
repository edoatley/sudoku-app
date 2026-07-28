# AWS ↔ GCP Parity — Tracking

Status after the **games + player-profile vertical slice** (Strategy C) merged to `main`
(PR #137). The GCP path is proven end-to-end: pushing an `rcg-*` branch auto-builds the image
and applies Terraform (`deploy-gcp.yml`), and deleting the branch (or a manual dispatch) tears the
workspace down (`teardown-gcp.yml`). This doc tracks the remaining work to bring GCP to full
feature parity with AWS **as an alternate deployment target** and to converge on the single-artifact
goal (identical container image on both clouds, all differences in infrastructure/config).

Two kinds of gap below: **feature parity** (items A–F — endpoints/services AWS has that GCP
doesn't yet) and **deployment-target readiness** (item G — what's needed to actually *use* a
deployed GCP environment, not just stand it up).

## Principle

Every gap below follows the same shape: **one adapter behind the runtime producer, the
difference expressed in infra/config, application code otherwise identical.** No new
build variants; `sudoku.persistence` (and equivalent AI/coach selectors) choose at runtime.

## Endpoint parity snapshot

| Feature                                  | AWS                           | GCP                             | Status       |
| ---------------------------------------- | ----------------------------- | ------------------------------- | ------------ |
| Puzzle generate/validate/hint/candidates | ✓                             | ✓ (stateless compute)           | **parity**   |
| Games                                    | DynamoDB                      | Firestore                       | **parity**   |
| Player profile                           | DynamoDB                      | Firestore                       | **parity**   |
| Auth (JWT validation, CORS, allow-list)  | Cognito/edge                  | Identity Platform/in-app        | **parity**   |
| Leaderboard                              | DynamoDB                      | NoOp stub                       | gap — item B |
| AI Coach                                 | Bedrock + DynamoDB rate-limit | —                               | gap — item D |
| Admin data browser                       | DynamoDB (not behind adapter) | —                               | gap — item C |
| Image recognition                        | Python Lambda                 | Cloud Run defined, not deployed | gap — item E |
| Backend runtime artifact                 | container Lambda (`Dockerfile.jvm-lwa`) | container (HTTP+LWA)  | **parity**   |

## Sequenced work items

### A. AWS zip → container Lambda (single-artifact completion) — **done**
Migrated the AWS backend Lambda from the `function.zip` (java25 runtime + `QuarkusStreamHandler`)
to the **same `Dockerfile.jvm-lwa` image** run as a container Lambda; the Lambda Web Adapter
bridges the Runtime API to the HTTP server. Both clouds now run the identical artifact and the
`aws-lambda` Maven profile has been retired.
- Added `quarkus-smallrye-health`; `AWS_LWA_READINESS_CHECK_PATH=/api/v1/q/health/ready` set in the
  Dockerfile as the LWA readiness gate.
- `infra/aws/lambda.tf`: `package_type = "Image"`, pointed at the `sudoku-backend` ECR image
  (repo created by `scripts/infra/bootstrap.sh`, same pattern as `sudoku-image-recognition`); the
  `function.zip` / S3 zip-bucket path is gone from `ci-deploy.yml`, `deploy-local.sh`, and the
  teardown workflows.
- **Trade-off accepted:** AWS Lambda SnapStart does not support container-image package types, so
  `snap_start` was removed. Cold-start behaviour vs. the zip+SnapStart baseline has **not been
  measured against a live AWS deployment** (no AWS credentials in this environment) — verify p50/
  cold-start latency is acceptable on the next real deploy, and re-open this item if it regresses
  materially (e.g. by widening the Lambda timeout or revisiting SnapStart-eligible options).

### B. Leaderboard on Firestore
Real `FirestoreLeaderboardRepository` (replace `NoOpLeaderboardRepository`) behind the existing
`LeaderboardRepositoryProducer`, plus the write path and any composite index. Flips the leaderboard
half of **CP-GCP-021**. Small, mechanical — good second item.

### C. Admin data browser → adapter
`AdminDataResource` injects `DynamoDbEnhancedClient` directly (the one endpoint that bypasses the
repository abstraction). Refactor it onto a repository/port, then add the Firestore query side.
Prereq for admin parity; also unblocks **UM-GCP-008** (admin authz on GCP).

### D. AI Coach on GCP
Decision point:
- **(i) cross-cloud Bedrock** — keep Bedrock, authenticate from GCP via the `bedrock-aws-credentials`
  Secret Manager secret already scaffolded (runbook §6). Satisfies **CP-GCP-085**.
- **(ii) Vertex AI (Gemini)** — GCP-native `CoachModel` adapter, no cross-cloud hop. This is the
  deferred **CP-GCP-090** end state.
Either way, move the coach rate-limit store from DynamoDB to Firestore (`coachRateLimits`, TTL on
`expiresAt` — the collection + TTL already exist per CP-GCP-022). Completes the coach half of CP-GCP-021.

### E. Image recognition on Cloud Run
Flip the `deploy_image_recognition` flag (added this slice), build/push the Python image to the
`sudoku-image-recognition` Artifact Registry repo, and resolve its own HTTP-vs-Lambda question
(LWA on the Python container, or a small FastAPI/Flask front). Wire the frontend endpoint + CORS.

### F. Ops / infra parity
- Full `VITE_*` injection set in the deploy workflow — `VITE_MOCK_API`, `VITE_DEV_TOOLS`
  (per-workspace), `VITE_AI_COACH` (**CP-GCP-042/043**).
- CI smoke test against the deployed GCP stack using the Identity Platform test user
  (**CP-GCP-032**; smoke-user secrets per runbook §5).
- Budget/cost-guard parity (AWS has `budget-deny`; GCP budget alerts exist, hard-cap is the
  deferred **CP-GCP-061**).
- Custom-domain cutover (`sudoku-gcp.edoatley.co.uk`) once DNS is delegated.

### G. Deployment-target readiness (make a deployed GCP env actually usable)

Standing up an `rcg-*` environment works, but a freshly-deployed env is **not yet usable end to
end**. These are the gaps found while trying to exercise `rcg-smoke` (backend `403`, no UI, CORS
localhost-only). Each is what's required for GCP to be a first-class alternate deployment target,
not just a provisioned one.

- **G1. Public invoker is manual, per workspace.** Cloud Run returns `403` until
  `roles/run.invoker` for `allUsers` is granted by hand (runbook §2). For ephemeral `rcg-*` envs
  this is friction on every deploy. Options: grant it in Terraform behind a non-`default`-only
  condition (keep `default`/prod manual), or add a step to `deploy-gcp.yml`. The app enforces auth
  in-app, so `allUsers` invoker only lets requests *reach* the app — it is not "public access".
- **G2. CORS excludes the workspace's own Hosting origin.** `main.tf` sets
  `cors_allowed_origins = "http://localhost:5173"` for every non-`default` workspace, so a UI hosted
  at `https://<project>-<workspace>.web.app` is CORS-blocked when calling its backend. Today an
  `rcg-*` backend is only usable from a **locally-run UI** (`localhost:5173`). To support a hosted
  RC UI, include the workspace's Firebase Hosting URL in `cors_allowed_origins` for non-`default`
  workspaces (it is a known static value: `https://${var.project_id}${local.suffix}.web.app`).
- **G3. Frontend is not deployed by default on `rcg-*`.** `deploy-gcp.yml` deploys the UI only when
  repo variable `GCP_DEPLOY_FRONTEND == 'true'`, which additionally needs the `VITE_FIREBASE_API_KEY`
  secret and Identity Platform (§4). Until then, `https://<project>-<workspace>.web.app` returns
  "Site Not Found". Decide whether hosted-UI-per-RC is wanted; if so, wire the secret + variable and
  fix G2. (Also: the full `VITE_*` set is item F / CP-GCP-042/043.)
- **G4. Identity Platform authorized domains per RC host.** Google sign-in on a hosted RC UI needs
  each `*.web.app` (and any custom domain) added to the Identity Platform **authorized domains** /
  OAuth redirect list. Identity Platform is project-level and manual (§4), so this is a per-new-host
  step unless scripted.
- **G5. Branch-name length constraint.** The Firebase `site_id` is `<project_id>-<workspace>` and
  must be ≤ 30 chars; `scripts/github/gcp-workspace-name.sh` truncates the workspace to fit, so very
  long `rcg-*` branch names are silently shortened. Keep RC branch names short, or the workspace
  won't match the branch verbatim.
- **G6. Per-env smoke verification.** No automated check that a freshly-deployed env actually serves
  (login → create game → resume). Tie into the Identity Platform test user (CP-GCP-032, runbook §5)
  so `deploy-gcp.yml` can assert the env is healthy before it's considered "up".

## Related deferred specs (not parity gaps, tracked in specs)
- **CP-GCP-061** — budget hard-cap (needs Pub/Sub-triggered function)
- **CP-GCP-090** — Vertex AI (see item D-ii)
- **CP-GCP-091** — private VPC egress to Firestore
- **GL-GCP-006** — single-active-game Firestore transaction
- **UM-GCP-008** — admin authz on GCP (see item C)
