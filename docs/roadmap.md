# Roadmap

Grouped view of every open gap tracked in `docs/todo/documentation-gaps.md` and the `[D]`
deferred items in `docs/specs/*.md`, organized by area instead of by discovery order. This is a
navigation layer — full detail (state, size, acceptance criteria) lives in each linked doc; this
file doesn't duplicate it. Within each group, items are listed in the priority order agreed
2026-08-19 (updated 2026-08-20 — see Sequence below); re-order as priorities shift rather than
appending a new list.

**As of:** 2026-08-20. Re-derive against `docs/arrows/index.yaml` and `docs/todo/*.md` once
specs have moved on — this snapshot decays the same way `documentation-gaps.md` does.

Status tags: **[active]** — an open gap, safe to pick up now. **[deferred]** — intentionally
shelved; don't re-open without a reason that changes the original tradeoff. **[done]** — closed
recently; kept visible for one pass so the change is easy to spot, then removed.

---

## Sequence

The actual execution order, agreed 2026-08-20 — distinct from the grouped-by-area lists below
(those describe *what* each gap is; this describes *the order to do them in*, and why). **This
section is the source of truth for "what's next" — a cold-start session should read this first,
not infer priority from the grouped lists.** Update it whenever the plan changes.

1. **[done] `SC-UI-013`** — Escape closes coach panel. Merged (#189).
2. **[done, one follow-up open] Vertex AI context caching** — `SC-GCP-008/009`. Merged: SDK
   migration (#191) and the caching implementation itself (#193), both verified against real
   Vertex AI. One acceptance criterion from `docs/todo/vertex-context-caching.md` remains: a
   55-turn Bedrock-vs-Vertex comparison re-run to confirm total cost actually drops toward
   Bedrock's cached-adjusted baseline. Real API spend (55 live calls) — blocked as of this
   writing on the `sandbox` AWS profile's SSO session having expired; re-run once re-authenticated
   (`aws sso login --profile sandbox`).
3. **[next] Spec-annotation backfill** —
   [`docs/todo/spec-annotation-backfill.md`](todo/spec-annotation-backfill.md). The single
   biggest tracked item on this roadmap by volume (~107 uncited-but-implemented specs). Naturally
   sub-divides into the 4 clusters the 2026-08-19 coverage audit already found — image-recognition,
   cloud-platform, react-frontend, sudoku-coach (see each segment's `drift` note in
   `docs/arrows/index.yaml`). Recommended: its own focused session, likely one cluster per PR
   given the size — don't try to do all 4 in one pass.
4. **[unplanned]** Everything else in the grouped lists below. Order beyond item 3 isn't decided
   yet — re-derive once spec-annotation backfill is done or partially done.

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

1. **[done, one follow-up open] Vertex AI context caching** — see Sequence item 2 above.
2. **[active] Vertex AI coach cutover completion** — `CP-GCP-090` / `SC-GCP-007`, tracked as
   item 1 in [`docs/todo/documentation-gaps.md`](todo/documentation-gaps.md)'s Active gaps
   section. Closest-to-done item on the whole roadmap: adapter, rollout var, and context caching
   are all merged, and a real local comparison (55 turns, 0% fallback, ~2x faster than Bedrock)
   is done. No longer blocked on a caching decision — needs either the Cloud Logging work or a
   lighter smoke check against a deployed environment, then a one-line default flip.
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

1. **[done] Escape key closes coach panel** — see Sequence item 1 above.
2. **[active] Integrate hint output into AI coach chat window** —
   [`docs/todo/integrate-hint-output-into-coach-chat.md`](todo/integrate-hint-output-into-coach-chat.md).
   Merge the standalone `HintDialog` into the `CoachPanel` chat stream. Biggest single UX
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
3. **[active] Spec-annotation backfill** — see Sequence item 3 above. Recommended next item.

---

## Not on this roadmap

No code-level `TODO`/`FIXME`/`XXX` comments exist anywhere in `backend/src`, `ui/src`, `infra`,
or `image_recognition` — all deferred work is tracked here and in EARS specs, not in code.
