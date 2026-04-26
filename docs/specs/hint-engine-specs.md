# Hint Engine — EARS Specifications

## Strategy Discovery

- [x] **HE-BE-001**: The system shall discover all HintStrategy implementations automatically via CDI at startup without explicit registration.
- [x] **HE-BE-002**: The system shall order discovered strategies by difficulty rank ascending, storing the sorted list once at startup.

## Hint Request

- [x] **HE-BE-003**: When a hint is requested, the system shall calculate all cell candidates on the submitted grid before evaluating any strategy.
- [x] **HE-BE-004**: When a hint is requested with a minRank, the system shall skip all strategies with rank strictly less than minRank.
- [x] **HE-BE-005**: When a hint is requested with excludedRanks, the system shall skip all strategies whose rank appears in that list.
- [x] **HE-BE-006**: The system shall return the first strategy result that contains at least one eliminated candidate or one solved cell.
- [x] **HE-BE-007**: The system shall return a typed `HintResult`: `Found` when a strategy produces an actionable result, `PuzzleSolved` when the board has no empty cells and no errors, or `NoStrategyApplied` when all eligible strategies are exhausted without an actionable result.

## Strategy Implementations

- [x] **HE-BE-010**: The Full House strategy (rank 10) shall identify any unit with exactly one empty cell and return the forced digit for that cell.
- [x] **HE-BE-011**: The Naked Single strategy (rank 20) shall identify any empty cell with exactly one candidate remaining and return that digit.
- [x] **HE-BE-012**: The Naked Pair strategy (rank 30) shall identify two cells in a unit sharing an identical 2-candidate set and return the eliminations of those two digits from all other cells in that unit.
- [x] **HE-BE-013**: The Hidden Single strategy (rank 40) shall identify any digit appearing as a candidate in exactly one cell of a unit and return that digit as forced for that cell.
- [x] **HE-BE-014**: The Pointing Pair strategy (rank 50) shall identify any digit whose candidates within a block are confined to a single row or column, and return the eliminations of that digit from the rest of that line outside the block.
- [x] **HE-BE-015**: The Naked Triple strategy (rank 60) shall identify three cells in a unit whose combined candidates total exactly three digits, and return the eliminations of those digits from all other cells in that unit.
- [x] **HE-BE-016**: The Hidden Pair strategy (rank 70) shall identify two digits confined to the same two cells within a unit, and return the eliminations of all other candidates from those two cells.
- [x] **HE-BE-017**: The Hidden Triple strategy (rank 80) shall identify three digits confined to the same three cells within a unit, and return the eliminations of all non-triple candidates from those three cells.
- [x] **HE-BE-018**: The X-Wing strategy (rank 90) shall identify a digit confined to exactly two cells in each of two rows (or columns) sharing the same two columns (or rows), and return the eliminations of that digit from all other cells in those columns (or rows).
- [x] **HE-BE-019**: The Swordfish strategy (rank 100) shall identify a digit spanning two or three cells across three rows (or columns) confined to exactly three columns (or rows), and return the eliminations of that digit from all other cells in those cover lines.
- [x] **HE-BE-020**: The Y-Wing strategy (rank 110) shall identify a pivot cell with two candidates that sees two pincers each sharing one candidate with the pivot and a common third candidate, and return the eliminations of that third candidate from all cells visible to both pincers.

## Hint Response Structure

- [x] **HE-API-001**: The system shall return a hint response containing a nudge, focus, and reveal text field, all populated regardless of which disclosure stage the client is at.
- [x] **HE-API-002**: The system shall return highlightCells identifying the cells most relevant to the technique pattern.
- [x] **HE-API-003**: The system shall return focusCandidates identifying the specific pencil-mark digits central to the technique.
- [x] **HE-API-004**: The system shall return eliminatedCandidates as a list of (row, col, digit) triples for pencil marks that can be removed.
- [x] **HE-API-005**: The system shall return solvedCells as a list of (row, col, value) triples for cells with a definitively forced digit.
- [x] **HE-API-006**: The system shall include the strategy name, markdown slug, difficulty label, and numeric rank in every hint response.

## Hint Display (UI)

- [x] **HE-UI-001**: When the UI displays a hint text field (nudge, focus, or reveal), it shall convert any 0-based cell coordinate pattern `(r, c)` or `(r,c)` to the 1-based equivalent `(r+1, c+1)`.
- [x] **HE-UI-002**: When the UI displays a hint text field, it shall convert any 0-based unit reference of the form `Row N`, `Column N`, `row N`, or `column N` (where N is a single digit 0–8) to the 1-based equivalent (N+1).
- [x] **HE-UI-003**: When the UI displays a hint text field, it shall convert any 0-based multi-unit reference of the form `rows N and M`, `rows N, M and P`, `columns N and M`, or `columns N, M and P` to the 1-based equivalent.
- [x] **HE-UI-004**: When the UI displays a hint text field, it shall convert any 0-based block reference of the form `Block N` or `block N` to a human-readable name from the sequence: top-left (0), top-middle (1), top-right (2), middle-left (3), centre (4), middle-right (5), bottom-left (6), bottom-middle (7), bottom-right (8).
- [x] **HE-UI-005**: The coordinate and unit conversion shall be applied only at the display layer and shall not mutate the underlying hint data or any internal state.

## Hint Exhaustion Fallback (UI)

- [x] **HE-UI-010**: When the UI receives a null response from a hint request (HTTP 404 NoStrategyApplied or 204 PuzzleSolved), it shall treat it as a no-hint signal rather than an error, so the fallback path can be applied without surfacing an HTTP error to the user.
- [x] **HE-UI-011**: When a hint request returns null and the exclusion list is non-empty, the UI shall immediately retry the request with an empty exclusion list to obtain the easiest applicable hint for the current board state.
- [x] **HE-UI-012**: When the fallback retry succeeds, the UI shall reset the exclusion list to contain only the rank returned by the retry, discarding all previously accumulated exclusions.
- [x] **HE-UI-013**: When a hint request returns null and the exclusion list is already empty, the UI shall not retry and shall display a "No more hints available" message.
- [x] **HE-UI-014**: When both the initial and fallback hint requests return null, the UI shall display a "No more hints available" message without surfacing an HTTP error.

## Supporting Operations

- [x] **HE-BE-030**: When getCandidates() is called, the system shall return the full 9×9 candidate grid computed from the submitted current grid.
- [x] **HE-BE-031**: When validatePuzzle() is called with a solution grid, the system shall report as errors all cells where the current value differs from the solution.
- [x] **HE-BE-032**: When validatePuzzle() is called without a solution grid, the system shall report as errors all cells participating in a row, column, or block duplicate.
- [x] **HE-BE-033**: The system shall report isSolved=true only when there are no errors and no empty cells.
- [x] **HE-BE-034**: When solveGrid() is called, the system shall return a valid completed grid if one exists, or an empty result if the puzzle has no solution.
- [x] **HE-BE-035**: When hasSingleSolution() is called, the system shall return true only if exactly one solution exists.
