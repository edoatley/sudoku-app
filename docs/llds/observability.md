# Observability

**Created**: 2026-07-10
**Status**: Complete

## Context and Current State

Structured logging intent for this app previously lived scattered across the LLDs of the
components that produce log lines — each component owning its own schema prose, per the
project's per-component logging grain (a deliberate Phase-1 decision). That worked, but left
the full log-message vocabulary with no single home: understanding "what fields does a
`COACH_RESPONSE` line carry" meant reading the Sudoku Coach LLD, while "what does `NUMBER`
carry" meant reading the Game Lifecycle LLD, and the client-side buffering mechanics lived in
the React Frontend LLD. This document is that single home: the full log-message catalogue,
the `pid`/`cid` correlation model, transport, storage/retention, and the download tooling.

**This is a documentation consolidation, not a new component.** No code changes accompanied
its creation. The producing components — `PuzzleEventLogger` (backend, game-lifecycle),
`BedrockCoachClient` (backend, sudoku-coach), and `useEventLog`/`useGameSync` (frontend,
react-frontend) — are unchanged and still own their own EARS spec IDs (`GL-BE-04x`,
`SC-BE-*`/`SC-RL-*`, `FE-BE-02x`) in their respective spec files. This LLD does not introduce
new spec IDs; it centralizes the *prose* describing what those specs already require.

Files: this LLD has no files of its own. See `PuzzleEventLogger.java`, `BedrockCoachClient.java`
(backend); `useEventLog.js`, `useGameSync.js` (frontend) — documented in game-lifecycle.md,
sudoku-coach.md, and react-frontend.md respectively for component-specific wiring.

## Correlation Model

Every structured log line shares a common shape: `type`, and (for lines tied to a specific
play session) `pid` — the game's `gameId`. Two correlation keys, at two different scopes:

- **`pid` (whole-session key).** Carried by every puzzle-play event (`NUMBER`,
  `NUMBER_CLEAR`, `HINT_REQUEST`/`HINT_RESPONSE`, `EVENTS_TRUNCATED`) and every coach line
  (`COACH_REQUEST`/`COACH_RESPONSE`). It is the single key that joins "what the player did"
  with "what the coach said" for the same game — filter any CloudWatch query on `pid` to
  reconstruct a whole play session across both log families. Demo/practice games have no
  persisted `gameId`, so they never sync and `pid` is logged as `null` for coach calls made
  outside a saved game.
- **`cid` (within-interaction key).** A UUID generated per interaction — one coach turn
  (`BedrockCoachClient.call()`), or one hint request↔response pair. It pairs exactly two
  lines (a request and its response); it says nothing about the rest of the session.

## Event Catalogue

All lines share `type`, `pid` (see above), and a server-generated `ts` (epoch-millis). Client-
originated action lines also carry `userId` (from the authenticated PATCH principal — trusted,
not client-asserted) and `clientTs` (when the client observed the action, preserving intra-batch
ordering).

### Puzzle-play events (game-lifecycle → `PuzzleEventLogger`)

| Type | Origin | Fields beyond common |
| --- | --- | --- |
| `NUMBER` | client action | `r`, `c`, `v` |
| `NUMBER_RESULT` | **server-derived** | `r`, `c`, `v`, `correct` (matches `solutionGrid`) |
| `NUMBER_CLEAR` | client action | `r`, `c` |
| `HINT_REQUEST` | client action | `cid`, `minRank?`, `excludedRanks?` |
| `HINT_RESPONSE` | client action | `cid`, `techniqueName`, `strategyRank`, `difficulty`, `found` |
| `EVENTS_TRUNCATED` | marker | — (client buffer overflowed before flush) |

For each buffered `NUMBER`, the server emits **two** lines: the raw `NUMBER` action, then a
`NUMBER_RESULT` whose `correct = (solutionGrid[r][c] == v)`, computed server-side against the
authoritative solution and never shown to the player. `HINT_RESPONSE` is recorded on every
resolution — `found: false` (technique/rank null) when the engine has no applicable strategy;
a hint that fails with a transport error leaves a `HINT_REQUEST` whose `cid` never pairs, which
is itself the signal that it failed. Candidate-mode toggles are not buffered at all (they are
not placements) — see FE-BE-020.

Full spec: `docs/specs/game-lifecycle-specs.md` — `GL-BE-040..046`, `GL-API-005`. Client
buffering mechanics: `docs/specs/react-frontend-specs.md` — `FE-BE-020..024`.

### Coach events (sudoku-coach → `BedrockCoachClient`)

| Type | Fields |
| --- | --- |
| `COACH_REQUEST` | `cid`, `modelId`, `technique`, `historyLen`, `userMsgLen`, `userMessage`, `board`, `candidatesGrid` |
| `COACH_RESPONSE` | `cid`, `revealHint`, `inputTokens`, `outputTokens`, `cacheReadTokens`, `cacheWriteTokens`, `latencyMs`, `fallback`, `aiMessage`, `errorType?`, `errorMsg?` |

`board` is the placed-digit grid (row-major); `candidatesGrid` is the per-cell candidates grid.
Both are computed once in `SudokuCoachServiceImpl.coach()` and passed through, not recomputed
in `BedrockCoachClient`. `aiMessage` is logged on **every** path, including `fallback: true` —
it is the deterministic hint nudge there rather than an actual Bedrock response, but it is
still what the player saw. `fallback` has two distinct triggers, both of which must set
`fallback: true`: the outer SDK/timeout catch in `call()`, and `parseResponse()` falling back
*internally* (the Bedrock call succeeded, but the response text was blank or failed to parse
as the expected JSON schema) — the second case never throws, so `call()` derives the flag from
`parseResponse()`'s result rather than hardcoding it. `errorType`/`errorMsg` are present on
either fallback trigger, null on a genuine successful reply.

Full spec: `docs/specs/sudoku-coach-specs.md` — `SC-BE-005..022`.

## Transport

**Puzzle-play events — the existing PATCH sync, not a new endpoint.** Individual placements
and clears live in client state and are never sent to the server per-move. The client buffers
them (`useEventLog`) and flushes the buffer as the `events` array on the existing
`PATCH /api/v1/games/{gameId}` autosave. `GameServiceImpl.update` — which already loads the
`GameItem` (and therefore has the authoritative `solutionGrid`, `gameId`, and JWT `userId` in
hand) — walks `events` and emits one log line per event via `PuzzleEventLogger`. This adds no
per-move network cost and no new attack surface. The buffer is capped at 500 entries
(drop-oldest, `EVENTS_TRUNCATED` marker on overflow) and cleared only after the PATCH succeeds
— on failure it is retained and re-sent on the next sync.

**Coach events — synchronous, server-side only.** Each `BedrockCoachClient.call()` logs its
`COACH_REQUEST`/`COACH_RESPONSE` pair directly from the backend; there is no client buffering
involved, since the coach endpoint is a single synchronous request/response per turn.

## Robustness

`r`, `c`, and `v` on puzzle-play events are client-supplied and untrusted. An event with an
unknown `type`, missing required fields, or out-of-range coordinates/digit is logged at WARN
and skipped — in particular, the `NUMBER_RESULT` solution lookup is bounds-guarded so a bad
coordinate degrades to a dropped event rather than throwing and failing the PATCH. Event
logging must never break progress persistence. The server processes at most 500 events per
request regardless of what the client sends.

All lines (puzzle-play and coach) are built with the injected Jackson `ObjectMapper`
(`writeValueAsString` on an `ObjectNode`), never string templating — `userMessage` and
`aiMessage` are freeform, user-/LLM-authored text, and naive `%s` substitution breaks on
embedded quotes or newlines.

## Storage & Policy

All structured lines go to the same CloudWatch log group as every other Lambda log
(`/aws/lambda/sudoku{suffix}`) at the existing retention (30 days) — no separate store, no
per-log-type retention override. What may be logged, and the threat-model rationale for
logging full coach conversation content and gameplay actions (both non-sensitive under this
app's threat model — a small, known allowlisted user set), is policy, not schema — see
`docs/arrows/security-standards.md` — Logging Policy. This document is the schema reference;
that document is the policy/threat-model rationale.

## Download Tooling

| Script | Covers | Usage |
| --- | --- | --- |
| `scripts/logs/download-coach-logs.sh` | `COACH_REQUEST`/`COACH_RESPONSE` | `docs/tests/ai-coach.md` |
| `scripts/logs/download-puzzle-logs.sh` | `NUMBER`/`NUMBER_RESULT`/`NUMBER_CLEAR`/`HINT_*`/`EVENTS_TRUNCATED`, plus `COACH_*` for the same `pid` | `docs/tests/puzzle-logs.md` |

Both scripts derive the CloudWatch log group from the current git branch (mirroring the
Terraform workspace naming rule) and support `--summary` for a human-readable digest instead
of raw NDJSON. See the field-reference tables and worked examples in the linked test docs.
