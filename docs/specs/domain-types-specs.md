# Domain Types — EARS Specifications

## Grid Type

- [x] **DT-GRID-001**: The system shall define a `Grid` record in `com.sudoku.domain` wrapping a `List<List<Integer>>` as its `rows` field.
- [x] **DT-GRID-002**: The system shall serialize `Grid` over the wire as `{"rows": [[...], ...]}` — a JSON object with a single `rows` key containing a 9×9 array.
- [x] **DT-GRID-003**: The system shall deserialize a JSON object with a `rows` key into a `Grid` instance.
- [x] **DT-GRID-004**: `Grid` shall provide a `row(int i)` accessor returning the list of integers for row `i`.
- [x] **DT-GRID-005**: `Grid` shall provide a `cell(int r, int c)` accessor returning the integer value at row `r`, column `c`.
- [x] **DT-GRID-006**: `Grid` shall provide a static `of(List<List<Integer>>)` factory method.

## CandidatesGrid Type

- [x] **DT-CGRID-001**: The system shall define a `CandidatesGrid` record in `com.sudoku.domain` wrapping a `List<List<List<Integer>>>` as its `rows` field.
- [x] **DT-CGRID-002**: The system shall serialize `CandidatesGrid` over the wire as `{"rows": [[[...], ...], ...]}` — a JSON object with a single `rows` key containing a 9×9 array of candidate lists.
- [x] **DT-CGRID-003**: The system shall deserialize a JSON object with a `rows` key into a `CandidatesGrid` instance.
- [x] **DT-CGRID-004**: `CandidatesGrid` shall provide a `candidates(int r, int c)` accessor returning the list of candidate integers for the cell at row `r`, column `c`.
- [x] **DT-CGRID-005**: `CandidatesGrid` shall provide a static `of(List<List<List<Integer>>>)` factory method.

## Board Integration

- [x] **DT-BOARD-001**: `Board.fromGrid()` shall accept a `Grid` parameter instead of `List<List<Integer>>`.
- [x] **DT-BOARD-002**: `Board.toCandidatesGrid()` shall return `CandidatesGrid` instead of `List<List<List<Integer>>>`.

## Service Interface Boundaries

- [x] **DT-SVC-001**: `SudokuService.solveGrid()` shall accept a `Grid` parameter and return `Optional<Grid>`.
- [x] **DT-SVC-002**: `SudokuService.hasSingleSolution()` shall accept a `Grid` parameter.
- [x] **DT-SVC-003**: `GameService.createGameFromExistingGrid()` shall accept a `Grid` parameter instead of `List<List<Integer>>`.

## DTO Boundaries

- [x] **DT-DTO-001**: `BoardRequest` fields `currentGrid` and `solutionGrid` shall use type `Grid`.
- [x] **DT-DTO-002**: `PuzzleResponse` fields `originalGrid` and `solutionGrid` shall use type `Grid`.
- [x] **DT-DTO-003**: `CreateGameFromGridRequest` field `originalGrid` shall use type `Grid`.
- [x] **DT-DTO-004**: `GameUpdateRequest` field `currentGrid` shall use type `Grid`; field `candidates` shall use type `CandidatesGrid`.
- [x] **DT-DTO-005**: `GameState` fields `originalGrid`, `solutionGrid`, and `currentGrid` shall use type `Grid`; field `candidates` shall use type `CandidatesGrid`.
- [x] **DT-DTO-006**: `CandidatesResponse` field `candidatesGrid` shall use type `CandidatesGrid`.
- [x] **DT-DTO-007**: `GameState.solutionGrid` shall be non-nullable — every persisted game shall have a solution.

## PuzzleGenerator Public API

- [x] **DT-GEN-001**: `PuzzleGenerator.solveGrid()` shall accept a `Grid` and return `Optional<Grid>`.
- [x] **DT-GEN-002**: `PuzzleGenerator.countSolutions()` shall accept a `Grid`.
- [x] **DT-GEN-003**: `PuzzleGenerator.PuzzleResult` shall use `Grid` for both `puzzle` and `solution` fields.
- [x] **DT-GEN-004**: Internal algorithm variables within `PuzzleGenerator` (including `int[][]` arrays and local `List<List<Integer>>` variables) shall not be changed — the scope of the type change is public API boundaries only.

## Frontend Wire Adapter

- [x] **DT-UI-001**: The frontend shall provide `gridFromWire(wireGrid)` converting `{rows: [...]}` to a plain array-of-arrays.
- [x] **DT-UI-002**: The frontend shall provide `gridToWire(grid)` converting a plain array-of-arrays to `{rows: [...]}`.
- [x] **DT-UI-003**: The frontend shall provide `candidatesFromWire(wireCandidates)` converting `{rows: [...]}` to a plain array-of-arrays.
- [x] **DT-UI-004**: The frontend shall provide `candidatesToWire(candidates)` converting a plain array-of-arrays to `{rows: [...]}`.
- [x] **DT-UI-005**: All outbound API calls in `sudokuApi.js` that send grid data shall wrap the grid using `gridToWire` or `candidatesToWire` before serialization.
- [x] **DT-UI-006**: All inbound API responses in `sudokuApi.js` that contain grid data shall unwrap using `gridFromWire` or `candidatesFromWire` after parsing.
- [x] **DT-UI-007**: Internal hook state (`currentGrid`, `candidateGrid`, `originalGrid`, `solutionGrid`) shall remain as plain array-of-arrays — the `Grid` wire format shall not propagate into component state.

## Frontend Error Handling

- [x] **DT-UI-008**: The `apiFetch` error handler in `sudokuApi.js` shall read `errorBody.message` as the primary error field, falling back to `errorBody.error` for backwards compatibility.
- [x] **DT-UI-009**: Mock canned data in `cannedData.js` shall wrap all grid fields in `{rows: [...]}` to match the new wire format.
