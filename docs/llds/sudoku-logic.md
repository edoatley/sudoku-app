# Sudoku Logic

**Created**: 2026-04-18
**Status**: Complete

## Context and Current State

The Sudoku Logic component is the pure mathematical core of the application. It has no I/O, no persistence, and no HTTP concerns — it models the rules of Sudoku and exposes operations on those models to the rest of the backend. All other backend components depend on this layer; it depends on nothing.

This component exists in `backend/src/main/java/*/domain/` and portions of `puzzle/` (specifically BoardUtils). The guiding principle is immutable-structure, mutable-state: the grid topology is fixed at construction time, but cell values and candidates evolve as the game progresses.

## Domain Model

### Constants (`SudokuConstants`)

Central definitions shared across the entire domain:

| Constant | Value | Meaning |
| --- | --- | --- |
| `UNIT_SIZE` | 9 | Rows, columns, and blocks all have 9 cells |
| `MIN_DIGIT` | 1 | Minimum valid digit |
| `MAX_DIGIT` | 9 | Maximum valid digit |
| `BOX_SIZE` | 3 | Each 3×3 block has a 3-cell edge |

### Cell

A `Cell` is a mutable model of a single position in the 9×9 grid. It holds:

- **row, col** — zero-based coordinates
- **value** — the placed digit (0 = empty)
- **candidates** — `Set<Integer>` of digits still possible at this position

Key behavioral invariant: when `setValue()` is called, the candidates set is cleared. This keeps the cell in a consistent state — a cell with a placed value has no remaining candidates.

`isEmpty()` returns `true` when `value == 0`.

### Board

`Board` is the 9×9 grid. The structure (which cell is at which coordinate) is fixed at construction; the cell states evolve.

**Factory:** `Board.fromGrid(List<List<Integer>>)` validates:

- Grid must be 9×9 (exactly 9 rows, each with exactly 9 columns)
- Each value must be in range [0, 9] (0 = empty)

If validation fails, an `IllegalArgumentException` is thrown.

**Internals:** Cells are stored in a `Cell[][]` array (9×9). The board exposes three unit views:

| Method | Returns |
| --- | --- |
| `getRow(i)` | All 9 cells in row i |
| `getColumn(i)` | All 9 cells in column i |
| `getBlock(i)` | All 9 cells in the i-th 3×3 block (row-major order: block 0=top-left, block 8=bottom-right) |
| `getCell(r,c)` | Single cell at (r,c) |

**Candidate calculation:** `calculateAllCandidates()` computes candidates for every empty cell by eliminating digits already present in the cell's row, column, and block. This is called before any hint strategy runs.

**Serialization:** `toCandidatesGrid()` converts the internal candidate sets to `List<List<List<Integer>>>` (9×9 outer, inner list of candidate digits) for REST response serialization.

### BoardUtils

Stateless geometry helpers used by hint strategies:

| Method | Purpose |
| --- | --- |
| `candidateColumnsInRow(board, digit, row)` | Returns column indices in `row` where `digit` is still a candidate |
| `candidateRowsInColumn(board, digit, col)` | Returns row indices in `col` where `digit` is still a candidate |
| `isVisible(a, b)` | True if cells `a` and `b` share a row, column, or 3×3 block |

## Board Geometry

Blocks are numbered 0–8 in row-major order:

```text
┌───────┬───────┬───────┐
│ 0 0 0 │ 1 1 1 │ 2 2 2 │
│ 0 0 0 │ 1 1 1 │ 2 2 2 │
│ 0 0 0 │ 1 1 1 │ 2 2 2 │
├───────┼───────┼───────┤
│ 3 3 3 │ 4 4 4 │ 5 5 5 │
│ 3 3 3 │ 4 4 4 │ 5 5 5 │
│ 3 3 3 │ 4 4 4 │ 5 5 5 │
├───────┼───────┼───────┤
│ 6 6 6 │ 7 7 7 │ 8 8 8 │
│ 6 6 6 │ 7 7 7 │ 8 8 8 │
│ 6 6 6 │ 7 7 7 │ 8 8 8 │
└───────┴───────┴───────┘
```

Block index formula: `blockIndex = (row / BOX_SIZE) * BOX_SIZE + (col / BOX_SIZE)`

Coordinates are zero-based throughout the entire system. This is enforced at the domain boundary — the REST layer never re-indexes.

## Observed Design Decisions

| Decision | Chosen | Alternatives Considered | Rationale |
| --- | --- | --- | --- |
| Mutable Cell, immutable Board structure | Board topology fixed at construction; cell values/candidates mutable | Fully immutable (copy-on-write) | Hint strategies iterate and mutate candidates in place; copy-on-write would be expensive for the 81-cell grid |
| Candidates as `Set<Integer>` on Cell | Each cell owns its candidate set | Separate candidates array, external candidates map | Keeps related data co-located; Cell is the natural owner |
| `setValue()` clears candidates | Side effect built into setter | Caller responsibility | Invariant enforcement — a solved cell must have no candidates |
| Zero-based coordinates throughout | All (row, col) are 0-indexed | 1-indexed (user-facing) | Simplifies array indexing; REST layer serializes the same values |
| Static factory `fromGrid()` with validation | Throws `IllegalArgumentException` on bad input | Separate validator class, Optional return | Fail-fast at boundary; callers (PuzzleGenerator, REST) catch and translate |
| `BoardUtils` as stateless helper class | Static methods in separate class | Methods on Board, utility methods on Cell | Keeps Board focused on state; geometry helpers used only by hint strategies |

## Technical Debt & Inconsistencies

- `Board.fromGrid()` throws `IllegalArgumentException` but the game layer catches and wraps it in `InvalidPuzzleException`. The exception type mismatch is functional but inelegant.
- Candidate calculation runs on every hint request (full recalculation). There is no caching — acceptable for serverless (stateless per-request) but worth noting.
- `toCandidatesGrid()` returns a `List<List<List<Integer>>>` which is verbose to work with in callers.

## Behavioral Quirks

- `getBlock(i)` returns cells in row-major order within the block (left-to-right, top-to-bottom). Hint strategies that need block cells rely on this order being stable.
- An empty board (all zeros) is valid from `fromGrid()`'s perspective — validation of puzzle-quality (min clues, unique solution) is handled in the Puzzle Generation component.

## References

- `backend/src/main/java/.../domain/SudokuConstants.java`
- `backend/src/main/java/.../domain/Cell.java`
- `backend/src/main/java/.../domain/Board.java`
- `backend/src/main/java/.../puzzle/hint/BoardUtils.java`
- Depends on: nothing
- Depended on by: Hint Engine, Puzzle Generation & Validation
