# Game History

**Created**: 2026-05-24
**Status**: In Progress

## Context and Current State

Game history was previously stored in browser localStorage only (key `sudoku_gameHistory`, spec FE-UI-042). This component adds a server-side read path so that history persists across devices and browser storage clears.

This is a **read-only** concern — it queries existing `SudokuGames` DynamoDB records written by the Game Lifecycle component. It does not write any new records. It extends the existing `GameRepository`, `GameService`, and `GameResource` rather than introducing new classes for those layers.

Files: `game/GameResource.java`, `game/GameService.java`, `game/GameServiceImpl.java`, `game/GameRepository.java`, `game/DynamoDbGameRepository.java`, `dto/GameHistoryEntry.java`, `dto/GameHistoryResponse.java`.

## REST API

All endpoints require JWT authentication. `userId` is always extracted from the JWT principal — never trusted from the request.

| Method | Path | Query params | Response | Notes |
|--------|------|-------------|----------|-------|
| GET | `/api/v1/games/history` | `?limit=20` | 200 `GameHistoryResponse` | Default limit 20, max 100 |

### GameHistoryEntry

```json
{
  "gameId":        "uuid-string",
  "difficulty":    "easy | medium | hard | imported",
  "outcome":       "won | abandoned",
  "endedAt":       "2026-05-24T14:32:00.000Z",
  "elapsedSeconds": 847,
  "hintsUsed":     2
}
```

### GameHistoryResponse

```json
{
  "entries": [ ...GameHistoryEntry ]
}
```

## DTO Design

`GameHistoryEntry` is a lightweight projection of `GameItem` — it contains only the fields needed for the history display and omits all grid data. This avoids deserialising the JSON grid strings (which `GameState.toGameState()` would do) for every item in the result set.

Outcome mapping (done in the repository, not the DTO):

| GameStatus | outcome |
|------------|---------|
| SOLVED | `"won"` |
| ABANDONED | `"abandoned"` |
| IN_PROGRESS | excluded from results |

## Query Strategy

```
DynamoDbGameRepository.findHistory(userId, limit):
  1. Query SudokuGames partition key = userId  (returns all games for user)
  2. Stream items
  3. Filter: status ≠ IN_PROGRESS  (also filter endedAt != null as a safety guard)
  4. Sort: endedAt descending  (ISO-8601 strings are lexicographically sortable — no date parsing needed)
  5. Limit: cap at limit
  6. Map: GameItem → GameHistoryEntry (outcome derived here)
```

No GSI is needed. The single-active-game invariant (at most one IN_PROGRESS per user) means the vast majority of records are completed games, so the in-memory filter is efficient in practice.

## Limit Handling

- Default: 20 (via `@DefaultValue("20")` on the JAX-RS query param)
- Maximum: 100 (clamped with `Math.min(limit, 100)` in `GameResource`)
- The clamped value is passed through to `GameServiceImpl` → `DynamoDbGameRepository`

## Frontend Fetch Strategy

`usePlayerProfile.js` exposes a `fetchHistory()` callback:

1. Call `getGameHistory(20)` (authenticated)
2. On success: map server entries to the local history shape, call `setHistory`, write to localStorage cache (capped at 10)
3. On failure: swallow the error silently — keep whatever localStorage history is already in state (spec GH-UI-004)

`PuzzleHistoryDialog` gains `onRefresh` and `loading` props. On dialog open and on refresh-button click, `Header` calls `fetchHistory` and threads the loading state into the dialog.

Score calculation remains client-side (FE-UI-042b TODO unchanged — backend sends raw `elapsedSeconds` and `hintsUsed`).

## Decisions

| Decision | Chosen | Rationale |
|----------|--------|-----------|
| Endpoint path | `/games/history` | Keeps it in the existing `GameResource`; no new resource class needed |
| New arrow vs extend game-lifecycle | New `game-history` arrow | Read aggregation is a distinct concern from lifecycle mutation |
| Outcome mapping location | Repository (DynamoDbGameRepository) | Keeps DTO a pure data record; mapping logic is co-located with the query |
| Limit clamping location | GameResource | Enforces the max before delegating; service and repo receive an already-safe value |
| Score | Client-side | Deferred per FE-UI-042b; backend sends raw data |
| Caching | localStorage written on fetch success | Provides offline fallback without a separate cache layer |

## References

- Depends on: Game Lifecycle (provides `GameRepository`, `GameItem`, `GameStatus`, `GameResource`)
- Depended on by: React Frontend (`PuzzleHistoryDialog`, `usePlayerProfile`)
- Specs: `docs/specs/game-history-specs.md`
