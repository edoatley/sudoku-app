# Vertex AI coach cutover completion

**Summary:** Finish moving the AI coach on GCP from cross-cloud AWS Bedrock to native Vertex AI
(Gemini via ADC). All the plumbing is merged; what remains is a deployed-path validation and a
one-line flip of the `coach_ai_provider` default. Backlog priority 1 — the closest-to-done item.

**Specs:** `CP-GCP-090` (`docs/specs/cloud-platform-specs.md`), `SC-GCP-007`
(`docs/specs/sudoku-coach-specs.md` — Cloud Run must **not** mount AWS Bedrock creds when
`coach.ai.provider=vertex`).

## Current state — what's already merged

- The `CoachAiClient` port and `VertexCoachClient` adapter landed (PRs #181, #183, #184).
- The rollout mechanism landed (PR #185): the `coach_ai_provider` Terraform variable
  (`infra/gcp/variables.tf`, default `"bedrock"`, validated to `bedrock`|`vertex`) lets a workspace
  opt into Vertex via `-var coach_ai_provider=vertex` without touching prod's default. It also
  decoupled the backend's Bedrock secret mount from image-recognition's, and fixed a latent bug where
  `GCP_REGION` was never set on the Cloud Run container (Vertex location would have defaulted to
  `us-central1` regardless of deploy region).
- Vertex AI **context caching** closed the cost gap (`SC-GCP-008/009`, PRs #191/#193/#196/#197): a
  real 55-turn comparison showed a 91% cache-read ratio, matching/exceeding Bedrock's ~89%. See
  `docs/planning/old/vertex-context-caching.md`.
- **Local validation done** (not yet against a live deployment):
  `docker-compose.coach-quality-vertex.yml` with real Vertex AI calls via local ADC — 55 turns on
  `gemini-2.5-flash-lite`, 0% fallback rate, mean latency ~1143ms vs Bedrock's ~2041ms (~2x faster),
  multi-turn escalation-ladder quality confirmed by eye.

## What's left

1. **Validate against a deployed `rcg-*` / Cloud Run environment** — the local comparison validates
   the Vertex AI call itself, not the deployed path. This needs either the GCP Cloud Logging harness
   support ([coach-quality-gcp-cloud-logging.md](coach-quality-gcp-cloud-logging.md), backlog #2) for
   a real report, or a lighter manual smoke check: dispatch *Deploy GCP* with
   `coach_ai_provider=vertex` and exercise the coach, confirming 0% Bedrock fallback and no AWS creds
   mounted.
2. **Flip the default** — once satisfied, change `coach_ai_provider`'s **default** from `"bedrock"`
   to `"vertex"` in `infra/gcp/variables.tf` (the actual prod cutover), which flips `SC-GCP-007` to
   `[x]`.

## Acceptance criteria

- [ ] The Vertex path is validated against a real deployed Cloud Run env (0% fallback, no `AWS_*`
      creds mounted on the backend when `coach_ai_provider=vertex`)
- [ ] `coach_ai_provider` default is `"vertex"`; `SC-GCP-007` marked `[x]`
- [ ] Image recognition still uses cross-cloud Bedrock (unaffected by the coach provider)

## Related specs / docs

- [`docs/specs/cloud-platform-specs.md`](../specs/cloud-platform-specs.md) — `CP-GCP-090`
- [`docs/specs/sudoku-coach-specs.md`](../specs/sudoku-coach-specs.md) — `SC-GCP-007`
- [`docs/todo/coach-quality-gcp-cloud-logging.md`](coach-quality-gcp-cloud-logging.md) — unblocks the
  deployed-path validation
- [`docs/planning/old/vertex-context-caching.md`](../planning/old/vertex-context-caching.md) — the
  closed caching cost gap
