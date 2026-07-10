# Game Lifecycle

**Created**: 2026-04-18
**Status**: Complete

## Context and Current State

The Game Lifecycle component owns all stateful game operations: creating games, persisting player progress, enforcing the single-active-game invariant, and managing the state machine from `IN_PROGRESS` through to `SOLVED` or `ABANDONED`. It is the only component that reads and writes game records in DynamoDB.

Files: `game/web/GameResource.java`, `game/GameService.java`, `game/GameServiceImpl.java`, `game/GameRepository.java`, `game/persistence/DynamoDbGameRepository.java`, `game/persistence/GameItem.java`, `game/GameStatus.java`, `game/InvalidPuzzleException.java`, `game/DuplicateDigitsException.java`, `game/PuzzleHasNoSolutionException.java`, `game/PuzzleHasMultipleSolutionsException.java`, `game/GameNotFoundException.java`, `web/exception/GameNotFoundExceptionMapper.java`, `game/web/GameState.java`, `game/web/GameUpdateRequest.java`, `game/web/CreateGameFromGridRequest.java`, `game/web/GameHistoryEntry.java`, `game/web/GameHistoryResponse.java`.

## State Machine

Games move through a simple lifecycle:

```text
                 ┌──────────────┐
                 │  (created)   │
                 └──────┬───────┘
                        │ createGame / createGameFromExistingGrid
                        ▼
                 ┌──────────────┐
         ┌──────▶│ IN_PROGRESS  │◀──── PATCH (isComplete=false)
         │       └──────┬───────┘
         │              │
         │    ┌─────────┴──────────┐
         │    │                    │
         │    ▼                    ▼
         │ SOLVED             ABANDONED
         │ (PATCH             (server-initiated:
         │  isComplete=true)   new game created)
         │
         └── resume via GET /games/current
```

Imported games start as `IN_PROGRESS`; their origin is recorded as `"imported"` in the `difficulty` field.

### Valid Transitions

| From | To | Trigger |
| --- | --- | --- |
| (new) | IN_PROGRESS | `createGame` or `createGameFromExistingGrid` |
| IN_PROGRESS | SOLVED | `PATCH` with `isComplete=true` |
| IN_PROGRESS | IN_PROGRESS | `PATCH` with `isComplete=false` (progress save) |
| IN_PROGRESS | ABANDONED | Server calls `abandonGame` before creating new game |

There is no client-initiated abandon — the server transitions the prior game automatically.

## Single-Active-Game Invariant

Only one game per player may be `IN_PROGRESS` at any time. Enforced server-side in `GameServiceImpl.abandonAnyInProgressGame()`:

```text
createGame(userId, difficulty):
  1. findInProgress(userId)     ← DynamoDB query
  2. if found → abandonGame()   ← DynamoDB update (status=ABANDONED, endedAt=now)
  3. generate new puzzle
  4. save new game              ← DynamoDB put
```

Validation happens **before** abandonment for import flows — to avoid wasting the DB abandon operation on a grid that will be rejected:

```text
createGameFromExistingGrid(userId, grid):
  1. validatePuzzle(grid)       ← duplicate check
  2. solveGrid(grid)            ← solvability check
  3. hasSingleSolution(grid)    ← uniqueness check
  [throws InvalidPuzzleException on any failure]
  4. abandonAnyInProgressGame() ← only if grid is valid
  5. save new game
```

## REST API

All endpoints are under `/api/v1/games` and require JWT authentication. The `userId` is always extracted from the JWT principal — never trusted from the request body.

| Method | Path | Request | Response | Notes |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/games` | `{"difficulty": "medium"}` | 201 `GameState` | Default difficulty: medium |
| POST | `/api/v1/games/from-image` | `CreateGameFromGridRequest` | 201 `GameState` | 422 if invalid puzzle |
| GET | `/api/v1/games/current` | — | `GameState` or 204 | 204 if no active game |
| GET | `/api/v1/games/{gameId}` | — | `GameState` | 404 if not found |
| PATCH | `/api/v1/games/{gameId}` | `GameUpdateRequest` | 200 (no body) | Saves progress |

### Import Validation Error Messages

`InvalidPuzzleException` is mapped to HTTP 422 by `InvalidPuzzleExceptionMapper` in `com.sudoku.exception`. Three concrete subclasses carry fixed messages:

| Exception | Message |
| --- | --- |
| `DuplicateDigitsException` | `"Puzzle contains duplicate digits — check rows, columns, and boxes for conflicts"` |
| `PuzzleHasNoSolutionException` | `"Puzzle has no valid solution — it may have been scanned incorrectly"` |
| `PuzzleHasMultipleSolutionsException` | `"Puzzle has multiple solutions — a valid sudoku must have exactly one solution"` |

All three produce `ErrorResponse("INVALID_PUZZLE", <message>)` — code is always `INVALID_PUZZLE` regardless of subtype.

## DynamoDB Schema

Table: `SudokuGames{suffix}` (name injected via `sudoku.dynamodb.table-name`)

| Attribute | DynamoDB Type | Key Role | Notes |
| --- | --- | --- | --- |
| `userId` | String | Partition Key | JWT principal name |
| `gameId` | String | Sort Key | UUID generated at creation |
| `difficulty` | String | — | "easy" / "medium" / "hard" / "expert" / "imported" |
| `originalGrid` | String | — | JSON-serialized `Grid` (inner rows stored as list-of-lists) |
| `solutionGrid` | String | — | JSON-serialized `Grid`; non-nullable — every game has a solution |
| `currentGrid` | String | — | JSON-serialized `Grid`; updated on each PATCH |
| `candidates` | String | — | JSON-serialized `CandidatesGrid` |
| `timeSpentSeconds` | Number | — | Cumulative; overwritten (not incremented) |
| `status` | String | — | "IN_PROGRESS" / "SOLVED" / "ABANDONED" |
| `hintsUsed` | Number | — | Overwritten from client |
| `startedAt` | String | — | ISO-8601 UTC |
| `endedAt` | String | — | ISO-8601 UTC; null while in progress |

Grids are stored as JSON strings because DynamoDB has no native multi-dimensional list type. Serialization/deserialization is handled in `GameItem` using a static `ObjectMapper`.

### DynamoDB Access Patterns

| Operation | DynamoDB API | Key Condition |
| --- | --- | --- |
| Save new game | PutItem | userId + gameId |
| Load game by id | GetItem | userId + gameId |
| Find in-progress game | Query + client-side filter | userId (all games), filter status=IN_PROGRESS |
| Update progress | UpdateItem (fetch-mutate-put) | userId + gameId |
| Abandon game | UpdateItem (fetch-mutate-put) | userId + gameId |

`findInProgress` uses a partition-key-only query (returns all games for user) then filters in memory for `IN_PROGRESS`. This is acceptable given the single-active-game invariant means at most one `IN_PROGRESS` record exists per user at any time.

## GameItem — Entity Layer

`GameItem` is the DynamoDB entity (`@DynamoDbBean`). It is a mutable Java class (not a record) because the DynamoDB Enhanced Client requires a no-arg constructor and getters/setters.

Conversion is symmetric:

```text
GameState (DTO, immutable record)
    ↕  GameItem.from(state) / item.toGameState()
GameItem (entity, mutable, JSON grids)
```

Two mutation methods on `GameItem`:

**`markAbandoned(String now)`** — sets `status=ABANDONED`, `endedAt=now`

**`applyUpdate(GameUpdateRequest request, String now)`** — overwrites `currentGrid`, `candidates`, `timeSpentSeconds`, optionally `hintsUsed`; sets `status=SOLVED` + `endedAt=now` if `isComplete=true`, otherwise keeps `IN_PROGRESS`

## GameStatus Enum

| Value | String | Meaning |
| --- | --- | --- |
| `IN_PROGRESS` | `"IN_PROGRESS"` | Active game |
| `SOLVED` | `"SOLVED"` | Player completed puzzle |
| `ABANDONED` | `"ABANDONED"` | Superseded by new game |

`@JsonValue` on `getValue()` ensures Jackson serializes the string form, not the enum name.

## DTOs

### GameState (full game record)

Returned by all read and create endpoints. Contains the complete game including all grids, status, timing, and hints.

Grid fields use domain types: `originalGrid`, `solutionGrid`, `currentGrid` are `Grid`; `candidates` is `CandidatesGrid`.

Key nullable fields: `endedAt` (null while in progress). `solutionGrid` is non-nullable — every persisted game has a solution.

### GameUpdateRequest (progress save)

Sent by client on each autosave:

| Field | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `currentGrid` | `Grid` | No | Full grid, not a diff |
| `candidates` | `CandidatesGrid` | No | Full pencil marks |
| `timeSpentSeconds` | `int` | No | Cumulative total |
| `isComplete` | `Boolean` | Yes | True triggers SOLVED transition |
| `hintsUsed` | `Integer` | Yes | If null, hints count is not updated |
| `events` | `List<PuzzleEvent>` | Yes | Buffered puzzle-play actions to log (see Puzzle-Play Event Logging). Null/absent on older clients — no logging, no error |

The client always sends the full grid (not a diff). The server overwrites, not merges.
The `events` array is observability-only: it never affects the persisted game state
or the PATCH response, and a malformed or absent `events` array never fails the save.

### PuzzleEvent (buffered action)

Each entry describes one player action the client observed since the last sync. The
shape is a single flat record covering all event types; fields not relevant to a
given `type` are null.

| Field | Type | Notes |
| --- | --- | --- |
| `type` | `String` | One of `NUMBER`, `NUMBER_CLEAR`, `HINT_REQUEST`, `HINT_RESPONSE` |
| `r`, `c` | `Integer` | Zero-based cell coordinates (`NUMBER`, `NUMBER_CLEAR`) |
| `v` | `Integer` | Placed digit 1–9 (`NUMBER`) |
| `cid` | `String` | Client-generated UUID pairing a `HINT_REQUEST` with its `HINT_RESPONSE` |
| `clientTs` | `Long` | Client epoch-millis when the action happened (preserves ordering within the batch) |
| `techniqueName`, `strategyRank`, `difficulty`, `found` | — | `HINT_RESPONSE` only: what the hint engine returned |
| `minRank`, `excludedRanks` | — | `HINT_REQUEST` only: the parameters the client asked for |

`NUMBER_RESULT` is **not** a client-supplied type — the server derives it (see below).

### CreateGameFromGridRequest

Single-field record: `Grid originalGrid`. Used by the image import flow.

## Puzzle-Play Event Logging (Observability)

The AI Coach logs full request/response content (`COACH_*`) so coaching quality can
be reviewed after the fact. On its own that is blind to what the player *did* around
each coach turn — which digits they placed, whether those were correct, when they
cleared cells, and when they asked for a hint. This component emits structured log
lines for those player actions so a whole puzzle-play session can be reconstructed
and correlated with the coach conversation.

**Correlation key — `pid`.** Every structured log line for a play session carries
`pid`, which is the game's `gameId`. `pid` is the single key that joins puzzle-play
events with the `COACH_REQUEST`/`COACH_RESPONSE` lines for the same game (which also
carry `pid` — see the Sudoku Coach LLD). `cid` remains a *within-interaction* pair
key (one coach turn, or one hint request↔response); `pid` is the *whole-session* key.

**Transport — the existing PATCH sync, not a new endpoint.** Individual placements
and clears live in client state and are never sent to the server per-move. The client
buffers them (see React Frontend LLD) and flushes the buffer as the `events` array on
the existing `PATCH /api/v1/games/{gameId}` autosave. `GameServiceImpl.update` — which
already loads the `GameItem` (and therefore has the authoritative `solutionGrid`,
`gameId`, and JWT `userId` in hand) — walks `events` and emits one log line per event
via `PuzzleEventLogger`. This adds no per-move network cost and no new attack surface.

**Emitted event types.** Common fields on every line: `type`, `pid`, `userId`, `ts`
(server epoch-millis). `userId` is taken from the JWT principal (the same value used
for the game write), so it is trustworthy, not client-asserted.

| Type | Origin | Fields beyond common |
| --- | --- | --- |
| `NUMBER` | client action | `r`, `c`, `v`, `clientTs` |
| `NUMBER_RESULT` | **server-derived** | `r`, `c`, `v`, `correct`, `clientTs` |
| `NUMBER_CLEAR` | client action | `r`, `c`, `clientTs` |
| `HINT_REQUEST` | client action | `cid`, `clientTs`, `minRank?`, `excludedRanks?` |
| `HINT_RESPONSE` | client action | `cid`, `clientTs`, `techniqueName`, `strategyRank`, `difficulty`, `found` |

For each buffered `NUMBER`, the server emits **two** lines: the raw `NUMBER` action,
then a `NUMBER_RESULT` whose `correct = (solutionGrid[r][c] == v)`. Correctness is
computed server-side against the stored solution — authoritative, and never shown to
the player (the game does not reveal move correctness during play). Splitting into two
lines keeps the player's action stream (`NUMBER`) distinct from the server's verdict
and keeps per-type formatting clean for the `download-puzzle-logs.sh` summary.

**Robustness.** `r`, `c`, and `v` are client-supplied and untrusted. An event with an
unknown `type`, missing required fields, or out-of-range coordinates/digit is logged at
WARN and skipped — in particular, the `NUMBER_RESULT` solution lookup is bounds-guarded
so a bad coordinate degrades to a dropped event rather than throwing and failing the
PATCH. Event logging must never break progress persistence. The server processes at
most 500 events per request; if the
client marks the batch truncated (its buffer overflowed before flush), the server
emits an `EVENTS_TRUNCATED` marker line so the gap is visible downstream.

**Storage.** These lines go to the same CloudWatch log group as all other Lambda logs
at the existing 30-day retention — no separate store — and log gameplay actions (cell
coordinates, placed digits, move correctness, hint technique), which are non-sensitive
under this app's threat model per `docs/arrows/security-standards.md` Logging Policy.

Lines are built with the injected Jackson `ObjectMapper` (`writeValueAsString` on an
`ObjectNode`), never string templating, so freeform fields escape correctly — the same
rule the coach logging follows.

## Observed Design Decisions

| Decision | Chosen | Alternatives Considered | Rationale |
| --- | --- | --- | --- |
| Puzzle-play events ride the existing PATCH autosave | `events` array on `GameUpdateRequest`, flushed with the 60s/visibility/complete sync | New per-move telemetry endpoint; derive moves by diffing the synced grid server-side | Per-move endpoint multiplies Lambda invocations and CloudWatch writes per keystroke; grid-diffing loses ordering, timing, and any move cleared before a sync. Buffer-and-flush keeps ~1 call per 60s while preserving faithful per-action data |
| `NUMBER_RESULT.correct` computed server-side | Compare `v` against the loaded `solutionGrid` in `GameServiceImpl.update` | Trust a `correct` flag sent by the client | Server already holds the authoritative solution; client-asserted correctness is spoofable and redundant |
| Emit `NUMBER` and `NUMBER_RESULT` as two lines | Raw action line + separate server verdict line | Single `NUMBER` line with an inline `correct` field | Keeps the player action stream separate from the server verdict, matches the named event vocabulary, and makes per-type counts in the summary tool straightforward |
| Malformed/unknown events skipped, not rejected | Log WARN and continue; never 500 the PATCH | Reject the whole PATCH with 400 | Observability must not break progress persistence; a bad event from a stale client should degrade to a dropped log line, not a lost save |
| Single-active-game enforced server-side | `abandonAnyInProgressGame` on create | Client sends explicit abandon request | Simpler client; server owns invariant; no race condition if client crashes mid-session |
| Validation before abandonment in import flow | 3-stage validation first, then abandon | Abandon first regardless | Avoids wasting a DB write on grids that will be rejected; better UX (no "ghost" abandonment) |
| Full grid overwrite on PATCH | Client sends complete state | Server applies delta/diff | Eliminates merge conflict logic; client is source of truth for current state |
| JSON string serialization for grids | `toJson()` / `fromJson()` in GameItem | DynamoDB List type, custom serializer | DynamoDB native List type doesn't handle nested lists cleanly; JSON string is portable and debuggable |
| userId from JWT only | `securityContext.getUserPrincipal().getName()` | userId in request body | Prevents users from reading/writing other users' games; never trust client-supplied identity |
| `findInProgress` client-side filter | Query all user games, filter in memory | GSI on status | Single-active invariant means ≤1 IN_PROGRESS item per user; GSI cost not justified |
| `update` as fetch-mutate-save | GetItem → mutate → UpdateItem | DynamoDB UpdateExpression | Simpler code; no risk of expression syntax errors; acceptable for single-user personal data |

## Technical Debt & Inconsistencies

- `GameServiceImpl.createGameFromExistingGrid` sets `difficulty` to the plain string `"imported"`. This conflates the difficulty field with the import source — a semantic mismatch, but acceptable for now.
- `DynamoDbGameRepository.update()` silently no-ops if the game doesn't exist (null check returns early). Callers receive no indication that the update was dropped. (AEH-EX-009 — deferred)
- `findInProgress` queries all games for a user then filters in memory. If a user accumulates many games, this scan grows. Acceptable now; a GSI on `status` would fix it at scale.
- The `ObjectMapper` in `GameItem` is a static field. Jackson ObjectMapper is thread-safe and this is fine, but it's an implicit dependency hidden inside an entity class.

## Behavioral Quirks

- `GET /api/v1/games/current` returns 204 (no body) when no game is in progress. The frontend must handle this as a valid "no active game" state, distinct from an error.
- `PATCH /api/v1/games/{gameId}` returns 200 with no body. The frontend does not need the updated state echoed back.
- Abandonment is silent to the player — the prior game's status changes to ABANDONED in DynamoDB, but the client is never explicitly told this happened. The client discovers it indirectly when `GET /games/current` returns the new game.
- `solveGrid()` called during import validation uses randomized backtracking — for a valid unique-solution puzzle this always returns the correct solution, but the path taken is random. The solution stored in `GameItem.solutionGrid` is deterministically correct (unique solution) even though the solver is non-deterministic.

## References

- `backend/src/main/java/.../game/web/GameResource.java`
- `backend/src/main/java/.../game/GameService.java`
- `backend/src/main/java/.../game/GameServiceImpl.java`
- `backend/src/main/java/.../game/GameRepository.java`
- `backend/src/main/java/.../game/persistence/DynamoDbGameRepository.java`
- `backend/src/main/java/.../game/persistence/GameItem.java`
- `backend/src/main/java/.../game/GameStatus.java`
- `backend/src/main/java/.../game/InvalidPuzzleException.java`
- `backend/src/main/java/.../game/DuplicateDigitsException.java`
- `backend/src/main/java/.../game/PuzzleHasNoSolutionException.java`
- `backend/src/main/java/.../game/PuzzleHasMultipleSolutionsException.java`
- `backend/src/main/java/.../game/GameNotFoundException.java`
- `backend/src/main/java/.../web/exception/GameNotFoundExceptionMapper.java`
- `backend/src/main/java/.../game/web/GameState.java`
- `backend/src/main/java/.../game/web/GameUpdateRequest.java`
- `backend/src/main/java/.../game/web/PuzzleEvent.java`
- `backend/src/main/java/.../game/PuzzleEventLogger.java`
- `backend/src/main/java/.../game/web/CreateGameFromGridRequest.java`
- `backend/src/main/java/.../game/web/GameHistoryEntry.java`
- `backend/src/main/java/.../game/web/GameHistoryResponse.java`
- `backend/src/main/java/.../domain/Grid.java`
- `backend/src/main/java/.../domain/CandidatesGrid.java`
- Depends on: Puzzle Generation & Validation (generatePuzzle, solveGrid, hasSingleSolution, validatePuzzle)
- Depended on by: nothing (terminal component for game operations)
