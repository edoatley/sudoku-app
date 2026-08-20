# GCP Infra Dev Journey

Since #137 kicked off multi-cloud support, the app has grown a full second deployment
target on Google Cloud Platform, running alongside the original AWS stack. This doc
summarises the key changes made to add GCP support, grouped by area.

Source of truth for current status: `docs/arrows/cloud-platform.md` (CP-GCP specs),
`docs/aws-vs-gcp-comparison.md`, `docs/aws-vs-gcp-deployment.md`, and
`docs/runbooks/gcp-manual-setup.md`.

## CI

- Added a dedicated `deploy-gcp.yml` workflow: builds/pushes images to Artifact Registry
  via Workload Identity Federation, runs `terraform apply` against `infra/gcp`, deploys
  the frontend to Firebase Hosting, then smoke-tests the API. Triggers on `rcg-*` branches
  (ephemeral per-branch workspaces) or manual dispatch for prod.
- Added `teardown-gcp.yml` to destroy and delete a workspace when its `rcg-*` branch is
  deleted, mirroring the existing AWS `rc-*` teardown pattern.
- Extended `ci.yml` so `terraform-validate` runs across both `infra/aws` and `infra/gcp`.
- Kept the main AWS pipeline (`ci-deploy.yml`) untouched — GCP deploys are deliberately
  separate and manual for prod, rather than continuous.
- Known gap: local pre-push tooling (`local-alltests.sh`, Trivy hook) still only checks
  `infra/aws`, so GCP Terraform is only validated in CI, not locally (tracked in
  `docs/todo/terraform-ci-testing-review.md`).

## Infrastructure

- Built out `infra/gcp/*.tf`: Cloud Run services for the backend and image-recognition
  API, Firestore (Native mode) for games/players/leaderboard/coach rate limits, and
  Firebase Hosting for the frontend — the GCP equivalents of Lambda, DynamoDB and Amplify.
- Added a Cloud Billing budget with Pub/Sub alerts as a cost guardrail (alert-only —
  GCP has no equivalent to AWS's Bedrock deny-action kill switch).
- Wired a custom domain (`sudoku-gcp.edoatley.co.uk`) via Firebase Hosting and a single
  Route53 CNAME, simpler than AWS's delegated sub-zone since GCP has no apex-alias.
- Deliberately left IAM, service accounts, WIF and Identity Platform out of Terraform —
  these are provisioned manually via `docs/runbooks/gcp-manual-setup.md` as a stated
  learning surface, not an oversight.
- Per `docs/arrows/cloud-platform.md`, 30/33 GCP specs are done and live on project
  `sudoku-app-eo`, with Vertex AI coach cutover in progress and a budget hard-cap plus
  private VPC deferred.

## Code

- Converged on a single container image (`Dockerfile.jvm-lwa`) for both clouds — the
  Lambda Web Adapter is inert on Cloud Run, which runs the same fast-jar directly.
- Added Firestore-backed repository implementations alongside the existing DynamoDB
  ones, selected at runtime by a `sudoku.persistence` config switch.
- Added a `%gcp` Quarkus profile: Firebase/Identity Platform OIDC validation, an
  in-app CORS filter (since `quarkus.http.cors` is build-time only), and a fix to
  stop Firestore authenticating as the caller's token instead of the runtime service
  account.
- Added a `VertexCoachClient` alongside the existing `BedrockCoachClient` behind the
  same `CoachAiClient` port, selectable via a `coach.ai.provider` config, later gaining
  Gemini context caching to close a cost gap with Bedrock.
- Fixed a `UserIdentityResolver` bug where nested Firebase JSON claims weren't coerced
  to strings, which caused every real GCP request to 401.

## Other

- Wrote comparison and runbook docs: `docs/aws-vs-gcp-comparison.md`,
  `docs/aws-vs-gcp-deployment.md`, and `docs/runbooks/gcp-manual-setup.md`, including
  an ordered "prod bring-up" sequence and a postmortem of the two bugs that initially
  blocked GCP from working end-to-end.
- Tracked feature-parity and deployment-readiness gaps to closure in
  `docs/planning/old/gcp-aws-parity.md` before treating GCP as a real second target.
- Added an interim cross-cloud path so the AI coach can call Bedrock from GCP (an
  AWS IAM key in Secret Manager, mounted on Cloud Run) while the Vertex AI migration
  was still in progress.
- Since there's no edge-level throttle on GCP (no API Gateway equivalent), added
  per-user coach rate-limiting in Firestore as the main abuse/cost control.
- Remaining known gaps: Vertex AI full cutover, a hard budget cap, private VPC,
  admin authorization (no group concept in Identity Platform), and GCP Cloud Logging
  support for the coach-quality test harness.
