# Coach-quality A/B: Bedrock invoke vs converse

**Summary:** The structured-output/caching work shipped both Bedrock API modes
(`coach.bedrock.api-mode` = `invoke` | `converse`) without ever comparing them in practice. Run the
existing coach-quality harness in both modes and pick a default based on the data. Backlog priority 3.

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

## Acceptance criteria

- [ ] A comparative run exists for both `invoke` and `converse` over the same scenarios
- [ ] The chosen default is set in config with the comparison recorded as the rationale

## Related specs / docs

- [`docs/arrows/index.yaml`](../arrows/index.yaml) — `sudoku-coach` arrow (`next` field)
- [`docs/todo/optimise-ai-coach-bedrock-model.md`](optimise-ai-coach-bedrock-model.md) — same harness,
  same effort shape (run together)
