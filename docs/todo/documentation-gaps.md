# Documentation gap inventory

**Purpose:** single entry point consolidating every open gap/TODO tracked across
`docs/arrows/index.yaml`, `docs/specs/*.md` (`[ ]`/`[D]` markers), and the standalone
`docs/todo/*.md` docs, so future sessions can pick a gap and drive it through the full
HLD → LLD → EARS → Tests → Code workflow without re-deriving this survey. This doc is the
triage layer — it does not itself contain new design work.

**As of:** 2026-08-19. Re-derive rather than trust this snapshot once specs/arrows have moved on.
See [`docs/roadmap.md`](../roadmap.md) for the same gaps grouped by area (infrastructure, AI
quality, UX, tech debt) instead of by discovery order.

---

## Active gaps (`[ ]` in specs, arrow status `IN_PROGRESS`)

### 1. Vertex AI coach cutover — CP-GCP-090 + SC-GCP-007
- **State:** The `CoachAiClient` port and `VertexCoachClient` adapter landed (PRs #181, #183,
  #184). PR #185 (this branch) added the rollout mechanism: `coach_ai_provider` Terraform
  variable (`infra/gcp/variables.tf`, default `"bedrock"`) lets a workspace opt into Vertex via
  `-var coach_ai_provider=vertex` without touching prod's default, decoupled the backend's AWS
  Bedrock secret mount from image-recognition's (they previously shared one `enable_coach` gate,
  which made it impossible to drop the coach's Bedrock creds without also breaking image-rec),
  and fixed a latent bug where `GCP_REGION` was never set on the Cloud Run container so
  `VertexCoachClient`'s Vertex AI location would have silently defaulted to `us-central1`
  regardless of the actual deploy region. `docs/llds/cloud-platform.md`'s Design Decisions table
  updated accordingly.
- **Validation done (local, not yet against a live deployment):** an `rcg-*`-based validation
  attempt hit a real blocker — the coach-quality harness's log correlation only reads local
  `docker compose logs`, with no GCP Cloud Logging equivalent
  (`docs/todo/coach-quality-gcp-cloud-logging.md`). Redirected to a local comparison instead
  (`docker-compose.coach-quality-vertex.yml`, real Vertex AI calls via local ADC): 55 turns on
  `gemini-2.5-flash-lite`, 0% fallback rate, mean latency 1143ms vs Bedrock's 2041ms (~2x
  faster), multi-turn escalation-ladder quality confirmed by eye (correct reasoning, proper
  nudge→focus→reveal escalation). One real cost gap found:
  `docs/todo/vertex-context-caching.md` — Vertex pays full price for ~89% of the tokens Bedrock
  gets at a steep cache-read discount, since `VertexCoachClient` doesn't use Vertex AI context
  caching yet. Also fixed two real bugs surfaced during this: `COACH_VERTEX_MODEL_ID` wasn't
  passed through the compose overlay (silently used a stale, now-`NOT_FOUND` default model id),
  and `aggregate.js` assumed Bedrock's log schema unconditionally (Vertex's simpler `tokens`
  total + missing `latencyMs` aggregated to `NaN`/`0` despite real successful responses).
- **What's left (still `[ ]`):** (1) decide whether the context-caching gap blocks cutover or is
  an acceptable interim cost tradeoff given the latency win; (2) validate against an actual
  deployed `rcg-*`/Cloud Run environment (the local comparison validates the Vertex AI call
  itself, not the deployed path) — needs `docs/todo/coach-quality-gcp-cloud-logging.md` first for
  a real report, or a lighter manual smoke check; (3) once satisfied, flip `coach_ai_provider`'s
  **default** to `"vertex"` in a small follow-up (the actual prod cutover), which flips
  `SC-GCP-007` to `[x]`.
- **Specs:** `docs/specs/cloud-platform-specs.md` (CP-GCP-090), `docs/specs/sudoku-coach-specs.md`
  (SC-GCP-007 — Cloud Run must **not** mount AWS Bedrock creds when `coach.ai.provider=vertex`).
- **Size:** small now — the rollout plumbing is done; what remains is a live harness run plus a
  one-line default flip.
- **Recommendation:** pursue next. Closest-to-done item in the inventory.

### 2. Escape key closes coach panel — SC-UI-013
- **State:** Isolated, small UI gap in the coach chat panel.
- **Spec:** `docs/specs/sudoku-coach-specs.md` (SC-UI-013).
- **Size:** small.
- **Recommendation:** pursue opportunistically — cheap to fold into any other coach-panel UI
  work, or take standalone as a quick win.

### 3. Coach-quality harness A/B (invoke vs converse)
- **State:** Noted in `docs/arrows/index.yaml` (`sudoku-coach` arrow `next` field) as
  outstanding, but not tracked by an individual spec ID. The structured-output/caching work
  (see hygiene note below) shipped both `invoke` and `converse` API modes
  (`coach.bedrock.api-mode`); no comparative run has validated which mode performs better in
  practice.
- **Size:** small — the `coach-quality-repeat.sh` harness already supports this comparison
  (see item 4 under docs/todo items below, which describes the same harness for a different
  comparison).
- **Recommendation:** pursue alongside item 4 below (same tooling, same effort shape).

---

## Existing `docs/todo/` items

These are already self-contained and scoped — read the linked file directly rather than
duplicating it here.

1. **[Add a log browser to the developer/admin menu](add-log-browser-to-developer-menu.md)** —
   admin-gated CloudWatch log viewer correlated by `pid`/gameId, replacing manual `aws logs
   filter-log-events` calls. Needs new IAM grant + `/admin/logs` endpoint + UI dialog. Not
   started. Size: medium.

2. **[Integrate hint output into AI coach chat window](integrate-hint-output-into-coach-chat.md)**
   — merge the standalone `HintDialog` popup into the `CoachPanel` chat stream so hints and
   coach conversation share one transcript. Explicitly flagged in the doc as needing a full
   HLD/LLD/EARS pass before any code changes (UX surface merge, not a bug fix). Not started.
   Size: large.

3. **[Optimise AI coach Bedrock model selection](optimise-ai-coach-bedrock-model.md)** — evaluate
   Claude Haiku 4.5 vs Sonnet 4.6 cost/quality tradeoff for the coach using the
   `coach-quality-repeat.sh` harness (scripted-scenario comparison) and/or production log
   analysis. Acceptance criteria (cache hit rate, p90 latency, cost estimate) not yet measured.
   Size: medium.

4. **[GCP Cloud Logging support for the coach-quality harness](coach-quality-gcp-cloud-logging.md)**
   — let the diagnostic runner read structured logs from Cloud Logging, not just local
   `docker compose logs`, so it can validate a real deployed environment. Filed while validating
   the Vertex AI cutover (item 1 above). Size: medium-large.

5. **[Vertex AI context caching](vertex-context-caching.md)** — `VertexCoachClient` pays full
   price for ~89% of the tokens Bedrock gets at a steep cache-read discount, since it doesn't
   use Vertex AI's context-caching feature yet. Directly informs the Vertex cutover cost
   tradeoff (item 1 above). Size: medium.

6. **[Terraform CI/testing review](terraform-ci-testing-review.md)** — local pre-push suite
   (`scripts/local/local-alltests.sh`) and the Trivy pre-commit hook both only validate
   `infra/aws`, not `infra/gcp`, even though CI validates both; no `terraform test`/tflint
   anywhere. Size: small-medium (the immediate AWS/GCP asymmetry fix), open-ended if
   `terraform test` adoption is pursued.

---

## Deferred (`[D]` in specs — intentional, not gaps to close opportunistically)

| Spec ID | What | Why deferred |
|---|---|---|
| GL-GCP-006 | Single-active-game invariant as a Firestore transaction | Currently orchestrated non-atomically by `GameServiceImpl`, matching AWS; a transaction needs an interface/service change, out of scope for the contained games slice that shipped it |
| UM-GCP-008 | Admin authorization on GCP (custom claim / allowlist) | `/admin/*` wasn't part of the games + player-profile slice; Identity Platform has no group concept to mirror Cognito's `administrators` group |
| IR-PROC-001..005 | PIL image preprocessing (downscale, alpha-composite, greyscale, JPEG re-encode, fallback) before sending to Bedrock | Blocked on solving the colour-cell desaturation problem first; a multi-model cascade approach was tried and rejected (2026-04-20) |
| CP-GCP-061 | Budget hard-cap on GCP | Needs a Pub/Sub-triggered function; AWS has `budget-deny`, GCP currently has budget alerts only (soft) |
| CP-GCP-091 | Private VPC egress to Firestore | Deferred alongside the other residual GCP hardening items |

These are intentional design decisions, not overlooked work — don't re-open without a reason
that changes the tradeoff that led to deferring them.

---

## Doc hygiene — done

Both items below were completed 2026-08-19:

- **`docs/planning/bedrock-coach-structured-output-plan.md` → `docs/planning/old/`.** Every spec
  it tracked (SC-BE-011/012/014/025/026/027/029) is `[x]` implemented.
- **`docs/todo/gcp-aws-parity.md` → `docs/planning/old/gcp-aws-parity.md`.** It tracked sequenced
  work items and decisions (a planning artifact, not an open todo) whose residual items are now
  tracked directly via spec IDs elsewhere in this inventory; `docs/aws-vs-gcp-comparison.md` and
  `docs/aws-vs-gcp-deployment.md` are the maintained architecture/ops reference docs it was
  superseded by. References in `docs/llds/cloud-platform.md` and `docs/aws-vs-gcp-comparison.md`
  updated to the new path.

---

## Not found

A full repo grep (`backend/src`, `ui/src`, `infra`, `image_recognition`, excluding
`node_modules`/`target`/`dist`/`build`) for code-level `TODO`/`FIXME`/`XXX` comments returned
nothing outside `docs/`. All deferred work in this codebase is tracked in EARS specs and
`docs/todo/`, not code comments — no stray in-code TODOs to reconcile.
