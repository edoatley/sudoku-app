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

## Firestore Persistence (GCP facet)

`FirestoreGameRepository implements GameRepository` is the GCP adapter, selected at build time
(`@IfBuildProperty(name = "sudoku.persistence", stringValue = "firestore")`; `DynamoDbGameRepository`
is the `@DefaultBean`). It uses the Google Cloud Firestore client, running as the Cloud Run runtime
service account (`roles/datastore.user`). The `GameRepository` contract and all callers are unchanged.

### Document layout

Top-level collection `games`, one document per game, **document id = `<userId>__<gameId>`**.

Using the composite `userId__gameId` id — rather than the bare `gameId` — preserves the AWS
IDOR-safety property structurally: a `get` requires the caller's `userId` in the path, so one user
can never fetch another's game even knowing the `gameId` (mirrors DynamoDB's composite-key `GetItem`).
`userId` and `gameId` are also stored as fields for querying. **Grids remain JSON strings** —
Firestore prohibits nested arrays (an array can't contain an array), so a 9×9 grid can't be a native
nested array; JSON strings match `GameItem`. The adapter **reuses `GameItem` directly** as the
document model: Firestore's POJO mapper serialises its getters/setters and ignores the (inert)
`@DynamoDbBean` annotations, so grid JSON (de)serialization and the `applyUpdate`/`markAbandoned`
mutations are shared with the AWS adapter with no duplication (no separate document class).

### Access patterns

| Operation | Firestore API | Query |
| --- | --- | --- |
| Save new game | `document("<userId>__<gameId>").set(doc)` | — |
| Load game by id | `document("<userId>__<gameId>").get()` | — (userId in the doc path = IDOR guard) |
| Find in-progress game | `collection("games").where(userId==).where(status=="IN_PROGRESS").limit(1)` | served by the `(userId, status, endedAt)` index (key-prefix match) |
| Game history | `where(userId==).where(status in ["SOLVED","ABANDONED"]).orderBy(endedAt desc)` | composite index `(userId, status, endedAt)` |
| Update / abandon | fetch-mutate-`set` (in a transaction — see below) | — |

Unlike the DynamoDB adapter (which queries all of a user's games and filters `IN_PROGRESS`
client-side), Firestore filters `status` **server-side**. A **single** composite index on
`(userId, status, endedAt)` covers both queries — it serves the history query directly and
`findInProgress` as a key-prefix match — so only one index is declared. It is a
`google_firestore_index` resource in `infra/gcp/firestore.tf`, created **with the database (not
gated on `deploy_cloud_run`)** so the index build finishes before the app serves queries — a new
composite index takes minutes to build, and a query issued before it is ready fails
`FAILED_PRECONDITION`.

### Single-active-game invariant

The invariant (`abandonAnyInProgressGame` before persisting a new game) is orchestrated by
`GameServiceImpl` (`findInProgress → abandonGame → save`) — *shared* game-lifecycle code, not the
repository. To keep the slice contained (the Firestore adapter mirrors the DynamoDB port contract
exactly, and `GameServiceImpl` is unchanged), this stays **non-atomic on GCP, matching AWS**
(acceptable because a single client serialises a player's requests). Wrapping abandon+save in a
Firestore transaction is a deferred hardening — it would require an interface/service change — see
`GL-GCP-006` (`[D]`).

### Decisions & Alternatives

| Decision | Chosen | Alternative | Rationale |
| --- | --- | --- | --- |
| Collection shape | Top-level `games`, composite `userId__gameId` id | Subcollection `players/{userId}/games/{gameId}` | Keeps the game adapter independent of the player adapter and mirrors the table-per-entity model; the composite id still gives per-user isolation + IDOR safety |
| Grid storage | JSON strings | Native Firestore structures | Firestore can't nest arrays; JSON reuses `GameItem` serialization and keeps the DTO mapping identical |
| Single-active-game | Non-atomic (AWS parity) | Firestore transaction | Keeps the slice contained — the adapter matches the DynamoDB port contract and `GameServiceImpl` is unchanged; the transaction is deferred (`GL-GCP-006` `[D]`) |
| Document model | Reuse `GameItem` | Separate `FirestoreGameDocument` | Firestore's POJO mapper serialises `GameItem` (DynamoDB annotations inert); shares grid JSON + mutations, zero duplication |

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

`GameServiceImpl.update` walks the buffered client `events` array on every
`PATCH /api/v1/games/{gameId}` and emits one structured log line per event via
`PuzzleEventLogger`, so a whole puzzle-play session can be reconstructed and correlated
with the AI coach conversation for the same game. **Full log-message catalogue, the
`pid`/`cid` correlation model, transport, robustness, and storage policy are documented
centrally in `docs/llds/observability.md`** — this section covers only this component's
part: it already loads the `GameItem` on every PATCH (and therefore has the authoritative
`solutionGrid`, `gameId`, and JWT `userId` in hand), so `PuzzleEventLogger` is handed
those directly rather than re-fetching them, and `NUMBER_RESULT`'s correctness check is
derived here from the stored solution rather than trusting anything client-supplied.

Spec: `docs/specs/game-lifecycle-specs.md` — `GL-BE-040..047`, `GL-API-005`.

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
