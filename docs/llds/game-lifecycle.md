# Game Lifecycle

**Created**: 2026-04-18
**Status**: Complete

## Context and Current State

The Game Lifecycle component owns all stateful game operations: creating games, persisting player progress, enforcing the single-active-game invariant, and managing the state machine from `IN_PROGRESS` through to `SOLVED` or `ABANDONED`. It is the only component that reads and writes game records in DynamoDB.

Files: `game/GameResource.java`, `game/GameService.java`, `game/GameServiceImpl.java`, `game/GameRepository.java`, `game/DynamoDbGameRepository.java`, `game/GameItem.java`, `game/GameStatus.java`, `game/InvalidPuzzleException.java`, `game/InvalidPuzzleExceptionMapper.java`, `dto/GameState.java`, `dto/GameUpdateRequest.java`, `dto/CreateGameFromGridRequest.java`.

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

`IMPORTED` is not a status — it is a `difficulty` label. Imported games start as `IN_PROGRESS`.

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

`InvalidPuzzleException` is mapped to HTTP 422 by `InvalidPuzzleExceptionMapper`. Three possible messages, surfaced directly to the user:

| Condition | Message |
| --- | --- |
| Duplicate digits | `"Puzzle contains duplicate digits — check rows, columns, and boxes for conflicts"` |
| No solution | `"Puzzle has no valid solution — it may have been scanned incorrectly"` |
| Multiple solutions | `"Puzzle has multiple solutions — a valid sudoku must have exactly one solution"` |

## DynamoDB Schema

Table: `SudokuGames{suffix}` (name injected via `sudoku.dynamodb.table-name`)

| Attribute | DynamoDB Type | Key Role | Notes |
| --- | --- | --- | --- |
| `userId` | String | Partition Key | JWT principal name |
| `gameId` | String | Sort Key | UUID generated at creation |
| `difficulty` | String | — | "easy" / "medium" / "hard" / "expert" / "imported" |
| `originalGrid` | String | — | JSON-serialized `List<List<Integer>>` |
| `solutionGrid` | String | — | JSON-serialized; nullable for imported games |
| `currentGrid` | String | — | JSON-serialized; updated on each PATCH |
| `candidates` | String | — | JSON-serialized `List<List<List<Integer>>>` |
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
| `IMPORTED` | `"imported"` | (Unused as status — see quirks) |

`@JsonValue` on `getValue()` ensures Jackson serializes the string form, not the enum name.

## DTOs

### GameState (full game record)

Returned by all read and create endpoints. Contains the complete game including all grids, status, timing, and hints.

Key nullable fields: `solutionGrid` (null for imported games), `endedAt` (null while in progress).

### GameUpdateRequest (progress save)

Sent by client on each autosave (every 5 seconds from the frontend):

| Field | Type | Nullable | Notes |
| --- | --- | --- | --- |
| `currentGrid` | `List<List<Integer>>` | No | Full grid, not a diff |
| `candidates` | `List<List<List<Integer>>>` | No | Full pencil marks |
| `timeSpentSeconds` | `int` | No | Cumulative total |
| `isComplete` | `Boolean` | Yes | True triggers SOLVED transition |
| `hintsUsed` | `Integer` | Yes | If null, hints count is not updated |

The client always sends the full grid (not a diff). The server overwrites, not merges.

### CreateGameFromGridRequest

Single-field record: `List<List<Integer>> originalGrid`. Used by the image import flow.

## Observed Design Decisions

| Decision | Chosen | Alternatives Considered | Rationale |
| --- | --- | --- | --- |
| Single-active-game enforced server-side | `abandonAnyInProgressGame` on create | Client sends explicit abandon request | Simpler client; server owns invariant; no race condition if client crashes mid-session |
| Validation before abandonment in import flow | 3-stage validation first, then abandon | Abandon first regardless | Avoids wasting a DB write on grids that will be rejected; better UX (no "ghost" abandonment) |
| Full grid overwrite on PATCH | Client sends complete state | Server applies delta/diff | Eliminates merge conflict logic; client is source of truth for current state |
| JSON string serialization for grids | `toJson()` / `fromJson()` in GameItem | DynamoDB List type, custom serializer | DynamoDB native List type doesn't handle nested lists cleanly; JSON string is portable and debuggable |
| userId from JWT only | `securityContext.getUserPrincipal().getName()` | userId in request body | Prevents users from reading/writing other users' games; never trust client-supplied identity |
| `findInProgress` client-side filter | Query all user games, filter in memory | GSI on status | Single-active invariant means ≤1 IN_PROGRESS item per user; GSI cost not justified |
| `update` as fetch-mutate-save | GetItem → mutate → UpdateItem | DynamoDB UpdateExpression | Simpler code; no risk of expression syntax errors; acceptable for single-user personal data |

## Technical Debt & Inconsistencies

- `GameStatus.IMPORTED` exists as an enum value but is used as a `difficulty` string ("imported"), not as a `status`. The `status` field of an imported game is `IN_PROGRESS`. This means `IMPORTED` is a vestigial enum value that is never written to the `status` attribute.
- `GameServiceImpl.createGameFromExistingGrid` sets `difficulty` to `GameStatus.IMPORTED.getValue()` (i.e., `"imported"`). This conflates the difficulty field with the import source — a semantic mismatch.
- `DynamoDbGameRepository.update()` silently no-ops if the game doesn't exist (null check returns early). Callers receive no indication that the update was dropped.
- `findInProgress` queries all games for a user then filters in memory. If a user accumulates many games, this scan grows. Acceptable now; a GSI on `status` would fix it at scale.
- The `ObjectMapper` in `GameItem` is a static field. Jackson ObjectMapper is thread-safe and this is fine, but it's an implicit dependency hidden inside an entity class.

## Behavioral Quirks

- `GET /api/v1/games/current` returns 204 (no body) when no game is in progress. The frontend must handle this as a valid "no active game" state, distinct from an error.
- `PATCH /api/v1/games/{gameId}` returns 200 with no body. The frontend does not need the updated state echoed back.
- Abandonment is silent to the player — the prior game's status changes to ABANDONED in DynamoDB, but the client is never explicitly told this happened. The client discovers it indirectly when `GET /games/current` returns the new game.
- `solveGrid()` called during import validation uses randomized backtracking — for a valid unique-solution puzzle this always returns the correct solution, but the path taken is random. The solution stored in `GameItem.solutionGrid` is deterministically correct (unique solution) even though the solver is non-deterministic.

## References

- `backend/src/main/java/.../game/GameResource.java`
- `backend/src/main/java/.../game/GameService.java`
- `backend/src/main/java/.../game/GameServiceImpl.java`
- `backend/src/main/java/.../game/GameRepository.java`
- `backend/src/main/java/.../game/DynamoDbGameRepository.java`
- `backend/src/main/java/.../game/GameItem.java`
- `backend/src/main/java/.../game/GameStatus.java`
- `backend/src/main/java/.../game/InvalidPuzzleException.java`
- `backend/src/main/java/.../game/InvalidPuzzleExceptionMapper.java`
- `backend/src/main/java/.../dto/GameState.java`
- `backend/src/main/java/.../dto/GameUpdateRequest.java`
- `backend/src/main/java/.../dto/CreateGameFromGridRequest.java`
- Depends on: Puzzle Generation & Validation (generatePuzzle, solveGrid, hasSingleSolution, validatePuzzle)
- Depended on by: nothing (terminal component for game operations)
