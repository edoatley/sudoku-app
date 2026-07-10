# Dedicated Observability LLD

**Summary:** Consolidate all structured-logging/observability intent into one LLD that documents every log message type, its fields and their meaning, and how observability works end-to-end — replacing the content now scattered across four other docs.

**Branch context:** `rc-detailed-puzzle-logs` — just added puzzle-play event logging (`NUMBER`/`NUMBER_RESULT`/`NUMBER_CLEAR`/`HINT_*`) + `pid` on `COACH_*` + `download-puzzle-logs.sh`.

## Why deferred

The puzzle-play logging feature landed with its intent **distributed** across existing arrows (the deliberate Phase-1 decision, following the project's per-component logging grain). That works, but the log-field vocabulary now lives in three LLDs + one policy doc with no single home. Promoting it to its own leaf LLD is a structural change worth doing on its own, not mid-feature. This is a doc-only refactor (no code change) and should walk the linked-intent-dev workflow.

## Context

**Relevant files:**
- `docs/llds/game-lifecycle.md` — "Puzzle-Play Event Logging" section (event schema, pid, NUMBER_RESULT validity, robustness). The bulk to extract.
- `docs/llds/sudoku-coach.md` — "Content logging" section (`COACH_REQUEST`/`COACH_RESPONSE` fields, pid vs cid).
- `docs/llds/react-frontend.md` — "Puzzle-Play Event Buffer" subsection (client capture/flush).
- `docs/arrows/security-standards.md` — "Logging Policy" (what may be logged, retention, log group).
- `docs/tests/puzzle-logs.md` + `docs/tests/ai-coach.md` — the field reference tables + download scripts (already user-facing docs).

**Current state:**
Structured logging is documented in four places. Each LLD owns its own log-line schema (coach `SC-BE`, game-lifecycle `GL-BE`), correlated by `pid` (=gameId) with `cid` for within-interaction pairing. All lines go to `/aws/lambda/sudoku{-workspace}` at 30-day retention. There is no central observability LLD; the HLD delegates logging to components.

**Key constraints:**
- Follow `linked-intent-dev`. Decide the arrow shape first: a new leaf LLD (`docs/llds/observability.md`, new spec prefix e.g. `OBS-`) vs. keeping specs in place and only centralizing the *reference*. Moving `GL-BE-040..046` / `SC-BE-*` spec IDs is a rename — EARS IDs are stable, so prefer re-homing the LLD *prose* while leaving spec IDs (and their `@spec` code annotations) where they are, unless you deliberately renumber.
- Do not break `@spec` annotations in code/tests — they cite `GL-BE-04x`, `SC-BE-020`, `FE-BE-02x`.
- Register the new arrow in `docs/arrows/index.yaml` if a new segment is created.

## What to do

1. Choose the shape (surface to user): new `observability` leaf LLD owning the cross-cutting log-message reference, with the producing components *referencing* it — vs. a lighter "reference doc only" approach. Decide whether spec IDs move or stay.
2. Draft `docs/llds/observability.md`: full log-message catalogue (every `type` and field with meaning), the `pid`/`cid` correlation model, transport (coach server-side; puzzle-play via buffered PATCH sync), storage/retention, and the download tooling.
3. Replace the detailed schema prose in game-lifecycle / sudoku-coach / react-frontend LLDs with a one-line pointer to the observability LLD, keeping only each component's *component-specific* wiring.
4. Fold the Logging-Policy table reference so it points at the observability LLD for field meanings (keep the policy/threat-model rationale in security-standards).
5. Update `docs/arrows/index.yaml` and cross-links.

## Acceptance criteria

- [ ] One LLD is the single source of truth for every structured log message's fields and meaning.
- [ ] game-lifecycle / sudoku-coach / react-frontend LLDs no longer duplicate the field schema; they reference the observability LLD.
- [ ] All existing `@spec` annotations in code/tests still resolve to spec IDs that exist.
- [ ] `docs/arrows/index.yaml` reflects any new/changed arrow, counts consistent.
- [ ] No code changes; `download-puzzle-logs.sh` / `download-coach-logs.sh` behaviour unchanged.

## Related specs / docs

- [`docs/specs/game-lifecycle-specs.md`](../specs/game-lifecycle-specs.md) — `GL-BE-040..046`, `GL-API-005`
- [`docs/specs/sudoku-coach-specs.md`](../specs/sudoku-coach-specs.md) — `SC-BE-005..020`
- [`docs/specs/react-frontend-specs.md`](../specs/react-frontend-specs.md) — `FE-BE-020..024`
- [`docs/arrows/security-standards.md`](../arrows/security-standards.md) — Logging Policy
