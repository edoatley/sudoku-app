# Arrow: Domain Types

Shared wire-format types (`Grid`, `CandidatesGrid`) and the refactor of all public API boundaries to use them instead of raw `List<List<Integer>>` / `List<List<List<Integer>>>`.

## Status

**OK** - 2026-04-22. All 36 specs implemented.

## References

### HLD
- docs/high-level-design.md — "Component Map" and "Data Flow" sections

### LLD
- docs/llds/sudoku-logic.md — Grid and CandidatesGrid type definitions

### EARS
- docs/specs/domain-types-specs.md (36 specs, all [x])

### Tests
- backend/src/test/java/com/sudoku/domain/GridTest.java — DT-GRID-001 to 006
- backend/src/test/java/com/sudoku/domain/CandidatesGridTest.java — DT-CGRID-001 to 005
- ui/src/api/sudokuApi.test.js — DT-UI-001 to 009

### Code
- backend/src/main/java/.../domain/Grid.java
- backend/src/main/java/.../domain/CandidatesGrid.java
- backend/src/main/java/.../domain/Board.java — DT-BOARD-001/002
- backend/src/main/java/.../puzzle/PuzzleGenerator.java — DT-GEN-001 to 004
- backend/src/main/java/.../service/SudokuService.java — DT-SVC-001/002
- backend/src/main/java/.../service/GameService.java — DT-SVC-003
- backend/src/main/java/.../dto/ — DT-DTO-001 to 007
- ui/src/api/sudokuApi.js — DT-UI-001 to 009
- ui/src/data/cannedData.js — DT-UI-009

## Architecture

**Purpose:** Replace raw nested-list types with named records at all public boundaries so the wire format (`{"rows": [...]}`) is explicit and refactoring-safe.

**Key Components:**
1. `Grid` — record wrapping `List<List<Integer>> rows`; accessors `row(i)`, `cell(r,c)`, factory `of(...)`
2. `CandidatesGrid` — record wrapping `List<List<List<Integer>>> rows`; accessor `candidates(r,c)`, factory `of(...)`
3. Frontend wire adapters — `gridFromWire/gridToWire`, `candidatesFromWire/candidatesToWire` in `sudokuApi.js`

## EARS Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
| --- | --- | --- | --- | --- |
| Grid Type | DT-GRID-001 to 006 | 6 | 0 | 0 |
| CandidatesGrid Type | DT-CGRID-001 to 005 | 5 | 0 | 0 |
| Board Integration | DT-BOARD-001 to 002 | 2 | 0 | 0 |
| Service Interface Boundaries | DT-SVC-001 to 003 | 3 | 0 | 0 |
| DTO Boundaries | DT-DTO-001 to 007 | 7 | 0 | 0 |
| PuzzleGenerator Public API | DT-GEN-001 to 004 | 4 | 0 | 0 |
| Frontend Wire Adapter | DT-UI-001 to 007 | 7 | 0 | 0 |
| Frontend Error Handling | DT-UI-008 to 009 | 2 | 0 | 0 |

**Summary:** 36 of 36 specs implemented; 0 deferred; 0 gaps.

## Key Findings

1. **Wire format vs internal state** — `Grid` and `CandidatesGrid` exist at API boundaries only; hooks and internal logic retain plain array-of-arrays. This boundary is enforced by the wire adapters in `sudokuApi.js`.
2. **Internal `PuzzleGenerator` algorithm unchanged** — DT-GEN-004 explicitly scopes the change to public API only; internal `int[][]` workspaces were not touched.
3. **Error body compatibility** — `apiFetch` reads `errorBody.message` first, falling back to `errorBody.error` for backwards compatibility with older Lambda responses.
