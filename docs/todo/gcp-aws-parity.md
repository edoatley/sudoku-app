# AWS ↔ GCP Parity — Tracking

> **✅ STATUS (current): parity achieved as an alternate deployment target.** All feature items
> (A–F) and the deployment-target readiness gaps (G1–G6) have landed — GCP deploys, serves, and is
> reachable at a custom DNS host with an automated post-deploy smoke. See
> `docs/aws-vs-gcp-comparison.md` (architecture), `docs/aws-vs-gcp-deployment.md` (pipelines/control),
> and the runbook *Prod bring-up* section. Only genuinely-**deferred** items remain, each tracked in
> specs: Vertex AI (**CP-GCP-090**), budget hard-cap (**CP-GCP-061**), private VPC egress
> (**CP-GCP-091**), admin authz on GCP (**UM-GCP-008**), the AWS Cognito→Google-`sub` re-key, and
> hosted-UI-per-RC (**G3**, not needed for prod). This doc is retained for the endpoint snapshot +
> history; the residual items live in the specs above.

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
| Leaderboard                              | DynamoDB                      | Firestore                       | **parity**   |
| AI Coach                                 | Bedrock + DynamoDB rate-limit | cross-cloud Bedrock + Firestore rate-limit | **parity** (item D) |
| Admin data browser                       | DynamoDB (behind adapter)     | Firestore (behind adapter)      | **parity** (item C) |
| Image recognition                        | Python Lambda                 | Cloud Run (FastAPI front, deployed) | **parity** (item E) |
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
  (repo created by `scripts/infra/aws/bootstrap.sh`, same pattern as `sudoku-image-recognition`); the
  `function.zip` / S3 zip-bucket path is gone from `ci-deploy.yml`, `deploy-local.sh`, and the
  teardown workflows.
- **Trade-off accepted:** AWS Lambda SnapStart does not support container-image package types, so
  `snap_start` was removed. Cold-start behaviour vs. the zip+SnapStart baseline has **not been
  measured against a live AWS deployment** (no AWS credentials in this environment) — verify p50/
  cold-start latency is acceptable on the next real deploy, and re-open this item if it regresses
  materially (e.g. by widening the Lambda timeout or revisiting SnapStart-eligible options).

### B. Leaderboard on Firestore — **done**
`FirestoreLeaderboardRepository` (replacing `NoOpLeaderboardRepository`) behind the existing
`LeaderboardRepositoryProducer` stores the aggregate in the `leaderboard` collection. `updateOnSolve`
does its read-modify-write in a Firestore transaction; `findAll` reads the collection and ranking
stays in memory, so no composite index is needed. Flips the leaderboard half of **CP-GCP-021**
(coach-rate-limit Firestore I/O — item D — is the remaining half). Specs `LT-GCP-001`–`LT-GCP-008`.

### C. Admin data browser → adapter — **done**
`AdminDataResource` now injects the `AdminDataRepository` port (spec **UM-GCP-010**) instead of
`DynamoDbEnhancedClient` directly. `DynamoDbAdminDataRepository` keeps the AWS scan behaviour;
`FirestoreAdminDataRepository` reads the `games`/`players` collections, selected at runtime by
`sudoku.persistence` via `AdminDataRepositoryProducer` (same pattern as leaderboard, item B).
Data access only — **UM-GCP-008** (admin authz on GCP) remains deferred (Identity Platform has no
group concept).

### D. AI Coach on GCP — **done (coach; option i)**
Chose **(i) cross-cloud Bedrock** (Vertex AI / **CP-GCP-090** stays deferred). Two parts landed:
- **Rate-limit store → Firestore.** `CoachRateLimiter` is now a port with `DynamoDbCoachRateLimiter`
  (AWS) and `FirestoreCoachRateLimiter` (GCP, transactional read-check-write, `expiresAt` as a
  Timestamp so the CP-GCP-022 TTL purges it) behind `CoachRateLimiterProducer`. Completes the coach
  half of **CP-GCP-021**; spec **SC-RL-011**.
- **Cross-cloud Bedrock creds.** `enable_coach` (default `false`) mounts the manually-created AWS key
  from Secret Manager into the backend Cloud Run service as `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`
  (`scripts/infra/gcp/bedrock-cross-cloud.sh` creates the secrets + grants the run SA `secretAccessor`);
  the SDK's default chain resolves them, so no coach
  code changed. Satisfies **CP-GCP-085** for the coach.
- **Correction to earlier wording:** the Bedrock secret was **not** actually scaffolded in Terraform;
  runbook §6 (now updated) documents creating **two plain secrets** manually. Live end-to-end
  verification needs an `rcg-*` deploy with `enable_coach=true` and the secrets present — not yet run.
- Note: the coach *UI* on hosted GCP still depends on `VITE_AI_COACH` (**item F**).

### E. Image recognition on Cloud Run
Flip the `deploy_image_recognition` flag (added this slice), build/push the Python image to the
`sudoku-image-recognition` Artifact Registry repo, and resolve its own HTTP-vs-Lambda question
(LWA on the Python container, or a small FastAPI/Flask front). Wire the frontend endpoint + CORS.

**Done — chose the FastAPI front (AWS Lambda untouched).** `image_recognition/app.py` wraps the
existing `handler.py` as an HTTP service (`Dockerfile.cloudrun`, uvicorn :8080), adding the edge
behaviours API Gateway provides on AWS: in-app Firebase JWT validation on POST, CORS, and an open
warmup probe (specs **IR-GCP-001–005**). Deployed via `deploy_image_recognition`; the image shares
the `sudoku-backend` Artifact Registry repo under image name `image-recognition` (GCP reuses one
repo; AWS uses a dedicated ECR repo). Cross-cloud Bedrock creds reuse the coach's Secret Manager
secrets (`enable_coach`). Frontend calls it via `VITE_IMAGE_RECOGNITION_URL` (falls back to
`VITE_API_URL` on AWS). **Not yet verified against a live Bedrock call on GCP** (needs the rcg-*
deploy + secrets).

### F. Ops / infra parity — **done (except the deferred budget hard-cap)**
- **Done:** full `VITE_*` injection set in `deploy-gcp.yml` — `VITE_MOCK_API`, `VITE_DEV_TOOLS`
  (per-workspace, off on `default`), `VITE_AI_COACH`, and `VITE_IMAGE_RECOGNITION_URL`
  (**CP-GCP-042/043**). The workflow also builds/pushes the Python image and passes
  `deploy_image_recognition` / `enable_coach` (both on for `rcg-*` push).
- **Done:** Identity Platform smoke user (**CP-GCP-032**) — `scripts/infra/gcp/create-smoke-user.sh`
  provisions it, `scripts/github/gcp-smoke-token.sh` mints tokens, and the `smoke` job in
  `deploy-gcp.yml` asserts the deployed backend serves (runbook §5).
- **Done:** custom-domain cutover (`sudoku-gcp.edoatley.co.uk`) — `enable_custom_domain` +
  `scripts/infra/gcp/{delegate-dns,apply-custom-domain-dns}.sh` (runbook §6b + *Prod bring-up*).
- Budget/cost-guard parity (AWS has `budget-deny`; GCP budget alerts exist, hard-cap is the
  deferred **CP-GCP-061**). *(deferred)*

**Gap G is now complete** (see the G section below). RC (`rcg-*`) envs are reachable (invoker in
Terraform), CORS-correct, and optionally deploy a hosted UI; prod is reachable at the custom DNS host
with an automated smoke. A per-RC-workspace Hosting *target* (**G3**) remains the only deferred piece
and isn't needed for prod — RC testing uses a local UI or the shared default site.

### G. Deployment-target readiness (make a deployed GCP env actually usable) — **done**

**All of G1–G6 have landed** (via the DNS-deployment PRs). A deployed GCP env is now usable end to
end: reachable (invoker), CORS-correct, frontend-deployable, sign-in-capable at a custom DNS host,
and smoke-verified. The per-item notes below record how each was closed.

- **G1. Public invoker — done.** Non-default (RC) workspaces get `allUsers` `run.invoker` from
  Terraform (**CP-GCP-014**); prod is granted by `scripts/infra/gcp/grant-prod-invoker.sh` (kept out
  of Terraform by design). The app enforces auth in-app, so this only lets requests *reach* Cloud Run.
  Cloud Run returns `403` until
  `roles/run.invoker` for `allUsers` is granted by hand (runbook §2). For ephemeral `rcg-*` envs
  this is friction on every deploy. Options: grant it in Terraform behind a non-`default`-only
  condition (keep `default`/prod manual), or add a step to `deploy-gcp.yml`. The app enforces auth
  in-app, so `allUsers` invoker only lets requests *reach* the app — it is not "public access".
- **G2. CORS now includes the workspace's own Hosting origin — done.** (Previously) `main.tf` set
  `cors_allowed_origins = "http://localhost:5173"` for every non-`default` workspace, so a UI hosted
  at `https://<project>-<workspace>.web.app` is CORS-blocked when calling its backend. Today an
  `rcg-*` backend is only usable from a **locally-run UI** (`localhost:5173`). To support a hosted
  RC UI, include the workspace's Firebase Hosting URL in `cors_allowed_origins` for non-`default`
  workspaces (it is a known static value: `https://${var.project_id}${local.suffix}.web.app`).
- **G3. Frontend deploy — done for prod; per-RC hosted UI deferred.** `deploy-gcp.yml`'s
  `deploy-frontend` job deploys the SPA to the default Hosting site (`<project>.web.app`) for prod
  (dispatch `deploy_frontend=true`) and, opt-in, for `rcg-*` (`GCP_DEPLOY_FRONTEND=true`). A distinct
  Hosting *target per RC workspace* is still deferred (not needed for prod); RC testing uses a local
  UI or the shared default site.
- **G4. Identity Platform authorized domains — done.** `scripts/infra/gcp/identity-platform-bootstrap.sh`
  merges the custom domain (and `localhost`/`*.web.app`/`*.firebaseapp.com`) into the authorized
  domains. The app uses `signInWithPopup` with the fixed `<project>.firebaseapp.com` handler, so the
  custom domain needs **no** OAuth-client redirect URI (see runbook §4).
- **G5. Branch-name length constraint — handled.** The Firebase `site_id` (`<project>-<workspace>`,
  ≤ 30 chars) is length-capped by `scripts/github/gcp-workspace-name.sh`. Keep `rcg-*` names short.
- **G6. Per-env smoke — done.** The `smoke` job in `deploy-gcp.yml` mints an Identity Platform token
  (CP-GCP-032) and asserts `GET /players/me` 200 + `POST /games` 201, so a run isn't green until the
  env serves (`run_smoke` input / `GCP_RUN_SMOKE` var; runbook §5).

## Related deferred specs (not parity gaps, tracked in specs)
- **CP-GCP-061** — budget hard-cap (needs Pub/Sub-triggered function)
- **CP-GCP-090** — Vertex AI (see item D-ii)
- **CP-GCP-091** — private VPC egress to Firestore
- **GL-GCP-006** — single-active-game Firestore transaction
- **UM-GCP-008** — admin authz on GCP (see item C)
