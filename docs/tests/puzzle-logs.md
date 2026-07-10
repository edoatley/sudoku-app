# Puzzle-Play Observability Logs

Structured logs that capture what a player does around the AI coach, so coaching quality
can be reviewed against the actual play session. Companion to `docs/tests/ai-coach.md`
(which covers the `COACH_*` lines). Design: `docs/llds/game-lifecycle.md`
(Puzzle-Play Event Logging) and `docs/llds/react-frontend.md` (Puzzle-Play Event Buffer).

## Correlation

Every structured line for a play session carries **`pid` = the gameId**. This is the single
key that joins the coach conversation (`COACH_REQUEST` / `COACH_RESPONSE`) with the player's
actions (`NUMBER`, `HINT_*`, …) for the same game. `cid` still pairs one interaction
(a coach turn, or a hint request↔response); `pid` groups the whole session.

## Event types

All lines share `type`, `pid`, `ts` (server epoch-ms). Action lines also carry `userId`
(from the authenticated PATCH principal) and `clientTs` (when the client observed the action).

| Type | Origin | Extra fields |
|---|---|---|
| `NUMBER` | player placed a digit | `r`, `c`, `v` |
| `NUMBER_RESULT` | server-derived | `r`, `c`, `v`, `correct` (matches the solution) |
| `NUMBER_CLEAR` | player cleared a cell | `r`, `c` |
| `HINT_REQUEST` | player asked for a hint | `cid`, `minRank`, `excludedRanks` |
| `HINT_RESPONSE` | hint resolved | `cid`, `techniqueName`, `strategyRank`, `difficulty`, `found` |
| `EVENTS_TRUNCATED` | client buffer overflowed before flush | — |
| `COACH_REQUEST` / `COACH_RESPONSE` | coach turn | `cid`, … (see `ai-coach.md`) |

Notes:
- `NUMBER_RESULT.correct` is computed server-side against the stored `solutionGrid`; it is
  never shown to the player.
- `HINT_RESPONSE` is recorded on every resolution — `found:false` when no strategy applies.
  A hint that fails with a transport error leaves a `HINT_REQUEST` whose `cid` never pairs.
- Demo/practice games have no persisted `gameId`, so they never sync and are not logged;
  the coach logs `pid:null` for them.
- Logging is at-least-once: a retry after an ambiguous save may duplicate lines.

## Downloading

`scripts/logs/download-puzzle-logs.sh` pulls the events from the same CloudWatch log group
as the coach logs (`/aws/lambda/sudoku{-workspace}`).

```bash
# Readable per-puzzle digest for one game, last hour:
AWS_PROFILE=sandbox bash scripts/logs/download-puzzle-logs.sh --puzzle-id <gameId> --summary --hours 1

# Raw NDJSON for one user, piped to jq (e.g. every incorrect move):
AWS_PROFILE=sandbox bash scripts/logs/download-puzzle-logs.sh --user-id <sub> | \
  jq 'select(.type=="NUMBER_RESULT" and .correct==false)'

# The puzzle ran on a different branch than the one you have checked out:
AWS_PROFILE=sandbox bash scripts/logs/download-puzzle-logs.sh --puzzle-id <gameId> --branch rc-foo

# You don't remember (or the branch has since been deleted) which environment it ran on:
AWS_PROFILE=sandbox bash scripts/logs/download-puzzle-logs.sh --puzzle-id <gameId> --branch all
```

Flags: `--puzzle-id`, `--user-id`, `--hours` (default 24), `--summary`, `--workspace`,
`--branch <name>|all`, `--output`, `--profile`. Default output is NDJSON (one event per
line); `--summary` prints a digest per puzzle. `--branch` resolves the workspace from an
arbitrary branch name (same rule as auto-detection from the current git branch) rather
than requiring the workspace name directly; `--branch all` scans every
`/aws/lambda/sudoku*` log group and merges the results — slower (one CloudWatch query per
environment) but useful when you don't know which environment a puzzle came from, or the
branch has since been torn down and `git rev-parse` can't help.

### Example summary

```
Puzzle 3f9c...-a12b   user=e1b2...
  span:    2026-07-10T10:17:02Z  ->  2026-07-10T10:19:44Z
  numbers: 12 placed  (10 correct, 2 incorrect)
  clears:  3
  hints:   2  [Naked Single x1, X-Wing x1]
  coach:   1 turn(s)
```

## Verification

1. Deploy to a feature workspace and open a saved game.
2. Place a correct digit, place a wrong digit, clear a cell, request a hint, send a coach message.
3. Wait for a sync (≤60s, or switch tabs to force a flush), then run
   `download-puzzle-logs.sh --puzzle-id <gameId> --summary --hours 1`.
4. Confirm the counts match your actions and that `NUMBER_RESULT` correctness is right
   (the wrong digit shows as incorrect), and that the `COACH_*` lines share the same `pid`.
