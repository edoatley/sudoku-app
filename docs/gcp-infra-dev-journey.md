# GCP infra dev journey

Since #137 (`feat: GCP migration — games + player-profile vertical slice`, merged 2026-07-16) the
app has grown a full second deployment target on Google Cloud Platform, running alongside the
original AWS stack. This is a chronological summary of that work — built from the actual commit
history on `main` (43 PRs from #137 through #196), not just the current end state — grouped by
area with up to 5 bullets each.

Source of truth for current status: `docs/aws-vs-gcp-comparison.md` (architectural),
`docs/aws-vs-gcp-deployment.md` (operational), `docs/runbooks/gcp-manual-setup.md`, and
`docs/arrows/cloud-platform.md` (`CP-GCP-*` specs).

## CI

- `teardown-gcp.yml` landed with #137 itself; `deploy-gcp.yml` followed immediately in #138 as a
  deliberately inert, dispatch-only stub — `workflow_dispatch` workflows are only invokable if the
  file exists on the default branch, so this was pure enabler, dispatched against the
  still-in-review `feat/gcp-migration` branch until #137 merged. Both authenticate via Workload
  Identity Federation (no long-lived keys).
- `ci.yml`'s `terraform-validate` matrix was extended to `infra/gcp` alongside `infra/aws` as part
  of #137, the same PR that first added the directory.
- `deploy-gcp.yml` grew real triggers and payload over time: `rcg-*` branches get ephemeral
  per-branch workspaces (mirroring the existing AWS `rc-*` pattern), and it gained the full
  `VITE_*` env wiring plus an image-recognition image build/push (#163).
- A fail-loud guard on the prod frontend deploy (#170) and an automated post-deploy smoke check
  asserting the env actually serves (#173) were added as the last two of five staged
  "DNS-deployment plan" PRs.
- `scripts/github/gcp-smoke-token.sh` and `scripts/infra/gcp-create-smoke-user.sh` had been used
  ad-hoc during debugging for months but were never actually committed — #167 tracked them so the
  smoke tooling referenced by the runbook and comparison docs was real, not just working-tree
  files.

## Infrastructure

- `infra/gcp/*.tf` built out the GCP equivalents of the AWS stack: Cloud Run (backend +
  image-recognition), Firestore (Native mode) for games/players/leaderboard/coach rate limits, and
  Firebase Hosting for the frontend — introduced in #137, expanded through the lettered
  "gap A–G" parity items tracked in `docs/planning/old/gcp-aws-parity.md`.
- Both clouds converged on one container image (#154, gap A): AWS Lambda migrated from a
  `function.zip` artifact to the same `Dockerfile.jvm-lwa` image Cloud Run runs, retiring the
  `aws-lambda` Maven profile entirely — one build, two deploy targets.
- A custom domain (`sudoku-gcp.edoatley.co.uk`) was wired via Firebase Hosting, DNS-delegated from
  the AWS-hosted Route53 parent zone (#169–172, staged as a 5-PR plan), then simplified in #176 to
  a single CNAME, dropping a short-lived Cloud DNS zone that turned out unnecessary.
- Prod-only bootstrap scripts cover the elevated setup that can't sensibly live in Terraform:
  granting the prod Cloud Run services' public invoker role, delegating DNS, and merging the
  custom domain into Identity Platform's authorized domains (#167) — prod is deliberately kept out
  of Terraform so a routine plan/apply can never toggle its public reachability.
- IAM, service accounts, Workload Identity Federation, and Identity Platform configuration were
  left intentionally manual rather than Terraform-managed — a stated learning surface, documented
  step-by-step in `docs/runbooks/gcp-manual-setup.md`.

## Code

- Firestore repository adapters were added alongside the existing DynamoDB ones for the
  leaderboard (gap B, #160) and the admin data browser (gap C, #161), selected at runtime by a
  `sudoku.persistence` config switch so only one cloud's SDK client is ever instantiated.
- Two critical bugs blocked GCP from working at all until #163 found and fixed them, both
  invisible on AWS because it never exercises this code path: `UserIdentityResolver` compared a
  JSON-typed OIDC claim to a `String` literal with `equals()` (always `false` → every real request
  401'd), and Firestore defaulted to authenticating as the caller's Firebase token instead of the
  Cloud Run runtime service account (`PERMISSION_DENIED` → 500 on every operation).
- The AI coach was ported to GCP behind the existing `CoachAiClient` port in stages: Firestore-
  backed per-user rate limiting plus an interim cross-cloud call to AWS Bedrock (gap D, #162),
  then a native `VertexCoachClient` calling Gemini via ADC (#178–186, `CP-GCP-090`) — later
  migrated off a since-deprecated Vertex SDK and given context caching to close a cost gap with
  Bedrock's own caching (this week's work, #191–196).
- Image recognition was wrapped as an HTTP service (FastAPI/uvicorn) for Cloud Run, adding in-app
  what API Gateway provides for free on AWS — Firebase JWT validation, CORS, a warmup probe (gap
  E, #163) — while leaving the underlying `handler.py` logic untouched.
- A `%gcp` Quarkus config profile handles what's genuinely cloud-specific: Firebase/Identity
  Platform OIDC validation, and an in-app CORS filter (`quarkus.http.cors` is a *build-time*
  property, so a GCP-only toggle couldn't use it — confirmed still true in `application.properties`
  today).

## Other

- `docs/aws-vs-gcp-comparison.md` (architectural side-by-side: auth edge, identity derivation,
  CORS, persistence, Bedrock, hosting, token minting) and `docs/aws-vs-gcp-deployment.md`
  (operational: how the deploy target is actually chosen) were both written as direct deliverables
  of #163 and #174 — the latter documents a real asymmetry: AWS prod is a push-to-`main`, GCP prod
  is a manual workflow dispatch.
- `docs/todo/gcp-aws-parity.md` tracked gaps A–G to closure before GCP was treated as a real second
  target; #174 marked it "parity achieved" and archived it to `docs/planning/old/`, with residual
  items (Vertex cutover, budget hard-cap, private VPC, admin authorization) handed off to direct
  spec-ID tracking instead.
- A billing budget with Pub/Sub alerts (`infra/gcp/budgets.tf`) is the GCP cost guardrail — there's
  no equivalent to AWS's Bedrock deny-action kill switch, so it's alert-only; a hard cap remains a
  tracked, deferred gap.
- The runbook's §9 "Prod bring-up" consolidates the ordered end-to-end sequence the five
  DNS-deployment PRs left scattered: prerequisites → first dispatch (no smoke) → grant prod
  invoker → validate (with smoke) → custom domain → verify.
- Doc archival was kept disciplined throughout rather than left to rot: superseded planning docs
  (`gcp-terraform-infrastructure-plan.md`, `gcp-dns-deployment-plan.md`, the parity doc) moved to
  `docs/planning/old/` as soon as their work landed, with references updated in the same PR.

## What's still open

Per [`docs/todo/vertex-ai-coach-cutover.md`](todo/vertex-ai-coach-cutover.md): the Vertex AI coach
cutover itself (`SC-GCP-007` — flipping `coach_ai_provider`'s default from `bedrock` to `vertex`) is
the closest-to-done item on the backlog. What's left is validating against an actual deployed `rcg-*`/Cloud Run
environment (blocked on GCP Cloud Logging support for the local coach-quality harness, or a
lighter manual smoke check) before the one-line default flip. Deferred, lower-priority gaps: a
budget hard cap, private VPC egress to Firestore, and admin authorization on GCP (Identity
Platform has no group concept).
