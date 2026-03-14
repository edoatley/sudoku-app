# Frontend ↔ API Workflow

This document describes how the React frontend interacts with the Sudoku backend API across the full game lifecycle.

## Board State Owned by the Client

| State variable   | Type                   | Description                                      |
|------------------|------------------------|--------------------------------------------------|
| `originalGrid`   | `number[][]`           | Puzzle as returned by the server (0 = empty). Never mutated after load. |
| `currentGrid`    | `number[][]`           | Working copy; updated on every cell edit.        |
| `candidateGrid`  | `number[][][]`         | Per-cell candidate lists (pencil marks). `[]` means no candidates. |
| `inputMode`      | `'normal' \| 'candidate'` | Controls whether a cell click writes a digit or toggles a candidate. |

---

## 1. Initialization — Start New Game

**Trigger:** App mount or "New Game" button click.

**API call:** `GET /puzzles/generate?difficulty={easy|medium|hard}`

**Response shape (`PuzzleResponse`):**
```json
{
  "difficulty": "easy",
  "originalGrid": [[5,3,0,...], ...]
}
```

**Client actions:**
1. Set `originalGrid` = `data.originalGrid`
2. Set `currentGrid` = deep copy of `originalGrid`
3. Reset `candidateGrid` to empty (all `[]`)
4. Reset error/hint state

---

## 2. Core Gameplay Loop

All cell edits are **purely local** — no API call is made on every keystroke.

### Normal mode (`inputMode === 'normal'`)
- Writes the selected digit into `currentGrid[row][col]`
- Clears `candidateGrid[row][col]` (candidates discarded when a value is placed)
- Removes the cell from the error set

### Candidate mode (`inputMode === 'candidate'`)
- Toggles the selected digit in `candidateGrid[row][col]`
- Does **not** modify `currentGrid`

---

## 3. Validation

### Manual validation
**Trigger:** "Validate" button click.

**API call:** `POST /puzzles/validate`

**Request body:**
```json
{ "currentGrid": [[5,3,4,...], ...] }
```

**Response shape (`ValidationResponse`):**
```json
{
  "valid": true,
  "errors": [],
  "message": "Board is valid so far."
}
```

**Client actions:**
- If `valid && allCellsFilled` → `gameStatus = 'solved'`
- If `valid && !allCellsFilled` → `gameStatus = 'valid'`
- If `!valid` → `gameStatus = 'invalid'`, highlight error cells from `errors[]`

### Real-time debounced validation (optional)
The same `validatePuzzle` function can be called on a debounced timer after each cell edit to provide immediate inline feedback without spamming the API.

---

## 4. Hint

**Trigger:** "Hint" button click.

**API call:** `POST /puzzles/hint`

**Request body:**
```json
{ "currentGrid": [[5,3,4,...], ...] }
```

**Response shape (`HintResponse`):**
```json
{
  "coordinate": { "row": 0, "col": 2 },
  "value": 4
}
```

**Client actions:**
1. Write `value` into `currentGrid[row][col]`
2. Highlight the hinted cell briefly (cleared after 2 s)

---

## 5. Auto-Notes

**Trigger:** "Auto-Notes" button click.

**API call:** `POST /puzzles/candidates`

**Request body:**
```json
{ "currentGrid": [[5,3,4,...], ...] }
```

**Response shape (`CandidatesResponse`):**
```json
{
  "candidatesGrid": [
    [[], [], [1,2,4,6], ...],
    ...
  ]
}
```

**Client actions:**
1. Overwrite the entire `candidateGrid` with `result.candidatesGrid`
2. Empty cells now show server-computed candidates; filled cells have `[]`

After Auto-Notes, the player can continue in candidate mode to refine marks, or switch to normal mode to fill in digits (which clears that cell's candidates automatically).

---

## 6. Mock API

Set `VITE_MOCK_API=true` in `.env.local` to run entirely offline. All API functions return canned data after a simulated 400 ms delay, allowing frontend development and testing without a running backend.

| Function          | Mock data source         |
|-------------------|--------------------------|
| `generatePuzzle`  | `CANNED_PUZZLES[difficulty]` |
| `validatePuzzle`  | `CANNED_VALIDATE_VALID`  |
| `getHint`         | `CANNED_HINT`            |
| `getCandidates`   | `CANNED_CANDIDATES`      |
