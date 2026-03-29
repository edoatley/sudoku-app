# TODO: Hint Strategy Implementation

## Implemented Strategies

| Rank | Strategy | Difficulty | Class |
|------|----------|------------|-------|
| 10 | Full House | Easy | `FullHouseStrategy.java` |
| 20 | Naked Single | Easy | `NakedSingleStrategy.java` |
| 30 | Naked Pair | Medium | `NakedPairStrategy.java` |
| 40 | Hidden Single | Easy | `HiddenSingleStrategy.java` |
| 50 | Pointing Pair | Medium | `PointingPairStrategy.java` |

## Remaining Strategies

| Rank | Strategy | Difficulty | Concept |
|------|----------|------------|---------|
| 60 | Naked Triple | Medium | Three cells in a unit share exactly the same three candidates |
| 70 | Hidden Pair | Medium | Two candidates appear in only two cells in a unit; remove other candidates from those cells |
| 80 | Hidden Triple | Hard | Three candidates restricted to exactly three cells in a unit |
| 90 | X-Wing | Hard | Candidate restricted to two cells in two rows sharing the same two columns |
| 100 | Swordfish | Hard | X-Wing extended to three rows/columns |
| 110 | Y-Wing | Hard | Three-cell chain with specific candidate relationships enabling eliminations |

---

## How Each Strategy Is Wired Up

Every strategy requires **five changes**:

1. **Strategy class** — `backend/src/main/java/com/sudoku/puzzle/hint/<Name>Strategy.java`
   - `@ApplicationScoped` (CDI auto-discovers it — no registration needed in `SudokuServiceImpl`)
   - Implements `HintStrategy`: `evaluate(Board)` + `getDifficultyRank()`
2. **Strategy test class** — `backend/src/test/java/com/sudoku/puzzle/hint/<Name>StrategyTest.java`
   - Follow the pattern in `NakedPairStrategyTest` / `HiddenSingleStrategyTest` / `PointingPairStrategyTest`
3. **Demo grid JSON** — `backend/src/main/resources/developer/hint-demo-<slug>.json`
   - A puzzle where the target strategy fires **after** lower-rank strategies have autocompleted all cells they can
   - Format: `{ "slug": "<slug>", "description": "...", "grid": [[...9 rows...]] }`
   - **Critical:** the grid must not be solvable by any lower-rank strategy after autocomplete — test this before committing
   - Add slug to `KNOWN_SLUGS` in `HintDemoGrids.java`
4. **Demo grid contract tests** — add to `backend/src/test/java/com/sudoku/puzzle/developer/HintDemoGridsTest.java`
   - `<slug>_demoGrid_strategyFiresAfterAutocomplete()` — autocomplete with all lower strategies, then assert the target strategy fires and `markdownSlug` matches
   - `<slug>_demoGrid_isValidSudoku()` — assert no duplicate digits in any row/column/block
   - Follow the `nakedPair_*` / `pointingPair_*` patterns already in the file
   - **These tests are the verification gate for the demo grid** — do not finalise the JSON until they pass
5. **Developer menu entry** — `ui/src/components/Header.jsx`
   - Add `{ slug: '<slug>', label: '<Name> demo' }` to the `DEMO_TECHNIQUES` array
   - The new entry is automatically covered by the Playwright hint demo tests in `ui/tests/integration/hint-demos.spec.js` — add the new `{ slug, label, techniqueName }` entry to `HINT_DEMOS` there too

### Demo Grid Pitfall

> **Lesson learned from Pointing Pair:** A grid that looks appropriate can be fully solved by simpler strategies, leaving nothing for the target strategy. Always verify by running the `HintDemoGridsTest` contract test before finalising the JSON. If the test fails, the grid needs to be harder (more cells left empty, fewer obvious singles).

To find a suitable grid: start from a puzzle rated at the target difficulty level (e.g. a "medium" puzzle for Naked Triple) and confirm with the contract test. The `GridDiagTest` temporary test pattern used during development — create it, run it, delete it — is a useful diagnostic tool.

---

## Per-Strategy Work

### Rank 60 — Naked Triple (`medium`)

**Files to create:**
- `backend/src/main/java/com/sudoku/puzzle/hint/NakedTripleStrategy.java`
- `backend/src/test/java/com/sudoku/puzzle/hint/NakedTripleStrategyTest.java`
- `backend/src/main/resources/developer/hint-demo-naked-triple.json`

**Files to modify:**
- `backend/src/main/java/com/sudoku/puzzle/developer/HintDemoGrids.java` — add `"naked-triple"` to `KNOWN_SLUGS`
- `backend/src/test/java/com/sudoku/puzzle/developer/HintDemoGridsTest.java` — add `nakedTriple_demoGrid_strategyFiresAfterAutocomplete()` and `nakedTriple_demoGrid_isValidSudoku()` (autocomplete with FullHouse + NakedSingle + HiddenSingle)
- `ui/src/components/Header.jsx` — add `{ slug: 'naked-triple', label: 'Naked Triple demo' }`
- `ui/tests/integration/hint-demos.spec.js` — add `{ slug: 'naked-triple', label: 'Naked Triple demo', techniqueName: 'Naked Triple' }` to `HINT_DEMOS`

**Algorithm:**
- For each unit (row 0–8, column 0–8, block 0–8):
  - Collect all empty cells in the unit
  - For every combination of 3 such cells, compute the union of their candidates
  - If the union has exactly 3 digits, it's a naked triple
  - Collect `CandidateElimination` entries: for every other empty cell in the unit, any of the 3 digits it contains as a candidate
  - If any eliminations exist → return immediately

**HintResponse fields:**
- `techniqueName`: `"Naked Triple"`
- `markdownSlug`: `"naked-triple"`
- `difficulty`: `"medium"`
- `nudge`: `"Three cells in a unit collectively contain only three candidates."`
- `focus`: describe the unit and the three digits, e.g. `"Row 3: cells (3,1), (3,4), (3,7) share only candidates {2, 5, 8}."`
- `reveal`: `"Digits 2, 5, and 8 can be removed from all other cells in row 3."`
- `highlightCells`: the three triple cells
- `eliminatedCandidates`: removable candidates in other cells of the unit
- `solvedCells`: empty list

**Tests:**
- Detection: three cells with candidates `{1,2}`, `{2,3}`, `{1,3}` — union is `{1,2,3}` — with at least one elimination in the same unit
- No-match: use the solved grid (no candidates)
- Metadata: `getDifficultyRank() == 60`, `markdownSlug == "naked-triple"`, `difficulty == "medium"`

---

### Rank 70 — Hidden Pair (`medium`)

**Files to create:**
- `backend/src/main/java/com/sudoku/puzzle/hint/HiddenPairStrategy.java`
- `backend/src/test/java/com/sudoku/puzzle/hint/HiddenPairStrategyTest.java`
- `backend/src/main/resources/developer/hint-demo-hidden-pair.json`

**Files to modify:**
- `backend/src/main/java/com/sudoku/puzzle/developer/HintDemoGrids.java` — add `"hidden-pair"` to `KNOWN_SLUGS`
- `backend/src/test/java/com/sudoku/puzzle/developer/HintDemoGridsTest.java` — add `hiddenPair_demoGrid_strategyFiresAfterAutocomplete()` and `hiddenPair_demoGrid_isValidSudoku()` (autocomplete with FullHouse + NakedSingle + NakedTriple + HiddenSingle)
- `ui/src/components/Header.jsx` — add `{ slug: 'hidden-pair', label: 'Hidden Pair demo' }`
- `ui/tests/integration/hint-demos.spec.js` — add `{ slug: 'hidden-pair', label: 'Hidden Pair demo', techniqueName: 'Hidden Pair' }` to `HINT_DEMOS`

**Algorithm:**
- For each unit (row, column, block):
  - For each pair of digits (A, B) from 1–9:
    - Find the empty cells in the unit that contain A as a candidate
    - Find the empty cells in the unit that contain B as a candidate
    - If both digits appear in exactly the same two cells:
      - Collect `CandidateElimination` entries: every candidate in those two cells that is not A or B
      - If any eliminations exist → return immediately

**HintResponse fields:**
- `techniqueName`: `"Hidden Pair"`
- `markdownSlug`: `"hidden-pair"`
- `difficulty`: `"medium"`
- `nudge`: `"Two digits appear as candidates in exactly the same two cells within a unit."`
- `focus`: describe the unit, digits, and cells, e.g. `"Column 5: digits 3 and 7 are confined to cells (1,5) and (6,5)."`
- `reveal`: `"All other candidates can be removed from cells (1,5) and (6,5)."`
- `highlightCells`: the two hidden-pair cells
- `eliminatedCandidates`: non-pair candidates in those two cells
- `solvedCells`: empty list

**Tests:**
- Detection: two digits each confined to the same two cells, with extra candidates to eliminate
- No-match: use the solved grid (no candidates)
- Metadata: `getDifficultyRank() == 70`, `markdownSlug == "hidden-pair"`, `difficulty == "medium"`

---

### Rank 80 — Hidden Triple (`hard`)

**Files to create:**
- `backend/src/main/java/com/sudoku/puzzle/hint/HiddenTripleStrategy.java`
- `backend/src/test/java/com/sudoku/puzzle/hint/HiddenTripleStrategyTest.java`
- `backend/src/main/resources/developer/hint-demo-hidden-triple.json`

**Files to modify:**
- `backend/src/main/java/com/sudoku/puzzle/developer/HintDemoGrids.java` — add `"hidden-triple"` to `KNOWN_SLUGS`
- `backend/src/test/java/com/sudoku/puzzle/developer/HintDemoGridsTest.java` — add `hiddenTriple_demoGrid_strategyFiresAfterAutocomplete()` and `hiddenTriple_demoGrid_isValidSudoku()` (autocomplete with FullHouse + NakedSingle + NakedTriple + HiddenSingle + HiddenPair)
- `ui/src/components/Header.jsx` — add `{ slug: 'hidden-triple', label: 'Hidden Triple demo' }`
- `ui/tests/integration/hint-demos.spec.js` — add `{ slug: 'hidden-triple', label: 'Hidden Triple demo', techniqueName: 'Hidden Triple' }` to `HINT_DEMOS`

**Algorithm:**
- For each unit (row, column, block):
  - For each combination of 3 digits (A, B, C) from 1–9:
    - Collect every empty cell in the unit that contains at least one of A, B, C as a candidate
    - If that set of cells has exactly 3 members:
      - Collect `CandidateElimination` entries: for those 3 cells, every candidate that is not A, B, or C
      - If any eliminations exist → return immediately

**HintResponse fields:**
- `techniqueName`: `"Hidden Triple"`
- `markdownSlug`: `"hidden-triple"`
- `difficulty`: `"hard"`
- `nudge`: `"Three digits are collectively confined to exactly three cells within a unit."`
- `focus`: describe the unit, digits, and cells, e.g. `"Block 4: digits 1, 4, and 9 are confined to cells (3,4), (4,3), (5,5)."`
- `reveal`: `"All other candidates can be removed from those three cells."`
- `highlightCells`: the three hidden-triple cells
- `eliminatedCandidates`: non-triple candidates in those three cells
- `solvedCells`: empty list

**Tests:**
- Detection: three digits confined to the same three cells with extra candidates to eliminate
- No-match: use the solved grid (no candidates)
- Metadata: `getDifficultyRank() == 80`, `markdownSlug == "hidden-triple"`, `difficulty == "hard"`

---

### Rank 90 — X-Wing (`hard`)

**Files to create:**
- `backend/src/main/java/com/sudoku/puzzle/hint/XWingStrategy.java`
- `backend/src/test/java/com/sudoku/puzzle/hint/XWingStrategyTest.java`
- `backend/src/main/resources/developer/hint-demo-x-wing.json`

**Files to modify:**
- `backend/src/main/java/com/sudoku/puzzle/developer/HintDemoGrids.java` — add `"x-wing"` to `KNOWN_SLUGS`
- `backend/src/test/java/com/sudoku/puzzle/developer/HintDemoGridsTest.java` — add `xWing_demoGrid_strategyFiresAfterAutocomplete()` and `xWing_demoGrid_isValidSudoku()` (autocomplete with all lower strategies)
- `ui/src/components/Header.jsx` — add `{ slug: 'x-wing', label: 'X-Wing demo' }`
- `ui/tests/integration/hint-demos.spec.js` — add `{ slug: 'x-wing', label: 'X-Wing demo', techniqueName: 'X-Wing' }` to `HINT_DEMOS`

**Algorithm:**
- For each digit 1–9:
  - **Row-based:** for each row, collect the columns where the digit is a candidate — keep only rows where exactly 2 such columns exist
    - For every pair of such rows that share the same 2 columns:
      - Collect `CandidateElimination` entries: in those 2 columns, every empty cell NOT in the two defining rows that has the digit as a candidate
      - If any eliminations exist → return immediately
  - **Column-based:** repeat symmetrically (find pairs of columns sharing the same 2 rows, eliminate from those rows)

**HintResponse fields:**
- `techniqueName`: `"X-Wing"`
- `markdownSlug`: `"x-wing"`
- `difficulty`: `"hard"`
- `nudge`: `"A digit appears in exactly two cells in each of two rows, and those cells share the same two columns."`
- `focus`: e.g. `"Digit 5 in rows 1 and 6 is confined to columns 2 and 8."`
- `reveal`: `"Digit 5 can be removed from all other cells in columns 2 and 8."`
- `highlightCells`: the four corner cells of the X-Wing rectangle
- `eliminatedCandidates`: cells in the two columns (or rows for column-based) outside the rectangle
- `solvedCells`: empty list

**Tests:**
- Detection (row-based): digit in exactly 2 columns in 2 rows that match, with eliminations in those columns
- Detection (column-based): symmetric case
- No-match: use the solved grid (no candidates)
- Metadata: `getDifficultyRank() == 90`, `markdownSlug == "x-wing"`, `difficulty == "hard"`

---

### Rank 100 — Swordfish (`hard`)

**Files to create:**
- `backend/src/main/java/com/sudoku/puzzle/hint/SwordfishStrategy.java`
- `backend/src/test/java/com/sudoku/puzzle/hint/SwordfishStrategyTest.java`
- `backend/src/main/resources/developer/hint-demo-swordfish.json`

**Files to modify:**
- `backend/src/main/java/com/sudoku/puzzle/developer/HintDemoGrids.java` — add `"swordfish"` to `KNOWN_SLUGS`
- `backend/src/test/java/com/sudoku/puzzle/developer/HintDemoGridsTest.java` — add `swordfish_demoGrid_strategyFiresAfterAutocomplete()` and `swordfish_demoGrid_isValidSudoku()` (autocomplete with all lower strategies)
- `ui/src/components/Header.jsx` — add `{ slug: 'swordfish', label: 'Swordfish demo' }`
- `ui/tests/integration/hint-demos.spec.js` — add `{ slug: 'swordfish', label: 'Swordfish demo', techniqueName: 'Swordfish' }` to `HINT_DEMOS`

**Algorithm:**
- For each digit 1–9:
  - **Row-based:** for each row, collect the columns where the digit is a candidate — keep only rows where 2 or 3 such columns exist
    - For every combination of 3 such rows whose column sets union to exactly 3 columns:
      - Collect `CandidateElimination` entries: in those 3 columns, every empty cell NOT in the three defining rows that has the digit as a candidate
      - If any eliminations exist → return immediately
  - **Column-based:** repeat symmetrically

**HintResponse fields:**
- `techniqueName`: `"Swordfish"`
- `markdownSlug`: `"swordfish"`
- `difficulty`: `"hard"`
- `nudge`: `"A digit appears in 2–3 cells across three rows, and those cells span exactly three columns."`
- `focus`: e.g. `"Digit 3 in rows 0, 4, and 7 is confined to columns 1, 5, and 8."`
- `reveal`: `"Digit 3 can be removed from all other cells in columns 1, 5, and 8."`
- `highlightCells`: the defining cells in the three rows (up to 9 cells)
- `eliminatedCandidates`: cells in the three columns outside the defining rows
- `solvedCells`: empty list

**Tests:**
- Detection: digit spanning 3 rows with exactly 3 column positions, with eliminations
- No-match: use the solved grid (no candidates)
- Metadata: `getDifficultyRank() == 100`, `markdownSlug == "swordfish"`, `difficulty == "hard"`

---

### Rank 110 — Y-Wing (`hard`)

**Files to create:**
- `backend/src/main/java/com/sudoku/puzzle/hint/YWingStrategy.java`
- `backend/src/test/java/com/sudoku/puzzle/hint/YWingStrategyTest.java`
- `backend/src/main/resources/developer/hint-demo-y-wing.json`

**Files to modify:**
- `backend/src/main/java/com/sudoku/puzzle/developer/HintDemoGrids.java` — add `"y-wing"` to `KNOWN_SLUGS`
- `backend/src/test/java/com/sudoku/puzzle/developer/HintDemoGridsTest.java` — add `yWing_demoGrid_strategyFiresAfterAutocomplete()` and `yWing_demoGrid_isValidSudoku()` (autocomplete with all lower strategies)
- `ui/src/components/Header.jsx` — add `{ slug: 'y-wing', label: 'Y-Wing demo' }`
- `ui/tests/integration/hint-demos.spec.js` — add `{ slug: 'y-wing', label: 'Y-Wing demo', techniqueName: 'Y-Wing' }` to `HINT_DEMOS`

**Algorithm:**
- For each empty cell P (pivot) with exactly 2 candidates `{A, B}`:
  - Find all empty cells visible to P (same row, column, or block) with exactly 2 candidates that share exactly one candidate with P — these are candidate pincers
  - For each pair of pincers (P1, P2):
    - P1 must share candidate A with P (so P1 has `{A, C}` for some C)
    - P2 must share candidate B with P (so P2 has `{B, C}` for the same C)
    - The "shared other" candidate C must be the same in both pincers
    - Find all empty cells visible to both P1 and P2 (same row, column, or block as each) that contain C as a candidate — these are the elimination targets
    - If any eliminations exist → return immediately
- "Visible" means: same row, same column, or same 3×3 block

**HintResponse fields:**
- `techniqueName`: `"Y-Wing"`
- `markdownSlug`: `"y-wing"`
- `difficulty`: `"hard"`
- `nudge`: `"A pivot cell with two candidates sees two pincers that together force an elimination."`
- `focus`: e.g. `"Pivot (2,4) has {3,7}; pincers (2,8) with {3,5} and (6,4) with {7,5} share candidate 5."`
- `reveal`: `"Digit 5 can be removed from any cell that sees both pincers."`
- `highlightCells`: pivot + two pincers (3 cells total)
- `eliminatedCandidates`: cells that see both pincers and contain C
- `solvedCells`: empty list

**Tests:**
- Detection: valid pivot + two pincers with at least one cell seeing both — that cell has C eliminated
- No-match: use the solved grid (no candidates)
- Metadata: `getDifficultyRank() == 110`, `markdownSlug == "y-wing"`, `difficulty == "hard"`

---

## Key Reference Files

| Purpose | Path |
|---------|------|
| Strategy interface | `backend/src/main/java/com/sudoku/puzzle/hint/HintStrategy.java` |
| CDI auto-discovery | `backend/src/main/java/com/sudoku/SudokuServiceImpl.java` |
| Board domain (`getRow`/`getColumn`/`getBlock`/`getCell`) | `backend/src/main/java/com/sudoku/domain/Board.java` |
| Cell domain (`row()`/`col()`/`isEmpty()`/`candidates()`) | `backend/src/main/java/com/sudoku/domain/Cell.java` |
| HintResponse record | `backend/src/main/java/com/sudoku/dto/HintResponse.java` |
| CandidateElimination record | `backend/src/main/java/com/sudoku/dto/CandidateElimination.java` |
| ActionableCell record | `backend/src/main/java/com/sudoku/dto/ActionableCell.java` |
| Coordinate record | `backend/src/main/java/com/sudoku/dto/Coordinate.java` |
| Best reference impl | `backend/src/main/java/com/sudoku/puzzle/hint/NakedPairStrategy.java` |
| Demo grid registry | `backend/src/main/java/com/sudoku/puzzle/developer/HintDemoGrids.java` |
| Demo grid contract tests | `backend/src/test/java/com/sudoku/puzzle/developer/HintDemoGridsTest.java` |
| Dev endpoint | `backend/src/main/java/com/sudoku/puzzle/developer/DevResource.java` |
| Developer menu UI | `ui/src/components/Header.jsx` |
| Hint demo Playwright tests | `ui/tests/integration/hint-demos.spec.js` |
| Frontend technique docs | `ui/public/techniques/<slug>.md` |

## Implementation Checklist

Each row covers: strategy class + strategy test + demo JSON + HintDemoGridsTest contract tests + Header.jsx entry + hint-demos.spec.js entry.

- [x] FullHouseStrategy
- [x] NakedSingleStrategy
- [x] NakedPairStrategy
- [x] HiddenSingleStrategy
- [x] PointingPairStrategy
- [ ] NakedTripleStrategy
- [ ] HiddenPairStrategy
- [ ] HiddenTripleStrategy
- [ ] XWingStrategy
- [ ] SwordfishStrategy
- [ ] YWingStrategy
