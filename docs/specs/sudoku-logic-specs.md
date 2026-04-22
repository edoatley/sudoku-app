# Sudoku Logic — EARS Specifications

## Board Construction

- [x] **SL-DATA-001**: The system shall represent a Sudoku puzzle as a 9×9 grid of cells addressed by zero-based (row, col) coordinates.
- [x] **SL-DATA-002**: The system shall represent an empty cell with the value 0 and a placed digit with values 1–9.
- [x] **SL-DATA-003**: When constructing a Board from a grid, the system shall reject any grid that is not exactly 9 rows of 9 columns by throwing an exception.
- [x] **SL-DATA-004**: When constructing a Board from a grid, the system shall reject any cell value outside the range [0, 9] by throwing an exception.
- [x] **SL-DATA-005**: The system shall number the nine 3×3 blocks 0–8 in row-major order (block 0 = top-left, block 8 = bottom-right).

## Cell Model

- [x] **SL-DATA-006**: The system shall store a candidate set on each Cell representing digits still possible at that position.
- [x] **SL-DATA-007**: When a digit is placed in a Cell via setValue(), the system shall clear that Cell's candidate set.
- [x] **SL-DATA-008**: The system shall expose isEmpty() returning true when a Cell's value is 0.

## Candidate Calculation

- [x] **SL-PROC-001**: When calculateAllCandidates() is called on a Board, the system shall populate the candidate set of every empty cell with digits 1–9 minus any digit already present in that cell's row, column, or 3×3 block.
- [x] **SL-PROC-002**: When calculateAllCandidates() is called on a Board, the system shall leave the candidate set of every filled cell empty.
- [x] **SL-PROC-003**: The system shall serialize candidate sets via toCandidatesGrid() returning a `CandidatesGrid`, with an empty inner list for filled cells.

## Geometry Utilities

- [x] **SL-PROC-004**: The system shall provide candidateColumnsInRow(board, digit, row) returning the sorted column indices where digit is a candidate in that row.
- [x] **SL-PROC-005**: The system shall provide candidateRowsInColumn(board, digit, col) returning the sorted row indices where digit is a candidate in that column.
- [x] **SL-PROC-006**: The system shall provide isVisible(cellA, cellB) returning true if the two cells share a row, column, or 3×3 block.
