# Capture Full Coach Conversation Content in Logs

**Summary:** Log the actual user prompt, the actual Bedrock response text, and the board state (including per-cell candidates) for every coach turn, so a full conversation can be reconstructed and reviewed after the fact to understand why the coach behaved the way it did.

## Context

**Relevant files:**
- `backend/src/main/java/com/sudoku/coach/bedrock/BedrockCoachClient.java:180` — logs `COACH_REQUEST` (cid, modelId, technique, historyLen, userMsgLen, ts — lengths only, no text)
- `backend/src/main/java/com/sudoku/coach/bedrock/BedrockCoachClient.java:192` and `:198` — logs `COACH_RESPONSE` (revealHint, token counts, latencyMs, fallback — no response text)
- `backend/src/main/java/com/sudoku/coach/SudokuCoachServiceImpl.java` — `coach()` already calls `board.calculateAllCandidates()` at step 1; the computed `Board` (with candidates populated) should be passed into `BedrockCoachClient.call()` rather than recomputed there
- `backend/src/main/java/com/sudoku/domain/Board.java` — `toCandidatesGrid()` serialises the already-computed candidate sets; must be called after `calculateAllCandidates()`
- `backend/src/main/java/com/sudoku/domain/CandidatesGrid.java` — the candidates data structure to log
- `scripts/logs/download-coach-logs.sh` — only surfaces whatever fields are actually logged; needs updated `jq` examples once new fields exist
- `docs/llds/sudoku-coach.md`, `docs/specs/sudoku-coach-specs.md` — existing coach design/specs to extend; new logging specs should use the reserved SC-BE-005..009 range (SC-BE-005 through SC-BE-008 are currently unassigned)
- `docs/arrows/security-standards.md` — "Logging Policy" section defines what coach content may be logged and under what conditions; read this before coding

**Current state:**
`BedrockCoachClient.call()` logs two structured JSON lines per turn (`COACH_REQUEST`, `COACH_RESPONSE`) with a shared `cid` correlating them, but both are metadata-only — no message text, no board. There is no way today to answer "what did the coach actually say, and what did the board look like, when it gave that advice?" from the logs alone.

**Key constraints:**
- Must follow `linked-intent-dev`: add/extend EARS specs in `docs/specs/sudoku-coach-specs.md` and update `docs/llds/sudoku-coach.md` before coding.
- Check `docs/arrows/security-standards.md` (Logging Policy section) for guidance on what coach content may be logged and under what conditions.
- Keep the existing `cid` correlation so request/response/board can be joined per turn.
- Board should be logged as sent (the `Grid`, i.e. placed digits), plus the derived `CandidatesGrid` — don't invent a new board representation.
- **Do not recompute candidates in `BedrockCoachClient`**: `SudokuCoachServiceImpl.coach()` already calls `board.calculateAllCandidates()` before invoking the client. Pass the populated `Board` object into `BedrockCoachClient.call()` so `toCandidatesGrid()` can be called directly without a second solve pass.

## What to do

1. Read `docs/arrows/security-standards.md` (Logging Policy) and `docs/specs/sudoku-coach-specs.md`; write new EARS specs in the reserved SC-BE-005..008 range covering content logging (at minimum: log `userMessage` in COACH_REQUEST, log `aiMessage` in COACH_RESPONSE, log board grid and candidates in COACH_REQUEST).
2. Update `docs/llds/sudoku-coach.md` to describe the extended log shape.
3. Extend `BedrockCoachClient.call()` to accept a populated `Board` parameter (passed from `SudokuCoachServiceImpl`, which has already called `calculateAllCandidates()`). Extend the `COACH_REQUEST` log line to include `userMessage`, the `Grid` (board), and `board.toCandidatesGrid()`.
4. Extend the `COACH_RESPONSE` log line to include the actual `aiMessage` text from `parsed.reply()`.
5. Update the field docs and `jq` examples in `scripts/logs/download-coach-logs.sh`'s header comment to reflect the new fields.
6. Add/update backend tests (e.g. `BedrockCoachClientTest.java`) asserting the new fields are present in the logged JSON.

## Acceptance criteria

- [ ] `COACH_REQUEST` log line includes the full user prompt text, the board grid, and the per-cell candidates grid
- [ ] `COACH_RESPONSE` log line includes the full AI response text
- [ ] Request and response for the same turn are still joinable via `cid`
- [ ] EARS specs and LLD updated to describe the new logged content
- [ ] `docs/arrows/security-standards.md` Logging Policy followed (or explicitly reconciled if it conflicts)
- [ ] `download-coach-logs.sh` header docs/examples updated to match new fields

## Related specs / docs

- [`docs/specs/sudoku-coach-specs.md`](../specs/sudoku-coach-specs.md) — extend with new logging EARS requirements
- [`docs/llds/sudoku-coach.md`](../llds/sudoku-coach.md) — update Orchestration/Bedrock adapter section
- [`docs/arrows/security-standards.md`](../arrows/security-standards.md) — Logging Policy section; read before implementation
- [`download-coach-logs-since-flag.md`](download-coach-logs-since-flag.md) — companion todo; the `--since` flag becomes far more useful once full conversation content is logged
