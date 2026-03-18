# TODO: Hint Strategy Implementation

## Implemented Strategies

| Rank | Strategy | Difficulty | Class |
|------|----------|------------|-------|
| 10 | Full House | Easy | `FullHouseStrategy.java` |
| 20 | Naked Single | Easy | `NakedSingleStrategy.java` |
| 30 | Naked Pair | Medium | `NakedPairStrategy.java` |

## Remaining Strategies

| Rank | Strategy | Difficulty | Concept |
|------|----------|------------|---------|
| 40 | Hidden Single | Easy | A digit can only go in one cell within a row, column, or block |
| 50 | Pointing Pair | Medium | Candidate in a block restricted to one row/column → eliminate from rest of that row/column |
| 60 | Naked Triple | Medium | Three cells in a unit share exactly the same three candidates |
| 70 | Hidden Pair | Medium | Two candidates appear in only two cells in a unit; remove other candidates from those cells |
| 80 | Hidden Triple | Hard | Three candidates restricted to exactly three cells in a unit |
| 90 | X-Wing | Hard | Candidate restricted to two cells in two rows sharing the same two columns |
| 100 | Swordfish | Hard | X-Wing extended to three rows/columns |
| 110 | Y-Wing | Hard | Three-cell chain with specific candidate relationships enabling eliminations |

---

## Per-Strategy Work

### Rank 40 — Hidden Single (`easy`)

**File:** `backend/src/main/java/com/sudoku/puzzle/hint/HiddenSingleStrategy.java`

A digit appears as a candidate in exactly one cell within a row, column, or block. That cell must contain that digit.

- Scan all rows, columns, and blocks
- For each unit, find digits that appear as a candidate in exactly one empty cell
- Return `solvedCells` with that cell and digit; `highlightCells` = the full unit for context
- `markdownSlug`: `"hidden-single"`

**Test:** `backend/src/test/java/com/sudoku/puzzle/hint/HiddenSingleStrategyTest.java`
- Detection: board where a digit has only one candidate cell in a unit
- No-match: board where every digit appears in 2+ cells in every unit
- Metadata: `getDifficultyRank() == 40`, `difficulty == "easy"`

---

### Rank 50 — Pointing Pair (`medium`)

**File:** `backend/src/main/java/com/sudoku/puzzle/hint/PointingPairStrategy.java`

Within a block, all candidates for a digit are confined to a single row or column. That digit can be eliminated from the rest of that row/column outside the block.

- For each block (0–8), for each digit 1–9:
  - Collect empty cells in the block that have the digit as a candidate
  - If all such cells share the same row → eliminate from that row outside the block
  - If all such cells share the same column → eliminate from that column outside the block
- Return `highlightCells` = the pointing cells; `eliminatedCandidates` = cells outside the block
- `markdownSlug`: `"pointing-pair"`

**Test:** `backend/src/test/java/com/sudoku/puzzle/hint/PointingPairStrategyTest.java`
- Detection (row): digit confined to one row within a block, eliminations exist in that row
- Detection (column): digit confined to one column within a block
- No-match: no digit is confined to a single row/column within any block
- Metadata: `getDifficultyRank() == 50`, `difficulty == "medium"`

---

### Rank 60 — Naked Triple (`medium`)

**File:** `backend/src/main/java/com/sudoku/puzzle/hint/NakedTripleStrategy.java`

Three empty cells in a unit collectively contain only the same three candidates (each cell may have 2 or 3 of those candidates). Eliminate those digits from all other cells in the unit.

- For each unit (row, column, block), find all combinations of three empty cells whose union of candidates has exactly three digits
- Eliminate those three digits from the remaining cells in the unit
- Return `highlightCells` = the three cells; `eliminatedCandidates` = removable candidates elsewhere
- `markdownSlug`: `"naked-triple"`

**Test:** `backend/src/test/java/com/sudoku/puzzle/hint/NakedTripleStrategyTest.java`
- Detection: three cells with candidates `{1,2}`, `{2,3}`, `{1,3}` and at least one elimination
- No-match: no triple produces any eliminations
- Metadata: `getDifficultyRank() == 60`, `difficulty == "medium"`

---

### Rank 70 — Hidden Pair (`medium`)

**File:** `backend/src/main/java/com/sudoku/puzzle/hint/HiddenPairStrategy.java`

Two digits each appear as candidates in exactly the same two cells within a unit. All other candidates can be removed from those two cells.

- For each unit, find pairs of digits that appear in exactly two cells, and those two cells are the same for both digits
- Eliminate all other candidates from those two cells
- Return `highlightCells` = the two cells; `eliminatedCandidates` = non-pair candidates in those cells
- `markdownSlug`: `"hidden-pair"`

**Test:** `backend/src/test/java/com/sudoku/puzzle/hint/HiddenPairStrategyTest.java`
- Detection: two digits confined to the same two cells with extra candidates to remove
- No-match: no hidden pair exists
- Metadata: `getDifficultyRank() == 70`, `difficulty == "medium"`

---

### Rank 80 — Hidden Triple (`hard`)

**File:** `backend/src/main/java/com/sudoku/puzzle/hint/HiddenTripleStrategy.java`

Three digits each appear as candidates in exactly three cells within a unit (the same three cells). Eliminate all other candidates from those three cells.

- For each unit, find combinations of three digits whose candidate cells are a subset of the same three empty cells
- Eliminate non-triple candidates from those three cells
- Return `highlightCells` = the three cells; `eliminatedCandidates` = removable candidates
- `markdownSlug`: `"hidden-triple"`

**Test:** `backend/src/test/java/com/sudoku/puzzle/hint/HiddenTripleStrategyTest.java`
- Detection: three digits confined to the same three cells with extra candidates
- No-match: no hidden triple exists
- Metadata: `getDifficultyRank() == 80`, `difficulty == "hard"`

---

### Rank 90 — X-Wing (`hard`)

**File:** `backend/src/main/java/com/sudoku/puzzle/hint/XWingStrategy.java`

A digit is a candidate in exactly two cells in each of two rows, and those cells fall in the same two columns. The digit can be eliminated from all other cells in those two columns (or symmetrically: two columns → eliminate from two rows).

- For each digit, find all rows where the digit appears in exactly two columns
- Find pairs of such rows sharing the same two columns
- Eliminate the digit from all other cells in those two columns
- Repeat with rows and columns swapped
- Return `highlightCells` = the four corner cells; `eliminatedCandidates` = eliminations in the two columns
- `markdownSlug`: `"x-wing"`

**Test:** `backend/src/test/java/com/sudoku/puzzle/hint/XWingStrategyTest.java`
- Detection (row-based): digit in exactly two columns per row, two rows match
- Detection (column-based): symmetric case
- No-match: no X-Wing pattern exists
- Metadata: `getDifficultyRank() == 90`, `difficulty == "hard"`

---

### Rank 100 — Swordfish (`hard`)

**File:** `backend/src/main/java/com/sudoku/puzzle/hint/SwordfishStrategy.java`

X-Wing extended to three rows: a digit appears in exactly two or three cells in each of three rows, and those cells span exactly three columns. Eliminate the digit from all other cells in those three columns.

- For each digit, find all rows where the digit appears in 2 or 3 cells
- Find triples of such rows whose candidate columns (union) contain exactly three columns
- Eliminate the digit from all other cells in those three columns
- Repeat with rows and columns swapped
- Return `highlightCells` = the defining cells (up to 9); `eliminatedCandidates` = eliminations
- `markdownSlug`: `"swordfish"`

**Test:** `backend/src/test/java/com/sudoku/puzzle/hint/SwordfishStrategyTest.java`
- Detection: digit spanning three rows with exactly three column positions
- No-match: no Swordfish pattern exists
- Metadata: `getDifficultyRank() == 100`, `difficulty == "hard"`

---

### Rank 110 — Y-Wing (`hard`)

**File:** `backend/src/main/java/com/sudoku/puzzle/hint/YWingStrategy.java`

A pivot cell with exactly two candidates `{A, B}` sees a "pincer" cell with candidates `{A, C}` and another pincer with `{B, C}`. Any cell that sees both pincers cannot contain `C`.

- For each empty cell with exactly two candidates as the pivot:
  - Find all empty cells visible to the pivot (same row, column, or block) with exactly two candidates, sharing exactly one candidate with the pivot — these are candidate pincers
  - For each pair of pincers where each shares a different candidate with the pivot and the shared "other" candidate `C` is the same in both:
    - Find all cells that see both pincers and contain `C` as a candidate
    - Eliminate `C` from those cells
- Return `highlightCells` = pivot + two pincers; `eliminatedCandidates` = cells where `C` is removed
- `markdownSlug`: `"y-wing"`

**Test:** `backend/src/test/java/com/sudoku/puzzle/hint/YWingStrategyTest.java`
- Detection: valid pivot + two pincers with at least one elimination
- No-match: no Y-Wing pattern exists
- Metadata: `getDifficultyRank() == 110`, `difficulty == "hard"`

---

## Key Reference Files

| Purpose | Path |
|---------|------|
| Strategy interface | `backend/src/main/java/com/sudoku/puzzle/hint/HintStrategy.java` |
| CDI auto-discovery | `backend/src/main/java/com/sudoku/SudokuServiceImpl.java` |
| Board domain (getRow/getColumn/getBlock) | `backend/src/main/java/com/sudoku/domain/Board.java` |
| HintResponse record | `backend/src/main/java/com/sudoku/dto/HintResponse.java` |
| CandidateElimination record | `backend/src/main/java/com/sudoku/dto/CandidateElimination.java` |
| Coordinate record | `backend/src/main/java/com/sudoku/dto/Coordinate.java` |
| Best reference impl | `backend/src/main/java/com/sudoku/puzzle/hint/NakedPairStrategy.java` |
| Frontend tutorials | `ui/public/techniques/<slug>.md` |

## Implementation Checklist

- [ ] HiddenSingleStrategy + test
- [ ] PointingPairStrategy + test
- [ ] NakedTripleStrategy + test
- [ ] HiddenPairStrategy + test
- [ ] HiddenTripleStrategy + test
- [ ] XWingStrategy + test
- [ ] SwordfishStrategy + test
- [ ] YWingStrategy + test
