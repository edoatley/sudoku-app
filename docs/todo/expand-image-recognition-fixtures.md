# Expand the image-recognition fixture set

**Summary:** Five fixtures, all app screenshots, both providers at 100% — the accuracy baseline is saturated and cannot detect a regression or a real-world failure mode.

**Branch context:** `image-recognition-thinking-tokens` — finishing Phase 5 of the GCP Pulumi re-platform, where the accuracy harness became the cutover gate for `IMAGE_AI_PROVIDER=vertex`.

## Why deferred

Surfaced while running the Bedrock-vs-Vertex comparison. Both providers scored 100% / 5-of-5, which is the problem: a baseline nobody can fail is not a baseline. Collecting and ground-truthing new fixtures is manual work, and Phase 5 needed to close.

## Context

**Relevant files:**
- `image_recognition/tests/e2e_config.json` — the five fixtures, each with `expected_grid`, `min_clues` and `expected_empty_cells`
- `image_recognition/tests/fixtures/` — the images themselves
- `image_recognition/tests/test_accuracy.py` — the harness; scores cell accuracy, exact grid, validity, and empty-cell handling
- `image_recognition/tests/accuracy_baseline.json` — committed per-provider floors, both currently 100% / 5-of-5

**Current state:**
All five fixtures are **screenshots from a Sudoku app** — clean, axis-aligned, uniform lighting, printed digits. Not one is a photograph of a physical puzzle, despite that being the feature's actual use case (`POST /ai/image-to-puzzle` exists so a user can photograph a newspaper grid). So the suite measures the easy case only, and both `claude-haiku-4.5` and `gemini-3.8-flash` ace it. The failure modes that would actually bite users — perspective skew, shadows, glare, handwriting, page curl, low resolution — are entirely unmeasured.

**Key constraints:**
- `IR-TEST-002` — the harness compares against a committed per-provider baseline, not an absolute threshold, because these models vary run to run. Keep that; adding fixtures means re-recording both baselines.
- The `accuracy` marker is excluded from CI and the pre-push suite (`IR-TEST-003`) — live credentials, costs money per run. More fixtures means a slower, pricier manual run; keep the set purposeful rather than large.
- `expected_empty_cells` encodes the coloured-cell trap behind `IR-PROC-013`. New fixtures should state their own trap, if any, in `comment`.

## What to do

1. Collect 8–12 new images covering what the current set does not: a **photograph** of a printed newspaper or book puzzle; perspective skew; a shadow or glare across the grid; **handwritten** pencil entries; a low-resolution or motion-blurred capture; a non-square crop; a puzzle with a heavy grid border or coloured regions.
2. Ground-truth each by hand into `e2e_config.json` with `file`, `name`, `expected_grid`, `min_clues`, `expected_empty_cells`, and a `comment` naming the specific difficulty it introduces.
3. Re-run `scripts/local/image-accuracy-compare.sh 3` and record fresh baselines for both providers in `accuracy_baseline.json`. Expect both to drop below 100% — that is the point.
4. If a provider fails badly on a category (e.g. handwriting), record it in the Phase 5 plan's outcome section rather than silently lowering the baseline to match.
5. Consider tagging fixtures by difficulty so the harness can report per-category scores; a single mean hides which failure mode is costing accuracy.

## Acceptance criteria

- [ ] At least one fixture is a photograph of a physical puzzle, not a screenshot
- [ ] Neither provider scores 100% on the expanded set — the baseline has headroom to regress
- [ ] `accuracy_baseline.json` records fresh figures for both providers, with the fixture count
- [ ] Each new fixture's `comment` names the difficulty it introduces
- [ ] `pytest -m accuracy` still passes against the new baselines for both providers

## Related specs / docs

- [`docs/specs/image-recognition-specs.md`](../specs/image-recognition-specs.md) — `IR-TEST-001..003`
- [`docs/planning/gcp-pulumi-phase-5-image-recognition-vertex.md`](../planning/gcp-pulumi-phase-5-image-recognition-vertex.md) §5 — "Still worth doing"
- [`docs/llds/image-recognition.md`](../llds/image-recognition.md) — scoring and cross-check design
