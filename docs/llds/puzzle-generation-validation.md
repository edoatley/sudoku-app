# Puzzle Generation & Validation

**Created**: 2026-04-18
**Status**: Complete

## Context and Current State

This component wraps the stateless puzzle operations exposed to the outside world: generating new puzzles, validating player grids, computing candidates, and serving developer demo grids. It sits between the Hint Engine / Sudoku Logic layer and the REST boundary.

Files: `puzzle/PuzzleGenerator.java`, `puzzle/PuzzleResource.java`, `puzzle/developer/DevResource.java`, `puzzle/developer/HintDemoGrids.java`, `puzzle/developer/MockSudokuService.java`, and the DTO records in `dto/`.

All operations are **stateless** — no game state is created or modified here. The Game Lifecycle component owns state.

## Puzzle Generation Algorithm

`PuzzleGenerator` uses a two-phase approach:

### Phase 1: Fill a Complete Valid Solution

`fillBoard(int[][] grid)` — randomized recursive backtracking:

```text
findEmpty(grid) → [row, col]
  if none → board is complete, return true

shuffled(1..9) → random digit order
  for each digit:
    if isPlaceable(grid, row, col, digit):
      place digit
      if fillBoard(grid) → return true
      backtrack (zero the cell)

return false  (no valid digit — caller must backtrack)
```

Randomization of digit order is what produces a different solution each call. `Random` is initialized once at startup (not per-request) for SnapStart compatibility.

### Phase 2: Dig Holes to Target Clue Count

`digHoles(int[][] grid, int targetClues)`:

```text
positions = allPositions() shuffled randomly

for each position [r, c]:
  if filledCells == targetClues → stop

  save = grid[r][c]
  grid[r][c] = 0

  if countSolutions(grid) == 1 → keep hole (filledCells--)
  else → restore grid[r][c] = save
```

Each removal is tested for uniqueness before committing. The short-circuit uniqueness counter caps at 2, so it never explores more solutions than necessary.

### Target Clue Counts by Difficulty

| Difficulty | Target Clues | Empty Cells |
| --- | --- | --- |
| Easy | 36 | 45 |
| Medium | 30 | 51 |
| Hard | 25 | 56 |
| Expert | 22 | 59 |

These are targets, not guarantees — if the solver cannot remove a clue without creating ambiguity, it skips that cell and moves on. The final puzzle may have slightly more clues than the target.

### Uniqueness Counting

`countSolutions(int[][] grid, int count)` — recursive, short-circuits at 2:

```text
if count >= 2 → return immediately (already ambiguous)
findEmpty(grid) → if none → return count + 1 (found a solution)

for each digit 1-9:
  if isPlaceable → place, recurse, backtrack
  if count >= 2 → break

return count
```

Used in two places:

- `digHoles()` — to verify each hole removal keeps uniqueness
- `countSolutions(List<List<Integer>>)` public API — for import validation

### `solveGrid()` Public API

Delegates to `fillBoard()` on a copy of the incoming grid. Accepts a `Grid` parameter and returns `Optional<Grid>` — empty if unsolvable.

## REST API

`PuzzleResource` exposes four stateless endpoints at `/puzzles`. No authentication required.

| Method | Path | Request | Response | Notes |
| --- | --- | --- | --- | --- |
| GET | `/puzzles/generate` | `?difficulty=medium` | `PuzzleResponse` | Default difficulty: medium |
| POST | `/puzzles/validate` | `BoardRequest` | `ValidationResponse` | Two validation modes (see below) |
| POST | `/puzzles/hint` | `BoardRequest` | `HintResponse` or 404 | 404 when no applicable strategy found |
| POST | `/puzzles/candidates` | `BoardRequest` | `CandidatesResponse` | Full pencil-mark grid |

### Validation Modes

Determined by whether `BoardRequest.solutionGrid` is present:

**With solution grid** (generated puzzles — solution always known):

- Cell-by-cell comparison against correct solution
- `errors` = coordinates where `current ≠ solution`
- Catches cells that are locally valid (no duplicates) but wrong

**Without solution grid** (imported puzzles — solution may not be stored):

- Duplicate detection across all 27 units (9 rows + 9 cols + 9 blocks)
- `errors` = coordinates of cells participating in any duplicate
- Cannot detect wrong-but-non-duplicate placements

In both modes:

- `isValid = errors.isEmpty()`
- `isSolved = isValid && no empty cells`

### hint() 404 Semantics

`hint()` returns 404 when `sudokuService.getHint()` returns `Optional.empty()`. This happens when either:

- No strategy applies (puzzle is already fully solvable without hints at current state)
- All applicable strategies are excluded by `minRank` / `excludedRanks`

The caller cannot distinguish these cases from the 404 alone.

## DTOs

All DTOs are immutable Java records.

### BoardRequest

The universal input for all puzzle operations:

| Field | Type | Nullable | Purpose |
| --- | --- | --- | --- |
| `currentGrid` | `Grid` | No | Player's current 9×9 grid (wire format: `{"rows": [...]}`) |
| `solutionGrid` | `Grid` | Yes | Known solution; enables precise validation |
| `minRank` | `Integer` | Yes | Skip hint strategies below this rank |
| `excludedRanks` | `List<Integer>` | Yes | Skip specific strategy ranks (already shown) |

Multiple convenience constructors exist (1-arg through 4-arg) to avoid passing nulls at call sites.

### PuzzleResponse

| Field | Type | Nullable | Purpose |
| --- | --- | --- | --- |
| `originalGrid` | `Grid` | No | Starting puzzle (wire format: `{"rows": [...]}`) |
| `solutionGrid` | `Grid` | No | Unique complete solution |
| `difficulty` | `String` | No | Label: "easy", "medium", "hard", "expert", or "demo" |
| `minRank` | `Integer` | Yes | Lowest strategy rank needed to solve (set by DevResource for demo grids, null otherwise) |

### Coordinate DTOs

| Record | Fields | Used In |
| --- | --- | --- |
| `Coordinate(row, col)` | int row, int col | `ValidationResponse.errors`, `HintResponse.highlightCells` |
| `CoordinateCandidate(row, col, value)` | int row, int col, int value | `HintResponse.eliminatedCandidates`, `HintResponse.focusCandidates` |
| `ActionableCell(row, col, value)` | int row, int col, int value | `HintResponse.solvedCells` |

`CoordinateCandidate` and `ActionableCell` have identical fields. They are distinct types because their semantic meaning differs: a `CoordinateCandidate` is a pencil mark (may or may not be placed), an `ActionableCell` is a cell whose digit is definitively known.

### ValidationResponse

```json
{
  "isValid": "boolean (no rule violations)",
  "isSolved": "boolean (valid and complete)",
  "errors": "Coordinate[] (conflicting cell positions)"
}
```

### CandidatesResponse

```json
{
  "candidatesGrid": "CandidatesGrid — wire format {\"rows\": [[[...],...],...]}; inner array = sorted valid digits; empty for placed cells"
}
```

## Developer Infrastructure

### DevResource (`/dev/hint-demo`)

Returns a pre-baked board where the named technique is immediately applicable, plus the technique's `minRank` so the hint engine skips simpler strategies:

```text
GET /dev/hint-demo?technique=naked-pair
→ PuzzleResponse {
    originalGrid: <board where naked pair is ready to apply>,
    solutionGrid: null,
    difficulty: "demo",
    minRank: 30   ← naked-pair rank, so hint engine starts there
  }
```

Slug-to-rank resolution uses a name-matching convention: `"naked-pair"` → strip hyphens → `"nakedpair"` → match against lowercased class name `"nakedpairstrategy"` via `startsWith`. This is a fragile convention (see Tech Debt).

### HintDemoGrids

Loads all 11 demo grids from classpath JSON files at class initialization time (static initializer block):

- Resource path: `/developer/hint-demo-{slug}.json`
- JSON shape: `{ "slug": "...", "description": "...", "grid": [[...], ...] }`
- Stored as immutable `Map<String, Grid>` — raw list parsed then wrapped via `Grid.of()` at load time
- Throws `IllegalStateException` at startup if any file is missing or malformed

Adding a new demo grid requires only dropping a correctly-named JSON file in `src/main/resources/developer/` — no Java changes.

### MockSudokuService

Provides canned puzzle grids and a duplicate-based validator for use in testing without the full solver. Not CDI-injectable; used directly in test constructors. Contains three hardcoded grids (easy, medium, hard; expert reuses hard) stored as `Grid` constants.

## Observed Design Decisions

| Decision | Chosen | Alternatives Considered | Rationale |
| --- | --- | --- | --- |
| Two-phase generation (fill then dig) | Generate full solution, then remove clues | Constraint propagation from scratch | Backtracking from empty board is reliable; separation makes uniqueness checking straightforward |
| Uniqueness check on every hole removal | `countSolutions` after each removal | Batch removals, check at end | Ensures puzzle is always solvable with unique solution at every intermediate step |
| Short-circuit at 2 solutions | Stop counting once 2 found | Count all solutions | Ambiguity is binary; no need to count beyond 2 |
| Random initialized at startup | `new Random()` in no-arg constructor | `new Random()` per generate call | SnapStart: all initialization must happen before first invocation; per-call would break warm starts |
| Seeded Random test constructor | `PuzzleGenerator(Random random)` package-private | Mockito mock, subclass | Allows deterministic tests without CDI; package-private keeps it out of public API |
| PuzzleResource returns 404 for no hint | `Response.status(404)` | 204 No Content, 200 with null body | 404 is semantically "no hint exists"; 204 would be ambiguous with "hint found but empty" |
| Slug-to-rank matching via getSlug() map | `Map<String, HintStrategy>` from `getSlug()` | Class-name prefix convention | Robust to class renames; duplicate slug fails fast at startup |
| HintDemoGrids static initializer | Load all grids at class init | Lazy load per request | Fail-fast at startup if files missing; no per-request I/O overhead |

## Technical Debt & Inconsistencies

- `DevResource` builds a `Map<String, HintStrategy>` keyed by `getSlug()` at construction time. A duplicate slug throws `IllegalStateException` at startup. The old class-name prefix matching has been removed.
- `PuzzleGenerator.solveGrid()` reuses `fillBoard()` which randomizes digit order — meaning the returned solution is random among all valid solutions. For import validation this is fine (we only care if a solution exists), but it means two calls on the same incomplete grid can return different solutions.
- `MockSudokuService` implements validation logic that duplicates `SudokuServiceImpl`'s `validateByDuplicates` path. If the validation logic changes, the mock must be updated manually.
- `PuzzleResponse.minRank` is null for generated puzzles. The field exists only for the dev demo use case. A separate `DemoPuzzleResponse` would be cleaner but adds a type.
- `digHoles()` operates on a primitive `int[][]` internally and wraps/unwraps via `Grid` only at the public API boundary. The conversion methods add a small amount of boilerplate.

## Behavioral Quirks

- A generated "easy" puzzle may have more than 36 clues if the hole-digging pass cannot remove clues without creating ambiguity. This is rare but possible with certain solution configurations.
- `fillBoard()` used as a solver (via `solveGrid()`) will return an arbitrary valid solution, not necessarily the canonical one. For puzzles with a unique solution this is fine; for incomplete/invalid grids it returns one of the many solutions.
- `isPlaceable()` in `PuzzleGenerator` is independent of `Board.calculateAllCandidates()` — it's a raw constraint check on the primitive array. Both implement the same Sudoku rules but through different code paths.

## References

- `backend/src/main/java/.../puzzle/PuzzleGenerator.java`
- `backend/src/main/java/.../puzzle/PuzzleResource.java`
- `backend/src/main/java/.../puzzle/developer/DevResource.java`
- `backend/src/main/java/.../puzzle/developer/HintDemoGrids.java`
- `backend/src/main/java/.../puzzle/developer/MockSudokuService.java`
- `backend/src/main/java/.../game/InvalidPuzzleException.java`
- `backend/src/main/java/.../exception/InvalidPuzzleExceptionMapper.java`
- `backend/src/main/java/.../dto/` (all DTO records)
- `backend/src/main/java/.../domain/Grid.java`
- `backend/src/main/java/.../domain/CandidatesGrid.java`
- Depends on: Sudoku Logic (Board, Cell), Hint Engine (SudokuService, HintStrategy)
- Depended on by: Game Lifecycle (GameService uses generatePuzzle, hasSingleSolution, solveGrid)
