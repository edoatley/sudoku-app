# Hint Engine

**Created**: 2026-04-18
**Status**: Complete

## Context and Current State

The Hint Engine is the most intellectually complex component in the system. It implements 11 Sudoku solving techniques, from elementary deductions to advanced multi-line patterns. Given a board state, it finds the simplest applicable technique and returns a structured hint that can be revealed progressively (nudge → focus → reveal) in the UI.

The engine lives in `backend/src/main/java/.../puzzle/hint/` (strategies) and `puzzle/SudokuServiceImpl.java` (orchestrator). It depends on the Sudoku Logic component (Board, Cell, BoardUtils) and nothing else.

## Strategy Chain Architecture

`SudokuServiceImpl` is a CDI `@ApplicationScoped` bean that discovers all `HintStrategy` implementations at startup via `Instance<HintStrategy>`. On construction it:

1. Streams all CDI-discovered strategy beans
2. Sorts by `getDifficultyRank()` ascending (easiest first)
3. Stores as an immutable `List<HintStrategy>`

This means adding a new strategy requires only implementing `HintStrategy` and annotating it `@ApplicationScoped` — no registration step, no factory change.

## getHint() Flow

```text
BoardRequest (currentGrid, minRank?, excludedRanks?)
        │
        ▼
Board.fromGrid(currentGrid)
        │
        ▼
board.calculateAllCandidates()   ← full recalculation, no caching
        │
        ▼
for each strategy in rank order:
  ├── skip if rank < minRank
  ├── skip if rank in excludedRanks
  └── strategy.evaluate(board)
          ├── Optional.empty() → try next
          └── HintResponse with action → return immediately
        │
        ▼
Optional<HintResponse>  (empty = no hint found, puzzle may be solved)
```

A hint is only returned if it has **action**: either `eliminatedCandidates` or `solvedCells` must be non-empty. Strategies that find a pattern but produce no actionable output are skipped.

`minRank` and `excludedRanks` exist to support the "Try Different Hint" UX — the frontend excludes ranks already shown so the user gets variety.

## The 11 Strategies

### Difficulty Tiers

| Tier | Ranks | Strategies |
| --- | --- | --- |
| EASY | 10–40 | Full House, Naked Single, Hidden Single |
| MEDIUM | 30–70 | Naked Pair, Pointing Pair, Naked Triple, Hidden Pair |
| HARD | 80–110 | Hidden Triple, X-Wing, Swordfish, Y-Wing |

Note: Naked Pair (rank 30) falls in the MEDIUM enum but ranks below Hidden Single (rank 40). The rank ordering is authoritative for hint selection; the difficulty label is informational.

### Strategy Reference Table

| Strategy | Rank | Difficulty | Slug | Pattern |
| --- | --- | --- | --- | --- |
| Full House | 10 | EASY | `full-house` | Unit has 1 empty cell → only missing digit goes there |
| Naked Single | 20 | EASY | `naked-single` | Cell has exactly 1 candidate remaining |
| Naked Pair | 30 | MEDIUM | `naked-pair` | Two cells in a unit share identical 2-candidate set → eliminate from rest of unit |
| Hidden Single | 40 | EASY | `hidden-single` | Digit appears as candidate in exactly 1 cell of a unit → must go there |
| Pointing Pair | 50 | MEDIUM | `pointing-pair` | All candidates for a digit within a block lie in one row/col → eliminate from rest of that line |
| Naked Triple | 60 | MEDIUM | `naked-triple` | Three cells in a unit share a combined 3-candidate set → eliminate from rest of unit |
| Hidden Pair | 70 | MEDIUM | `hidden-pair` | Two digits confined to same two cells in a unit → eliminate other candidates from those cells |
| Hidden Triple | 80 | HARD | `hidden-triple` | Three digits confined to same three cells in a unit → eliminate other candidates from those cells |
| X-Wing | 90 | HARD | `x-wing` | Digit confined to exactly 2 cells in each of 2 rows, forming a rectangle → eliminate from rest of those columns (and vice versa) |
| Swordfish | 100 | HARD | `swordfish` | 3-row/col generalization of X-Wing: digit spans 2–3 cells across 3 base lines confined to exactly 3 cover lines |
| Y-Wing | 110 | HARD | `y-wing` | Pivot with 2 candidates sees two pincers sharing a common third candidate → eliminate that candidate from cells visible to both pincers |

### Strategy Algorithms in Detail

**Full House (rank 10)**
Scans all 27 units (9 rows + 9 cols + 9 blocks). When a unit has exactly 1 empty cell, the missing digit is determined by set subtraction from {1–9}. Returns immediately on first match.

**Naked Single (rank 20)**
Iterates all 81 cells in row-major order. Returns on the first empty cell with exactly 1 candidate.

**Naked Pair (rank 30)**
For each unit, finds all cells with exactly 2 candidates. Tests all (i,j) pairs for identical candidate sets. If found, eliminates both digits from every other cell in the unit. Returns if at least one elimination exists.

**Hidden Single (rank 40)**
For each unit and each digit 1–9, counts cells where the digit appears as a candidate. When count == 1, that cell is forced. Returns on first match.

**Pointing Pair (rank 50)**
Iterates the 9 blocks. For each digit, finds all candidate cells within the block. If all share the same row → eliminate digit from that row outside the block. If all share the same column → eliminate from that column outside the block. Returns on first elimination.

**Naked Triple (rank 60)**
For each unit, collects cells with 1–3 candidates. Tests all triples (i < j < k). Union of candidates must be exactly 3 digits. Eliminates those 3 from other cells in the unit.

**Hidden Pair (rank 70)**
For each unit, tests all digit pairs (d1 < d2). Finds cells containing d1 AND d2 as candidates. If exactly 2 such cells exist for both digits, removes all other candidates from those 2 cells.

**Hidden Triple (rank 80)**
For each unit, tests all digit triples (d1 < d2 < d3). Finds all cells containing ANY of the three digits. If exactly 3 cells match AND all three digits appear at least once across them, removes non-triple candidates from those 3 cells.

**X-Wing (rank 90)**
Tests row-based and column-based modes for each digit. Row mode: finds rows with exactly 2 candidate columns, tests all row pairs with identical column sets, eliminates from other rows in those columns. Column mode mirrors this.

**Swordfish (rank 100)**
Extension of X-Wing to 3 base lines. Collects rows (or columns) with 2–3 candidate positions. Tests all triples. If union of their cross-positions is exactly 3, eliminates digit from other rows in those 3 columns (or vice versa).

**Y-Wing (rank 110)**
Collects all bi-value cells. For each pivot with candidates {A, B}, finds bi-value "pincers" visible to the pivot that each share one candidate with the pivot and introduce a common third candidate C. Any cell visible to both pincers can have C eliminated.

## HintResponse Structure

Every strategy returns the same response shape, with progressive disclosure:

| Field | nudge stage | focus stage | reveal stage |
| --- | --- | --- | --- |
| `techniqueName` | present | present | present |
| `markdownSlug` | present | present | present |
| `difficulty` | present | present | present |
| `strategyRank` | present | present | present |
| `nudge` | shown | shown | shown |
| `focus` | — | shown | shown |
| `reveal` | — | — | shown |
| `highlightCells` | — | shown | shown |
| `focusCandidates` | — | shown | shown |
| `eliminatedCandidates` | — | — | shown |
| `solvedCells` | — | — | shown |

The UI controls which fields to display based on the current stage — the full response is always sent, the frontend just reveals progressively.

**highlightCells** — always the cells most relevant to the pattern (e.g., the pair cells for Naked Pair, the four rectangle corners for X-Wing, pivot + pincers for Y-Wing).

**focusCandidates** — the specific candidate digits within highlight cells to draw attention to.

**eliminatedCandidates** — `List<CoordinateCandidate>` of (row, col, digit) triples to remove from pencil marks.

**solvedCells** — `List<ActionableCell>` of (row, col, value) triples for cells with a forced digit.

## Other SudokuService Operations

### getCandidates()

Constructs a `Board`, calls `calculateAllCandidates()`, serializes to `CandidatesResponse` via `toCandidatesGrid()`. Stateless per-request operation.

### validatePuzzle() — Two Modes

**With solution grid:**
 Cell-by-cell comparison. Reports `isValid=true` if no mismatches. Reports `isSolved=true` if valid AND no empty cells. Error coordinates are cells where current ≠ solution.

**Without solution grid:** Duplicate detection across all rows, columns, and blocks. Reports `isValid=true` if no duplicates. `isSolved=true` if no duplicates AND no empty cells. Error coordinates are cells containing duplicate values.

The distinction matters: mode 1 catches cells that are individually duplicate-free but wrong relative to the unique solution; mode 2 only catches structural rule violations.

### solveGrid()

Delegates to `PuzzleGenerator.solveGrid()` (backtracking solver). Returns `Optional<List<List<Integer>>>` — empty if no solution exists.

### hasSingleSolution()

Calls `PuzzleGenerator.countSolutions()` capped at 2. Returns `true` only if count == 1.

## Observed Design Decisions

| Decision | Chosen | Alternatives Considered | Rationale |
| --- | --- | --- | --- |
| CDI `Instance<HintStrategy>` discovery | All strategies auto-discovered at startup | Manual registry, factory pattern | Zero-registration extensibility — add a strategy, annotate it, done |
| Sort by rank at construction, not per-call | Sort once in constructor | Sort on every getHint() call | Immutable sorted list is safe and efficient in a stateless serverless environment |
| Full candidate recalculation per hint | `board.calculateAllCandidates()` every call | Candidate caching in request scope | Serverless: no shared state between requests; caching would require a request-scoped cache layer |
| Rank < minRank skip (not ≤) | `rank < minRank` skips lower ranks | `rank <= minRank` | Allows the strategy AT minRank to be tried; `>` would skip it |
| HintResponse always fully populated | All fields returned regardless of stage | Return only fields for current stage | Frontend owns stage progression; backend stays stateless |
| Dual validation modes | with-solution and without-solution paths | Always require solution, always use duplicate detection | Game context: generated puzzles always have a solution; imported puzzles might not store it |

## Technical Debt & Inconsistencies

- Naked Pair (rank 30) has a `MEDIUM` difficulty label but ranks below Hidden Single (rank 40, `EASY`). The ordering is intentional by the algorithm author but the enum label is misleading.
- `SudokuServiceImpl` has test-only constructors to inject mock strategies without CDI. This is functional but means the production code has non-production entry points. A test subclass would be cleaner.
- Strategy implementations repeat the unit-scanning loop (rows → columns → blocks) in each class. A shared `UnitScanner` abstraction would reduce duplication but doesn't exist yet.
- `getHint()` returns `Optional.empty()` both when the puzzle is already solved (no moves needed) and when no applicable strategy exists above `minRank`. Callers cannot distinguish these cases.

## Behavioral Quirks

- Y-Wing visibility uses `BoardUtils.isVisible()` which considers a cell visible to itself. Strategies must guard against pivot == pincer comparisons (they do, via index checks).
- Swordfish accepts base lines with **2 or 3** candidate positions (not strictly 2 like X-Wing). This is correct by the Sudoku rule but means a "degenerate" Swordfish where all three rows have exactly 2 candidates in the same 2 columns is valid — it subsumes an X-Wing. The engine would have found the X-Wing first at rank 90, so this is harmless.
- The `excludedRanks` list uses `List.contains()` which is O(n) per strategy check. With at most 11 strategies and typically <5 excluded ranks, this is negligible.

## References

- `backend/src/main/java/.../puzzle/SudokuService.java` (interface)
- `backend/src/main/java/.../puzzle/SudokuServiceImpl.java` (orchestrator)
- `backend/src/main/java/.../puzzle/hint/HintStrategy.java` (strategy interface)
- `backend/src/main/java/.../puzzle/hint/Difficulty.java`
- `backend/src/main/java/.../puzzle/hint/BoardUtils.java`
- `backend/src/main/java/.../puzzle/hint/*Strategy.java` (11 implementations)
- Depends on: Sudoku Logic (Board, Cell, BoardUtils)
- Depended on by: Puzzle Generation & Validation (PuzzleResource), Game Lifecycle (GameService)
