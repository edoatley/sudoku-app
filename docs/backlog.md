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
| 1 | GCP Cloud Logging support for the coach-quality harness | [coach-quality-gcp-cloud-logging.md](todo/coach-quality-gcp-cloud-logging.md) | Active |
| 2 | Coach-quality invoke/converse A/B | [coach-quality-invoke-converse-ab.md](todo/coach-quality-invoke-converse-ab.md) | Active |
| 3 | Optimise AI coach Bedrock model selection (Haiku 4.5 vs Sonnet) | [optimise-ai-coach-bedrock-model.md](todo/optimise-ai-coach-bedrock-model.md) | Active |
| 4 | Admin log browser (CloudWatch viewer in the admin menu) | [add-log-browser-to-developer-menu.md](todo/add-log-browser-to-developer-menu.md) | Active |
| 5 | Terraform CI/testing review (validate `infra/gcp` in the pre-push suite; tflint) | [terraform-ci-testing-review.md](todo/terraform-ci-testing-review.md) | Active |
| 6 | Integrate hint output into the AI coach chat window (needs full HLD→LLD→EARS pass) | [integrate-hint-output-into-coach-chat.md](todo/integrate-hint-output-into-coach-chat.md) | Active |
| — | GCP budget hard-cap (needs a Pub/Sub-triggered function; alert-only today) | `CP-GCP-061` (cloud-platform-specs.md) | Deferred |
| — | Private VPC egress to Firestore | `CP-GCP-091` (cloud-platform-specs.md) | Deferred |
| — | Single-active-game invariant as a Firestore transaction | `GL-GCP-006` (game-lifecycle-specs.md) | Deferred |
| — | GCP admin authorization (Identity Platform has no group concept) | `UM-GCP-008` (user-management-specs.md) | Deferred |
| — | PIL image preprocessing before Bedrock (blocked on colour-cell desaturation) | `IR-PROC-001..005` (image-recognition-specs.md) | Deferred |
| — | Vertex AI coach cutover — deployed-path validated (0% fallback, no `AWS_*` mount) + `coach_ai_provider` default flipped to `vertex`; `SC-GCP-007` `[x]` | [vertex-ai-coach-cutover.md](todo/vertex-ai-coach-cutover.md) · `CP-GCP-090`/`SC-GCP-007` | Done |
