# Coach-quality A/B: Bedrock invoke vs converse

**Summary:** The structured-output/caching work shipped both Bedrock API modes
(`coach.bedrock.api-mode` = `invoke` | `converse`) without ever comparing them in practice. Run the
existing coach-quality harness in both modes and pick a default based on the data. Priority lives in
[`docs/backlog.md`](../backlog.md) (the source of truth); don't hardcode it here.

**Tracking:** noted on the `sudoku-coach` arrow's `next` field in `docs/arrows/index.yaml`; not tied
to a single spec ID.

## Context

- Both API modes are already implemented and selectable at runtime via `coach.bedrock.api-mode`.
- The `coach-quality-repeat.sh` harness (scripted-scenario comparison) already supports running a
  fixed set of turns and aggregating latency / fallback-rate / cache-read metrics — the same tooling
  used for the Vertex vs Bedrock comparison. No new tooling is needed.
- This is the same effort shape as [optimise-ai-coach-bedrock-model.md](optimise-ai-coach-bedrock-model.md)
  (Haiku vs Sonnet) and can be run alongside it.

## What to do

1. Run `coach-quality-repeat.sh` against `coach.bedrock.api-mode=invoke` and `=converse` over the
   same scenario set, capturing latency (mean/p90), fallback rate, cache-read ratio, and cost.
2. Compare and choose the default; record the decision (and set the config default accordingly).

## Outcome (2026-08-26)

Ran `coach-quality-repeat.sh` 10×/mode against Haiku 4.5, same 8 scenarios, real Bedrock. Result
was a **tie** — no data-driven reason to switch, so `invoke` is retained as the default.

| Metric | invoke (incumbent) | converse |
|---|---|---|
| turns (n) | 107 | 110 |
| fallback rate | 0.9% (1× ApiCallTimeout) | 0.9% (1× Throttling 429) |
| latency mean / p50 / p90 (ms) | 2000 / 1917 / 2336 | 1975 / 1865 / 2243 |
| total tokens | 54,468 | 56,135 |

Both single fallbacks are infra transients (a client timeout and a per-minute throttle), not
JSON/parse/quality failures. Converse's ~4% lower p90 is within run-to-run noise **and** confounded:
converse ran immediately after invoke on a warm Bedrock prompt cache (hence `cacheWrite=0` yet
`cacheRead=543k`), so its token/latency edge is partly a free ride on invoke's cache, not intrinsic.

Baseline saved (tracked): `ui/tests/coach-quality/baselines/haiku-4-5-bedrock-results-260826.txt`.
Rationale recorded at `coach.bedrock.api-mode` in `backend/src/main/resources/application.properties`.

## Acceptance criteria

- [x] A comparative run exists for both `invoke` and `converse` over the same scenarios
- [x] The chosen default is set in config with the comparison recorded as the rationale

## Related specs / docs

- [`docs/arrows/index.yaml`](../arrows/index.yaml) — `sudoku-coach` arrow (`next` field)
- [`docs/todo/optimise-ai-coach-bedrock-model.md`](optimise-ai-coach-bedrock-model.md) — same harness,
  same effort shape (run together)
