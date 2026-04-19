# Arrow: Sudoku Logic

Pure domain model — Board, Cell, candidate calculation, geometry utilities.

## Status

**OK** - 2026-04-19. All 15 active specs implemented.

## References

### HLD
- docs/high-level-design.md — "Component Map" and "Dependency Order" sections

### LLD
- docs/llds/sudoku-logic.md

### EARS
- docs/specs/sudoku-logic-specs.md (15 specs, all [x])
- docs/specs/domain-types-specs.md — DT-BOARD-001/002 implemented (Board.fromGrid(Grid), toCandidatesGrid() → CandidatesGrid)

### Tests
- backend/src/test/java/com/sudoku/domain/BoardTest.java — covers SL-DATA-001 to 005, SL-PROC-001 to 003; @spec annotations added
- backend/src/test/java/com/sudoku/domain/CellTest.java — covers SL-DATA-006 to 008; @spec annotations added

### Code
- backend/src/main/java/.../domain/SudokuConstants.java
- backend/src/main/java/.../domain/Cell.java
- backend/src/main/java/.../domain/Board.java
- backend/src/main/java/.../domain/Grid.java
- backend/src/main/java/.../domain/CandidatesGrid.java
- backend/src/main/java/.../puzzle/hint/BoardUtils.java

## Architecture

**Purpose:** Immutable grid structure with mutable cell state. Provides the foundational data model and candidate computation used by all solving and validation logic.

**Key Components:**
1. `SudokuConstants` — shared numeric constants (UNIT_SIZE=9, BOX_SIZE=3, etc.)
2. `Board` — 9×9 grid, factory construction with validation, unit accessors, candidate calculation
3. `Cell` — mutable cell with value + candidate set; setValue() clears candidates
4. `BoardUtils` — stateless geometry helpers (candidateColumnsInRow, candidateRowsInColumn, isVisible)

## EARS Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
| --- | --- | --- | --- | --- |
| Board Construction | SL-DATA-001 to 005 | 5 | 0 | 0 |
| Cell Model | SL-DATA-006 to 008 | 3 | 0 | 0 |
| Candidate Calculation | SL-PROC-001 to 003 | 3 | 0 | 0 |
| Geometry Utilities | SL-PROC-004 to 006 | 3 | 0 | 0 |
| Board Integration | DT-BOARD-001 to 002 | 2 | 0 | 0 |

**Summary:** 16 of 16 active specs implemented; 0 deferred; 0 gaps.

## Key Findings

1. **Mutable Cell, immutable Board topology** — Board topology fixed at construction; cell values and candidates evolve. This is intentional for performance (hint strategies mutate candidates in place).
2. **`setValue()` side effect** — Clears candidates on placement. Callers must not rely on candidates after placing a value.
3. **Duplicate constraint logic** — `Board.calculateAllCandidates()` and `PuzzleGenerator.isPlaceable()` both implement Sudoku constraint checking independently on different data structures. A change to constraint semantics requires updates in both places. Note: the separate duplicate-detection concern (row/col/block violation reporting) has been consolidated into `BoardUtils.findDuplicatesInBoard()`.

## Work Required

### Done
1. ~~`Board.fromGrid()` throws `IllegalArgumentException`~~ — Introduced `InvalidGridException` in `com.sudoku.domain`; `fromGrid()` now throws it directly. (SL-DATA-003, SL-DATA-004)
2. ~~`Board.fromGrid(List<List<Integer>>)`~~ — Signature changed to `Board.fromGrid(Grid)`. (DT-BOARD-001)
3. ~~`toCandidatesGrid()` returns `List<List<List<Integer>>>`~~ — Now returns `CandidatesGrid`. (DT-BOARD-002, SL-PROC-003)
