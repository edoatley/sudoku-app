# AI Coach Quality Suite

A diagnostic runner, not a conventional test suite. It replaces the manual walkthroughs in
`docs/tests/ai-coach.md` (play in a browser, download CloudWatch logs by hand, eyeball a data
browser) with a scripted scenario that drives the real backend + real DynamoDB Local + real
Bedrock, and writes out **everything the system did** — every request/response, every
correlated structured log line, every assertion outcome — for you to dig through. The report
is the primary deliverable, produced whether or not a scenario's assertions all pass.

Opt-in only — not part of `scripts/local/local-alltests.sh`. It calls a real LLM: replies
are non-deterministic prose, it costs a small amount of real tokens, and it needs AWS
credentials with `bedrock:InvokeModel`.

## No browser

Nothing here drives a UI. `POST /puzzles/hint` and `POST /ai/coach` both take the board
directly in the request body — no persisted game state needed — and `PATCH /games/{id}`'s
`events` field is untrusted, client-supplied observability data that `PuzzleEventLogger` logs
verbatim (see the field's own doc comment in
`backend/src/main/java/com/sudoku/game/web/PuzzleEvent.java`). So a script can construct
`NUMBER`/`NUMBER_CLEAR`/`UNDO`/`HINT_REQUEST`/`HINT_RESPONSE` events itself and PATCH them in,
without a real UI ever having existed. `scripts/local/coach-quality-test.sh` never starts the
`ui` container.

## Running

```bash
AWS_PROFILE=sandbox bash scripts/local/coach-quality-test.sh
```

Requires `aws sso login --profile sandbox` (or equivalent) beforehand. To run against a stack
you've already started yourself (faster iteration while writing a scenario):
```bash
cd ui && npm run test:coach-quality
```
Filter to one scenario with Playwright's `--grep`:
```bash
npm run test:coach-quality -- --grep "explicit-answer-request"
```

## The report

Every run writes two files per scenario to `ui/tests/coach-quality/reports/` (gitignored):

- `<scenario>-<timestamp>.json` — the complete trace: every action, full request/response
  payloads, correlated structured log lines, timings, assertion outcomes.
- `<scenario>-<timestamp>.md` — a human-readable transcript: initial grid, a line per action
  (moves, hints with their full technique/nudge, coach turns with fallback status), the final
  persisted board, and an evidence appendix of every structured log line the backend emitted
  for that game (`pid`) — real `NUMBER_RESULT`/`HINT_RESPONSE`/`COACH_REQUEST`/`COACH_RESPONSE`
  lines, not a summary of them.

These are written unconditionally — a failed assertion doesn't cost you the rest of the trace
(see "Assertions never stop the scenario" below).

## Scenario format

A scenario is a starting grid plus an ordered array of actions:

```js
export default {
  name: 'my-scenario',
  grid: [ /* 9x9, 0 = empty */ ],   // must be a valid, uniquely-solvable puzzle —
                                    // POST /games/from-image re-validates this and rejects
                                    // anything else
  actions: [
    { type: 'move', r: 1, c: 6, v: 4 },
    { type: 'sync' },
    { type: 'hint' },
    { type: 'ask', text: "I'm stuck" },
    { type: 'assert', kind: 'coachFallback', expected: false },
  ],
};
```

Then register it in `coach-quality.spec.js`:
```js
import my_scenario from './scenarios/my-scenario.js';
const SCENARIOS = [naked_single_conversation, /* ... */, my_scenario];
```

**Where to get a real grid:** the easiest source of a guaranteed-valid, guaranteed-solvable
puzzle is a board dump from an actual play session — `docs/tests/ai-coach.md`'s manual
walkthroughs capture several (`originalGrid` in the "Puzzle from data browser" blocks), or grab
a fresh one from `GET /puzzles/generate` against a running backend.

### Actions

| Action | Behaviour |
|---|---|
| `{type:'move', r, c, v}` | Places `v` at `(r,c)` in the local working grid. No-ops on given/clue cells. |
| `{type:'clear', r, c}` | Clears `(r,c)`. No-ops on given cells or already-empty ones. |
| `{type:'undo'}` | Reverses the most recent `move`/`clear`. No-ops if there's nothing to undo. |
| `{type:'hint', minRank?, excludedRanks?}` | Calls the real hint engine (`POST /puzzles/hint`) against the current board. `minRank`/`excludedRanks` match the endpoint's own `BoardRequest` fields, for skipping techniques already shown. |
| `{type:'ask', text}` | Sends `text` to the coach (`POST /ai/coach`) with the current board and conversation history, then correlates the matching structured log pair (for `fallback`, latency, token usage — none of which the API response itself exposes). |
| `{type:'sync'}` | Flushes buffered `move`/`clear`/`undo`/`hint` events to `PATCH /games/{id}`, persisting `currentGrid` and writing their structured log lines. **Required** before a `boardValid` assertion, and before those events show up in the report's evidence appendix — moves/hints only exist in the structured logs once synced, matching the real client's buffer-then-flush behaviour. |
| `{type:'assert', kind, ...}` | See below. Never stops the scenario. |

### Assertions

`assert` never aborts a scenario — every subsequent action still runs, so the trace is always
complete. Failures are collected and the test fails at the end with a summary; the report is
written either way.

| `kind` | Fields | Checks |
|---|---|---|
| `boardValid` | `expected: boolean` | Reads the board back via `GET /games/{id}` and checks for row/column/box duplicates. Requires a prior `sync`. |
| `coachFallback` | `expected: boolean` | The most recent `ask`'s correlated `COACH_RESPONSE` log line's `fallback` field. |
| `coachLogContains` | `text: string` | Substring check against the most recent `ask`'s full request+response log pair (JSON-stringified). Avoid using this to check the *content* of a non-deterministic reply (prose varies run to run) — prefer `coachResponseType` for that; it's better suited to structural checks (e.g. a technique name appears in the request). |
| `coachResponseType` | `expected: string` | The most recent `ask`'s correlated `COACH_RESPONSE` log line's `responseType` field — the schema-enforced category (`nudge`\|`focus-hint`\|`reveal-answer`\|`gentle-redirect`\|`off-topic-redirect`\|`celebrate-progress`\|`clarify-technique`) Bedrock's structured output constrains every reply to. Prefer this over `coachLogContains` for asserting on pedagogical intent, since the same intent can be phrased many different ways in prose. |
| `hintTechnique` | `expected: string` | Substring check against the most recent `hint`'s `techniqueName`. |
| `hintFound` | `expected: 'found'\|'solved'\|'no-strategy'` | The most recent `hint`'s status. |
| `hintMatchesCoachTechnique` | *(none)* | Cross-checks two independently-obtained pieces of live state against each other: the hint engine's own technique choice vs. the technique the most recent coach call's context notes were built from, for the same board. No static `expected` — it compares live state to live state. |

If a board can't produce a hint (`NoStrategyApplied` → `hintFound: 'no-strategy'`), don't add an
`ask` after it with no prior real move — that path never calls Bedrock, so there's nothing for
`coachFallback`/`coachLogContains` to correlate against.

## Adding new `assert` kinds

`evaluateAssertion` in `lib/runner.js` is a small `switch` — add a case there. Anything the
trace already has access to (the local grid state, the last hint/coach result, or a fresh
`api.getGame()`/log read) is fair game.
