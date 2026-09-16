# Backlog

The single prioritized index of outstanding work. This is a quick-scan table only — full detail
(state, size, acceptance criteria, design notes) lives in the linked `docs/todo/*.md` file or EARS
spec for each row. **This is the source of truth for "what's next"** — a cold-start session should
read this first. Update it whenever priorities shift; mutate rows rather than appending history.

Status: **Active** — open, safe to pick up now. **Deferred** — intentionally shelved; don't re-open
without a reason that changes the original tradeoff. **Done** — recently closed; kept for one pass so
the change is easy to spot, then removed.

| Priority | Description | Reference | Status |
|---|---|---|---|
| 1 | **GCP re-platform to Pulumi** — Phases 0-5 complete and applied; Phase 6 (compute + frontend) next. **Start at the handoff doc.** | [gcp-pulumi-handoff.md](planning/gcp-pulumi-handoff.md) | Active |
| — | ↳ full plan and per-phase detail behind the handoff above | [gcp-pulumi-replatform.md](planning/gcp-pulumi-replatform.md) | Active |
| 2 | Optimise AI coach Bedrock model selection (Haiku 4.5 vs Sonnet) | [optimise-ai-coach-bedrock-model.md](todo/optimise-ai-coach-bedrock-model.md) | Active |
| 3 | Admin log browser (CloudWatch viewer in the admin menu) | [add-log-browser-to-developer-menu.md](todo/add-log-browser-to-developer-menu.md) | Active |
| 4 | Repoint AWS Cognito's Google IdP at its own OAuth client — prerequisite for deleting the old GCP project, since the current client is shared and lives there | [gcp-pulumi-replatform.md](planning/gcp-pulumi-replatform.md) §6 R2 | Active |
| 5 | Terraform CI/testing review — **AWS half only** (tflint, `terraform test`). The `infra/gcp` half is resolved by the Pulumi re-platform, which brings its own lint + unit-test gate. | [terraform-ci-testing-review.md](todo/terraform-ci-testing-review.md) | Active |
| 6 | Integrate hint output into the AI coach chat window (needs full HLD→LLD→EARS pass) | [integrate-hint-output-into-coach-chat.md](todo/integrate-hint-output-into-coach-chat.md) | Active |
| — | GCP IAM drift detection — Pulumi grants additively, so a hand-granted role is invisible to it and never removed | [gcp-iam-drift-detection.md](todo/gcp-iam-drift-detection.md) | Deferred |
| — | GCP budget hard-cap (needs a Pub/Sub-triggered function; alert-only today) | `CP-GCP-061` (cloud-platform-specs.md) | Deferred |
| — | Private VPC egress to Firestore | `CP-GCP-091` (cloud-platform-specs.md) | Deferred |
| — | Single-active-game invariant as a Firestore transaction | `GL-GCP-006` (game-lifecycle-specs.md) | Deferred |
| — | GCP admin authorization (Identity Platform has no group concept) | `UM-GCP-008` (user-management-specs.md) | Deferred |
| — | Review the Vertex image-recognition cost when Gemini promotional pricing ends 2026-12-31 — rates double, flipping Vertex from ~18% cheaper than Bedrock to ~64% more expensive | [gcp-pulumi-phase-5-image-recognition-vertex.md](planning/gcp-pulumi-phase-5-image-recognition-vertex.md) §5 | Deferred |
| — | Expand the image-recognition fixture set — five puzzles, all app screenshots, both providers at 100%: no headroom to regress and no photograph of a physical puzzle despite that being the use case | [expand-image-recognition-fixtures.md](todo/expand-image-recognition-fixtures.md) | Deferred |
| — | Expose the recognition chain of thought — the scratchpad and Gemini's thinking tokens are produced and billed on every request, then discarded, so a mis-read cannot be diagnosed | [expose-recognition-chain-of-thought.md](todo/expose-recognition-chain-of-thought.md) | Deferred |
| — | PIL image preprocessing before Bedrock (blocked on colour-cell desaturation) | `IR-PROC-001..005` (image-recognition-specs.md) | Deferred |
| — | Delete the old GCP project `sudoku-app-eo` — 30-day rollback window after cutover. **Blocked by** row 4 (the shared OAuth client lives in it) | [gcp-pulumi-replatform.md](planning/gcp-pulumi-replatform.md) §4 | Deferred |
| — | Image recognition on Vertex Gemini vision (`CP-GCP-089`, `IR-AI-001..006`) — tracked as Phase 5 of row 1, listed here because it is the last AWS runtime dependency on GCP | [gcp-pulumi-replatform.md](planning/gcp-pulumi-replatform.md) | Active |
