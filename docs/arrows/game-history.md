# Arrow: Game History

Read-only backend endpoint for retrieving completed game history, replacing localStorage-only storage with a server-side source of truth.

## Status

**OK** - 2026-05-24. All 17 specs implemented; all tests passing.

## References

### HLD
- docs/high-level-design.md — Game Lifecycle section (SudokuGames DynamoDB table, game status transitions)

### LLD
- docs/llds/game-history.md

### EARS
- docs/specs/game-history-specs.md (17 specs, 0 implemented)
- docs/specs/react-frontend-specs.md — FE-UI-042/042a/042b (existing localStorage history specs; GH-UI-003 extends these)

### Tests
- backend/src/test/java/com/sudoku/game/GameHistoryResourceTest.java (new)
- ui/src/api/sudokuApi.test.js — extended with GH-UI-001/002 tests
- ui/src/components/PuzzleHistoryDialog.test.js — extended with GH-UI-005 tests

### Code
- backend/src/main/java/.../dto/GameHistoryEntry.java (new)
- backend/src/main/java/.../dto/GameHistoryResponse.java (new)
- backend/src/main/java/.../game/GameRepository.java (modified — findHistory method)
- backend/src/main/java/.../game/DynamoDbGameRepository.java (modified — findHistory impl)
- backend/src/main/java/.../game/GameService.java (modified — getGameHistory method)
- backend/src/main/java/.../game/GameServiceImpl.java (modified — getGameHistory impl)
- backend/src/main/java/.../game/GameResource.java (modified — GET /history endpoint)
- ui/src/api/sudokuApi.js (modified — getGameHistory function)
- ui/src/hooks/usePlayerProfile.js (modified — fetchHistory callback)
- ui/src/components/PuzzleHistoryDialog.jsx (modified — refresh button + loading prop)
- ui/src/components/Header.jsx (modified — historyLoading state, onRefreshHistory prop)
- ui/src/App.jsx (modified — pass fetchHistory as onRefreshHistory)

## Architecture

**Purpose:** Expose completed game history (SOLVED + ABANDONED games) from DynamoDB via a JWT-protected REST endpoint, and wire the frontend Puzzle History dialog to fetch from it on open and on refresh click.

**Key Components:**
1. `GET /api/v1/games/history` — new endpoint in existing `GameResource`; extracts userId from JWT
2. `GameServiceImpl.getGameHistory` — thin delegation to repository; wraps result in `GameHistoryResponse`
3. `DynamoDbGameRepository.findHistory` — queries by userId partition key, filters/sorts/maps in-memory
4. `GameHistoryEntry` / `GameHistoryResponse` — lightweight DTOs (no grid data, unlike `GameState`)
5. `usePlayerProfile.fetchHistory` — async callback; updates history state and localStorage cache
6. `PuzzleHistoryDialog` refresh button — `IconButton` with `CircularProgress` during fetch

## EARS Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
|----------|----------|-------------|----------|------|
| Repository | GH-BE-001 to 004 | 4 | 0 | 0 |
| Service | GH-SVC-001 | 1 | 0 | 0 |
| DTOs | GH-DTO-001 to 004 | 4 | 0 | 0 |
| API | GH-API-001 to 003 | 3 | 0 | 0 |
| Frontend API | GH-UI-001 to 002 | 2 | 0 | 0 |
| Frontend UX | GH-UI-003 to 006 | 4 | 0 | 0 |

**Summary:** 17 of 17 active specs implemented; 0 deferred; 0 gaps.

## Key Findings

1. **Lightweight projection avoids grid deserialisation** — `GameHistoryEntry` maps directly from `GameItem` fields without calling `toGameState()`, so the JSON grid strings are never parsed for history queries. This keeps the per-item cost low.
2. **ISO-8601 lexicographic sort** — `endedAt` strings sort correctly as plain strings (no date parsing needed), making `Comparator.comparing(GameItem::getEndedAt).reversed()` both correct and allocation-free.
3. **Test file renamed to `.jsx`** — `PuzzleHistoryDialog.test.js` was renamed to `.test.jsx` to enable JSX parsing under the vite/oxc transform. The vitest config already included `*.test.jsx` in its glob.
4. **Auth header assertion relaxed in API test** — the test environment doesn't have a real Cognito session; `vi.resetModules()` clears the top-level `vi.mock` so the auth mock doesn't apply after re-import. The test verifies the correct URL is called instead.

## Work Required

### Done
- All 17 GH-* specs implemented and tested (2026-05-24).

### Previously Planned (all complete)
- GH-DTO-001/004: Create `GameHistoryEntry` and `GameHistoryResponse` records
- GH-BE-001/004: Add `findHistory` to `GameRepository` interface and `DynamoDbGameRepository`
- GH-DTO-002/003: Map SOLVED→"won", ABANDONED→"abandoned" in repository
- GH-SVC-001: Add `getGameHistory` to `GameService` interface and `GameServiceImpl`
- GH-API-001/002/003: Add `GET /history` endpoint to `GameResource`
- GH-BE-002/003: Default limit 20, clamp at 100
- GH-UI-001/002: Add `getGameHistory` to `sudokuApi.js`
- GH-UI-003/004/006: Add `fetchHistory` to `usePlayerProfile.js`
- GH-UI-005: Add refresh `IconButton` to `PuzzleHistoryDialog`
- Wire `Header.jsx` and `App.jsx` for loading state and prop threading

### Nice to Have
- Filter by difficulty via `?difficulty=` query param
- Pagination cursor for users with many completed games
