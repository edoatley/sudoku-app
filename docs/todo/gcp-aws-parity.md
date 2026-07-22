# AWS ↔ GCP Parity — Tracking

Status after the **games + player-profile vertical slice** (Strategy C) landed on
`feat/gcp-migration`. This doc tracks the remaining work to bring GCP to full feature
parity with AWS and to converge on the single-artifact goal (identical container image
on both clouds, all differences in infrastructure/config).

## Principle

Every gap below follows the same shape: **one adapter behind the runtime producer, the
difference expressed in infra/config, application code otherwise identical.** No new
build variants; `sudoku.persistence` (and equivalent AI/coach selectors) choose at runtime.

## Endpoint parity snapshot

| Feature | AWS | GCP | Status |
|---|---|---|---|
| Puzzle generate/validate/hint/candidates | ✓ | ✓ (stateless compute) | **parity** |
| Games | DynamoDB | Firestore | **parity** |
| Player profile | DynamoDB | Firestore | **parity** |
| Auth (JWT validation, CORS, allow-list) | Cognito/edge | Identity Platform/in-app | **parity** |
| Leaderboard | DynamoDB | NoOp stub | gap — item B |
| AI Coach | Bedrock + DynamoDB rate-limit | — | gap — item D |
| Admin data browser | DynamoDB (not behind adapter) | — | gap — item C |
| Image recognition | Python Lambda | Cloud Run defined, not deployed | gap — item E |
| Backend runtime artifact | zip Lambda (java25) | container (HTTP+LWA) | gap — item A |

## Sequenced work items

### A. AWS zip → container Lambda (single-artifact completion) — **do first**
Migrate the AWS backend Lambda from the `function.zip` (java25 runtime + `QuarkusStreamHandler`)
to the **same `Dockerfile.jvm-lwa` image** run as a container Lambda; the Lambda Web Adapter
bridges the Runtime API to the HTTP server. After this both clouds run the identical artifact and
the `aws-lambda` Maven profile can be retired.
- Add `quarkus-smallrye-health`; set `AWS_LWA_READINESS_CHECK_PATH=/api/v1/q/health/ready` in the
  Dockerfile (noted there as a TODO).
- `infra/aws/lambda.tf`: switch `package_type` to `Image`, point at the ECR image; drop the zip
  build path from `build-lambda-zip` / `ci-deploy.yml`.
- Verify cold-start + p50 latency are acceptable vs the zip.

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

## Related deferred specs (not parity gaps, tracked in specs)
- **CP-GCP-061** — budget hard-cap (needs Pub/Sub-triggered function)
- **CP-GCP-090** — Vertex AI (see item D-ii)
- **CP-GCP-091** — private VPC egress to Firestore
- **GL-GCP-006** — single-active-game Firestore transaction
- **UM-GCP-008** — admin authz on GCP (see item C)
