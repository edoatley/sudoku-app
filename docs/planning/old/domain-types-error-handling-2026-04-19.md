# Domain Types & API Error Handling — Implementation Plan

**Created:** 2026-04-19  
**Specs:** `docs/specs/domain-types-specs.md`, `docs/specs/api-error-handling-specs.md`  
**LLDs:** `docs/llds/api-error-handling.md` (new), updates to `sudoku-logic.md`, `puzzle-generation-validation.md`, `game-lifecycle.md`  
**Branch:** `rc-enhancements-td`

## Goal

Replace raw `List<List<Integer>>` and `List<List<List<Integer>>>` types at all public API boundaries with named domain types (`Grid`, `CandidatesGrid`). Introduce a uniform `ErrorResponse` envelope and a layered exception hierarchy so every error at the REST boundary produces a consistent `{code, message, detail}` JSON body.

## Definition of Done

- All `[ ]` specs in `domain-types-specs.md` and `api-error-handling-specs.md` are marked `[x]`
- GL-BE-004/005/006 and GL-API-004 in `game-lifecycle-specs.md` are marked `[x]`
- SL-PROC-003 in `sudoku-logic-specs.md` is marked `[x]`
- All backend tests pass (`./mvnw test`)
- All frontend tests pass (`npm test`)
- No raw `List<List<Integer>>` or `List<List<List<Integer>>>` in any public method signature or DTO record field
- `InvalidPuzzleExceptionMapper` no longer exists in `com.sudoku.game` — moved/replaced by mappers in `com.sudoku.exception`
- `GameServiceImpl.loadGame()` no longer uses JAX-RS `NotFoundException`
- Arrow docs and index updated to reflect new arrows

---

## Phase 1 — Foundation: ErrorResponse + exception hierarchy

_No behaviour change. No wire format change. Tests should pass throughout._

### 1.1 — `ErrorResponse` DTO
- [x] Create `com.sudoku.dto.ErrorResponse` record with fields `code`, `message`, `detail` (@spec AEH-DTO-001)
- [x] Add two-arg constructor setting `detail` to null (@spec AEH-DTO-002)
- [x] Override `detail()` to return empty string when null (@spec AEH-DTO-003)
- [x] Add static constants `INVALID_GRID`, `INVALID_PUZZLE`, `GAME_NOT_FOUND`, `INTERNAL_ERROR` (@spec AEH-DTO-004)

### 1.2 — Import exception hierarchy
- [x] Make `InvalidPuzzleException` abstract in `com.sudoku.game` (@spec AEH-EX-001)
- [x] Create `DuplicateDigitsException extends InvalidPuzzleException` with fixed no-arg constructor message (@spec AEH-EX-002, AEH-EX-005)
- [x] Create `PuzzleHasNoSolutionException extends InvalidPuzzleException` with fixed no-arg constructor message (@spec AEH-EX-003, AEH-EX-005)
- [x] Create `PuzzleHasMultipleSolutionsException extends InvalidPuzzleException` with fixed no-arg constructor message (@spec AEH-EX-004, AEH-EX-005)
- [x] Update `GameServiceImpl.createGameFromExistingGrid()` to throw the three specific subclasses (@spec GL-BE-004, GL-BE-005, GL-BE-006)

### 1.3 — GameNotFoundException
- [x] Create `GameNotFoundException` in `com.sudoku.game` accepting `gameId` (@spec AEH-EX-006, AEH-EX-007)
- [x] Update `GameServiceImpl.loadGame()` to throw `GameNotFoundException` instead of JAX-RS `NotFoundException` (@spec GL-API-004)

### 1.4 — Exception mappers in `com.sudoku.exception`
- [x] Create package `com.sudoku.exception`
- [x] Create `InvalidGridExceptionMapper` — HTTP 400, code `INVALID_GRID` (@spec AEH-MAP-001, AEH-MAP-002)
- [x] Create `InvalidPuzzleExceptionMapper` — HTTP 422, code `INVALID_PUZZLE` (replaces the one in `com.sudoku.game`) (@spec AEH-MAP-001, AEH-MAP-003)
- [x] Create `GameNotFoundExceptionMapper` — HTTP 404, code `GAME_NOT_FOUND` (@spec AEH-MAP-001, AEH-MAP-004)
- [x] Create `GlobalExceptionMapper` — HTTP 500, code `INTERNAL_ERROR`, UUID correlation ID in `detail`, logs at ERROR (@spec AEH-MAP-005, AEH-MAP-006, AEH-MAP-007)
- [x] Delete `com.sudoku.game.InvalidPuzzleExceptionMapper`
- [x] Verify all mappers set `Content-Type: application/json` (@spec AEH-WIRE-001)

### 1.5 — Phase 1 verification
- [x] Run `./mvnw test` — all tests pass
- [x] Confirm HTTP 422 still returned for duplicate/no-solution/multiple-solution import errors (response body now uses `ErrorResponse` shape)
- [x] Confirm HTTP 404 returned for missing game (was JAX-RS NotFoundException, now GameNotFoundException via mapper)

---

## Phase 2 — Domain types: Grid and CandidatesGrid

_Wire format changes. Frontend must be updated in the same phase before integration testing._

### 2.1 — Domain records + serializers
- [x] Create `Grid` record in `com.sudoku.domain` with `rows` field and `row(i)`, `cell(r,c)`, `of(...)` (@spec DT-GRID-001, DT-GRID-004, DT-GRID-005, DT-GRID-006)
- [x] Create `GridSerializer` and `GridDeserializer` in `com.sudoku.domain` — wire format `{"rows": [[...],...]}` (@spec DT-GRID-002, DT-GRID-003)
- [x] Create `CandidatesGrid` record in `com.sudoku.domain` with `rows` field and `candidates(r,c)`, `of(...)` (@spec DT-CGRID-001, DT-CGRID-004, DT-CGRID-005)
- [x] Create `CandidatesGridSerializer` and `CandidatesGridDeserializer` — wire format `{"rows": [[[...],...],...]}`  (@spec DT-CGRID-002, DT-CGRID-003)

### 2.2 — Board integration
- [x] Update `Board.fromGrid()` to accept `Grid` (@spec DT-BOARD-001)
- [x] Update `Board.toCandidatesGrid()` to return `CandidatesGrid` (@spec DT-BOARD-002, SL-PROC-003)
- [x] Update `BoardTest.java` — `@spec` annotations updated

### 2.3 — PuzzleGenerator public API
- [x] Update `PuzzleGenerator.solveGrid(Grid)` → `Optional<Grid>` (@spec DT-GEN-001)
- [x] Update `PuzzleGenerator.countSolutions(Grid)` (@spec DT-GEN-002)
- [x] Update `PuzzleGenerator.PuzzleResult` to use `Grid` for both fields (@spec DT-GEN-003)
- [x] Keep all internal `int[][]` and local `List<List<Integer>>` variables unchanged (@spec DT-GEN-004)

### 2.4 — SudokuService interface + impl
- [x] Update `SudokuService.solveGrid(Grid)` → `Optional<Grid>` (@spec DT-SVC-001)
- [x] Update `SudokuService.hasSingleSolution(Grid)` (@spec DT-SVC-002)
- [x] Update `SudokuServiceImpl` to match

### 2.5 — DTO updates
- [x] Update `BoardRequest` fields `currentGrid`, `solutionGrid` to `Grid` (@spec DT-DTO-001)
- [x] Update `PuzzleResponse` fields `originalGrid`, `solutionGrid` to `Grid` (@spec DT-DTO-002)
- [x] Update `CreateGameFromGridRequest` field `originalGrid` to `Grid` (@spec DT-DTO-003)
- [x] Update `GameUpdateRequest` — `currentGrid` to `Grid`, `candidates` to `CandidatesGrid` (@spec DT-DTO-004)
- [x] Update `GameState` — `originalGrid`, `solutionGrid`, `currentGrid` to `Grid`, `candidates` to `CandidatesGrid`, `solutionGrid` non-nullable (@spec DT-DTO-005, DT-DTO-007)
- [x] Update `CandidatesResponse` field `candidatesGrid` to `CandidatesGrid` (@spec DT-DTO-006)

### 2.6 — GameService + repository
- [x] Update `GameService.createGameFromExistingGrid(String userId, Grid grid)` (@spec DT-SVC-003)
- [x] Update `GameServiceImpl` to use `Grid` / `CandidatesGrid` throughout
- [x] Update `DynamoDbGameRepository.findById()` to throw `GameNotFoundException` instead of returning empty Optional (@spec AEH-EX-008)
- [x] Update `DynamoDbGameRepository.update()` to throw `GameNotFoundException` instead of silently no-oping (@spec AEH-EX-009)
- [x] Update `GameItem.toGameState()` and `GameItem.from()` to wrap/unwrap `Grid` and `CandidatesGrid`

### 2.7 — HintDemoGrids
- [x] Update internal map to `Map<String, Grid>` — wrap parsed list into `Grid` at load time

### 2.8 — MockSudokuService + test data
- [x] Update `MockSudokuService` hardcoded grids to `Grid` type
- [x] Update all test fixtures and `@spec` annotations in affected test files

### 2.9 — Phase 2 backend verification
- [x] Run `./mvnw test` — all tests pass (211 tests, 0 failures)
- [x] Confirm `PuzzleResourceTest`, `GameResourceTest`, `GameServiceImplTest` all pass

---

## Phase 3 — Frontend wire adapter

### 3.1 — Grid adapters utility
- [x] Create `ui/src/utils/gridAdapters.js` with `gridFromWire`, `gridToWire`, `candidatesFromWire`, `candidatesToWire` (@spec DT-UI-001 to DT-UI-004)

### 3.2 — sudokuApi.js updates
- [x] Wrap all outbound grid fields with `gridToWire` / `candidatesToWire` before serialization (@spec DT-UI-005)
- [x] Unwrap all inbound grid responses with `gridFromWire` / `candidatesFromWire` after parsing (@spec DT-UI-006)
- [x] Update error handler to read `errorBody.message` with fallback to `errorBody.error` (@spec DT-UI-008)
- [x] Confirm internal hook state remains as plain array-of-arrays (@spec DT-UI-007)

### 3.3 — Canned mock data
- [x] Update all grid fields in `cannedData.js` to `{rows: [...]}` wire format (@spec DT-UI-009)

### 3.4 — Phase 3 verification
- [x] Run `npm run test:unit` — all 76 tests pass
- [x] Run `npm run lint` — no errors (pre-existing exhaustive-deps warning only)
- [ ] Smoke-test in dev mode (`VITE_MOCK_API=true`): new game loads, save works, candidates display correctly

---

## Phase 4 — LLD and arrow updates

- [x] Create `docs/llds/api-error-handling.md` (new LLD)
- [x] Update `docs/llds/sudoku-logic.md` — `Grid`, `CandidatesGrid`, updated `Board.fromGrid()` and `toCandidatesGrid()` signatures; remove "Technical Debt" bullet about `toCandidatesGrid()` returning verbose type
- [x] Update `docs/llds/puzzle-generation-validation.md` — all DTO and service interface type changes
- [x] Update `docs/llds/game-lifecycle.md` — `Grid`/`CandidatesGrid` in DTOs, `GameNotFoundException`, non-nullable `solutionGrid`, `DuplicateDigitsException` etc., remove "Technical Debt" bullets now resolved
- [x] Update `docs/llds/react-frontend.md` — `gridAdapters.js`, updated error handling in `apiFetch`
- [x] Mark all `[ ]` specs `[x]` in `domain-types-specs.md` and `api-error-handling-specs.md`
- [x] Mark updated specs `[x]` in `game-lifecycle-specs.md` and `sudoku-logic-specs.md`
- [x] Add new arrow `api-error-handling` to `docs/arrows/index.yaml`
- [x] Create `docs/arrows/api-error-handling.md` arrow doc
- [x] Update `sudoku-logic`, `puzzle-generation`, `game-lifecycle`, `react-frontend` arrow docs to reflect resolved tech debt and type changes

---

## Commit strategy

One commit per phase keeps the diff reviewable:

| Commit | Contents |
|---|---|
| `feat: error envelope, exception hierarchy, mappers` | Phase 1 — no wire format change |
| `feat: Grid and CandidatesGrid domain types (backend)` | Phase 2 — wire format change, backend only |
| `feat: Grid wire adapter and error handling (frontend)` | Phase 3 — frontend catches up to Phase 2 wire format |
| `docs: LLD and arrow updates for domain types refactor` | Phase 4 — docs only |

**Note:** Phase 2 and Phase 3 must be deployed together — the wire format change is a breaking API contract change. Do not deploy Phase 2 without Phase 3.
