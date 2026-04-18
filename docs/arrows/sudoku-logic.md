# Arrow: Sudoku Logic

Pure domain model — Board, Cell, candidate calculation, geometry utilities.

## Status

**MAPPED** - 2026-04-18. All domain classes read and documented. No tests audited yet.

## References

### HLD
- docs/high-level-design.md — "Component Map" and "Dependency Order" sections

### LLD
- docs/llds/sudoku-logic.md

### EARS
- docs/specs/sudoku-logic-specs.md (15 specs, all [x])

### Tests
- backend/src/test/java/.../domain/ (not yet audited)

### Code
- backend/src/main/java/.../domain/SudokuConstants.java
- backend/src/main/java/.../domain/Cell.java
- backend/src/main/java/.../domain/Board.java
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

**Summary:** 14 of 14 active specs implemented; 0 deferred; 0 gaps.

## Key Findings

1. **Mutable Cell, immutable Board topology** — Board topology fixed at construction; cell values and candidates evolve. This is intentional for performance (hint strategies mutate candidates in place).
2. **`setValue()` side effect** — Clears candidates on placement. Callers must not rely on candidates after placing a value.
3. **Duplicate constraint logic** — `Board.calculateAllCandidates()` and `PuzzleGenerator.isPlaceable()` both implement Sudoku constraint checking independently on different data structures. A change to constraint semantics requires updates in both places.

## Work Required

### Should Fix
1. `Board.fromGrid()` throws `IllegalArgumentException`; callers wrap it in `InvalidPuzzleException`. A domain-level `InvalidGridException` from `fromGrid()` would eliminate one translation step. (SL-DATA-003, SL-DATA-004)
