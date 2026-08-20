# Roadmap

Grouped view of every open gap tracked in `docs/todo/documentation-gaps.md` and the `[D]`
deferred items in `docs/specs/*.md`, organized by area instead of by discovery order. This is a
navigation layer — full detail (state, size, acceptance criteria) lives in each linked doc; this
file doesn't duplicate it. Within each group, items are listed in the priority order agreed
2026-08-19; re-order as priorities shift rather than appending a new list.

**As of:** 2026-08-19. Re-derive against `docs/arrows/index.yaml` and `docs/todo/*.md` once
specs have moved on — this snapshot decays the same way `documentation-gaps.md` does.

Status tags: **[active]** — an open gap, safe to pick up now. **[deferred]** — intentionally
shelved; don't re-open without a reason that changes the original tradeoff. **[done]** — closed
recently; kept visible for one pass so the change is easy to spot, then removed.

---

## Infrastructure

1. **[active] GCP Cloud Logging support for the coach-quality harness** —
   [`docs/todo/coach-quality-gcp-cloud-logging.md`](todo/coach-quality-gcp-cloud-logging.md).
   Blocks real (not just local-ADC) validation of any GCP-hosted coach work, not only Vertex.
   Medium-large.
2. **[active] Admin log browser** —
   [`docs/todo/add-log-browser-to-developer-menu.md`](todo/add-log-browser-to-developer-menu.md).
   CloudWatch log viewer in the admin menu, correlated by `pid`. Standalone, no dependencies.
   Medium.
3. **[active] Terraform CI/testing review** —
   [`docs/todo/terraform-ci-testing-review.md`](todo/terraform-ci-testing-review.md). Local
   pre-push suite only validates `infra/aws`, not `infra/gcp`; no `terraform test`/tflint
   anywhere. Process hygiene, no user-facing impact, no urgency.
4. **[deferred] `CP-GCP-061`** — Budget hard-cap on GCP (needs a Pub/Sub-triggered function;
   alert-only today). See `docs/specs/cloud-platform-specs.md`.
5. **[deferred] `CP-GCP-091`** — Private VPC egress to Firestore. See
   `docs/specs/cloud-platform-specs.md`.
6. **[deferred] `GL-GCP-006`** — Single-active-game invariant as a Firestore transaction
   (currently non-atomic, matching AWS). See `docs/specs/game-lifecycle-specs.md`.
7. **[deferred] `UM-GCP-008`** — Admin authorization on GCP (Identity Platform has no group
   concept). See `docs/specs/user-management-specs.md`.

## AI Quality

1. **[active] Vertex AI context caching** —
   [`docs/todo/vertex-context-caching.md`](todo/vertex-context-caching.md). `VertexCoachClient`
   pays full price for ~89% of the tokens Bedrock gets at a steep cache-read discount. Directly
   informs the cutover decision below.
2. **[active] Vertex AI coach cutover completion** — `CP-GCP-090` / `SC-GCP-007`, tracked as
   item 1 in [`docs/todo/documentation-gaps.md`](todo/documentation-gaps.md)'s Active gaps
   section. Closest-to-done item on the whole roadmap: adapter and rollout var are merged, and a
   real local comparison (55 turns, 0% fallback, ~2x faster than Bedrock) is done. Needs the
   caching decision above plus either the Cloud Logging work or a lighter smoke check, then a
   one-line default flip.
3. **[active] Coach-quality invoke/converse A/B** — noted in `docs/arrows/index.yaml`
   (`sudoku-coach` arrow). Closes the loop on the structured-output/caching PR that shipped both
   Bedrock API modes without ever comparing them. Small, reuses existing tooling.
4. **[active] Optimise AI Coach Bedrock model selection** —
   [`docs/todo/optimise-ai-coach-bedrock-model.md`](todo/optimise-ai-coach-bedrock-model.md).
   Haiku 4.5 vs Sonnet 4.6 cost/quality tradeoff. Same tooling family as the A/B item above.
5. **[deferred] `IR-PROC-001..005`** — PIL image preprocessing (downscale, alpha-composite,
   greyscale, JPEG re-encode) before sending to Bedrock for image recognition. Blocked on the
   colour-cell desaturation problem; a multi-model cascade was tried and rejected. See
   `docs/specs/image-recognition-specs.md`.

## UX

1. **[active] Escape key closes coach panel** — `SC-UI-013`, see
   `docs/specs/sudoku-coach-specs.md`. Trivial, isolated, zero risk.
2. **[active] Integrate hint output into AI coach chat window** —
   [`docs/todo/integrate-hint-output-into-coach-chat.md`](todo/integrate-hint-output-into-coach-chat.md).
   Merge the standalone `HintDialog` into the `CoachPanel` chat stream. Biggest single
   investment on this roadmap — needs a full HLD/LLD/EARS pass before any code, per
   Linked-Intent Development (this is a UX surface merge, not a bug fix).

## Tech debt

1. **[done] Archive `docs/planning/bedrock-coach-structured-output-plan.md`** — moved to
   `docs/planning/old/`, 2026-08-19. Every spec it tracked (`SC-BE-011/012/014/025/026/027/029`)
   is `[x]`.
2. **[done] Archive `docs/todo/gcp-aws-parity.md`** — moved to `docs/planning/old/`, 2026-08-19
   (it was a planning-shaped work log, not an open todo; its residual items are tracked directly
   via spec IDs elsewhere on this roadmap). References updated in `docs/llds/cloud-platform.md`
   and `docs/aws-vs-gcp-comparison.md`.

---

## Not on this roadmap

No code-level `TODO`/`FIXME`/`XXX` comments exist anywhere in `backend/src`, `ui/src`, `infra`,
or `image_recognition` — all deferred work is tracked here and in EARS specs, not in code.
