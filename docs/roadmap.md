# Roadmap

Grouped view of every open gap tracked in `docs/todo/documentation-gaps.md` and the `[D]`
deferred items in `docs/specs/*.md`, organized by area instead of by discovery order. This is a
navigation layer — full detail (state, size, acceptance criteria) lives in each linked doc; this
file doesn't duplicate it. Within each group, items are listed in the priority order agreed
2026-08-19 (updated 2026-08-21 — see Sequence below); re-order as priorities shift rather than
appending a new list.

**As of:** 2026-08-24. Re-derive against `docs/arrows/index.yaml` and `docs/todo/*.md` once
specs have moved on — this snapshot decays the same way `documentation-gaps.md` does.

Status tags: **[active]** — an open gap, safe to pick up now. **[deferred]** — intentionally
shelved; don't re-open without a reason that changes the original tradeoff. **[done]** — closed
recently; kept visible for one pass so the change is easy to spot, then removed.

---

## Sequence

The actual execution order, agreed 2026-08-20, last synced 2026-08-21 — distinct from the
grouped-by-area lists below (those describe *what* each gap is; this describes *the order to do
them in*, and why). **This section is the source of truth for "what's next" — a cold-start
session should read this first, not infer priority from the grouped lists.** Update it whenever
the plan changes.

1. **[done] `SC-UI-013`** — Escape closes coach panel. Merged (#189).
2. **[done] Vertex AI context caching** — `SC-GCP-008/009`. Merged: SDK migration (#191), the
   caching implementation (#193), a real production-blocking CDI bug found and fixed along the
   way (#196 — `com.google.genai.Client` is a `final` class, so the `@ApplicationScoped` producer
   scope silently broke; would have blocked the cutover the moment it flipped), and the doc
   closeout (#197). All three `docs/planning/old/vertex-context-caching.md` acceptance criteria
   met — the real 55-turn comparison re-run showed a 91% cache-read ratio, matching/exceeding
   Bedrock's ~89%. Also: issue #195 (`docs/gcp-infra-dev-journey.md`, PR #198) done alongside this
   — a commit-history-grounded summary of the whole GCP migration, useful background for anyone
   picking up the Vertex cutover (item below) or GCP infra work generally.
3. **[done] Spec-annotation backfill** —
   [`docs/todo/spec-annotation-backfill.md`](todo/spec-annotation-backfill.md). Done 2026-08-24
   (PRs #200/#201/#204/#205/#206, one cluster per PR). All residual loose ends closed the same day:
   the 6 genuinely-uncited GCP CI/bootstrap IDs (`CP-GCP-041/043/050/080/081/082`) annotated in their
   real homes, `CP-GCP-050`'s spec text corrected (Route53 CNAME, not Cloud DNS zone), `CP-INFRA-061`
   and `FE-UI-042b` retired `[D]`, and the separate `HE-BE-035` hint-engine test gap closed with a
   direct test + citation. All `index.yaml` drift notes cleared.
4. **[next] Remaining new features, in order.** Tidy-up/tech-debt is complete; the sequence below is
   the agreed order for the remaining feature work (rationale inline). Re-order as priorities shift.
   1. **Vertex AI coach cutover completion** (`CP-GCP-090`/`SC-GCP-007`) — closest to done; adapter,
      rollout var, and context caching are all merged and locally validated. Finish with a light
      deployed-path smoke check, then flip `coach_ai_provider`'s default to `vertex`.
   2. **GCP Cloud Logging for the coach-quality harness** — unblocks durable (not just local-ADC)
      validation of #1 and all future GCP-hosted coach work.
   3. **Coach-quality invoke/converse A/B** — small; reuses `coach-quality-repeat.sh`.
   4. **Optimise AI coach Bedrock model selection** (Haiku 4.5 vs Sonnet) — same tooling family as #3.
   5. **Admin log browser** (CloudWatch viewer in the admin menu) — standalone, no dependencies.
   6. **Terraform CI/testing review** — extend the local pre-push suite / tflint to `infra/gcp`.
   7. **Integrate hint output into the AI coach chat window** — largest UX item; needs a full
      HLD→LLD→EARS pass before any code.

   Deferred (don't re-open without a reason that changes the tradeoff): `CP-GCP-061` (GCP budget
   hard-cap), `CP-GCP-091` (private VPC egress), `GL-GCP-006` (single-active-game txn), `UM-GCP-008`
   (GCP admin authz), `IR-PROC-001..005` (image preprocessing).

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

1. **[active] Vertex AI coach cutover completion** — `CP-GCP-090` / `SC-GCP-007`, tracked as
   item 1 in [`docs/todo/documentation-gaps.md`](todo/documentation-gaps.md)'s Active gaps
   section. Closest-to-done item on the whole roadmap: adapter, rollout var, and context caching
   are all merged, and a real local comparison (55 turns, 0% fallback, ~2x faster than Bedrock)
   is done. No longer blocked on a caching decision — needs either the Cloud Logging work or a
   lighter smoke check against a deployed environment, then a one-line default flip.
2. **[active] Coach-quality invoke/converse A/B** — noted in `docs/arrows/index.yaml`
   (`sudoku-coach` arrow). Closes the loop on the structured-output/caching PR that shipped both
   Bedrock API modes without ever comparing them. Small, reuses existing tooling.
3. **[active] Optimise AI Coach Bedrock model selection** —
   [`docs/todo/optimise-ai-coach-bedrock-model.md`](todo/optimise-ai-coach-bedrock-model.md).
   Haiku 4.5 vs Sonnet 4.6 cost/quality tradeoff. Same tooling family as the A/B item above.
4. **[deferred] `IR-PROC-001..005`** — PIL image preprocessing (downscale, alpha-composite,
   greyscale, JPEG re-encode) before sending to Bedrock for image recognition. Blocked on the
   colour-cell desaturation problem; a multi-model cascade was tried and rejected. See
   `docs/specs/image-recognition-specs.md`.

## UX

1. **[active] Integrate hint output into AI coach chat window** —
   [`docs/todo/integrate-hint-output-into-coach-chat.md`](todo/integrate-hint-output-into-coach-chat.md).
   Merge the standalone `HintDialog` into the `CoachPanel` chat stream. Biggest single UX
   investment on this roadmap — needs a full HLD/LLD/EARS pass before any code, per
   Linked-Intent Development (this is a UX surface merge, not a bug fix).

## Tech debt

_No open tech-debt/tidy-up items — the tracked backlog here is clear._

1. **[done] Spec-annotation backfill** — done 2026-08-24; all residual loose ends closed (see
   Sequence item 3).

---

## Not on this roadmap

No code-level `TODO`/`FIXME`/`XXX` comments exist anywhere in `backend/src`, `ui/src`, `infra`,
or `image_recognition` — all deferred work is tracked here and in EARS specs, not in code.
