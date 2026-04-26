# Implementation Plan: Keyboard Input

**Created**: 2026-04-26
**Branch**: rc-keyboard-entry
**Status**: Ready to implement

## References

- LLD: `docs/llds/keyboard-input.md`
- EARS: `docs/specs/keyboard-input-specs.md`
- Arrow: `docs/arrows/keyboard-input.md`

## Definition of Done

- [ ] All 18 KBD specs marked `[x]` in `docs/specs/keyboard-input-specs.md`
- [ ] `useKeyboardInput.test.js` passes with coverage of all 18 specs
- [ ] No regressions in `useSudokuGame.test.js`
- [ ] `npm run lint` passes clean
- [ ] Manual smoke test: digit entry, candidate toggle, arrow nav, clear, deselect, guard conditions (pause, modal, solved)
- [ ] Arrow: `docs/arrows/keyboard-input.md` updated to 18/18 implemented, status OK

---

## Phase 1 — `useSudokuGame` interface changes

Two small changes to the existing hook to expose what `useKeyboardInput` needs.

- [ ] **1a. Export `setSelectedCell`**
  Add `setSelectedCell` to the `useSudokuGame` return object (line ~744 in `useSudokuGame.js`).
  No logic change — `setSelectedCell` is already a `useState` setter.
  @spec KBD-010, KBD-011, KBD-012, KBD-013, KBD-022

- [ ] **1b. Update `clearCell` signature to accept explicit `(row, col)`**
  Change `clearCell` from reading `selectedCell` from closure to accepting `(row, col)` as parameters.
  Before:
  ```js
  const clearCell = useCallback(() => {
    if (!selectedCell) return;
    const { row, col } = selectedCell;
    ...
  }, [selectedCell, originalGrid]);
  ```
  After:
  ```js
  const clearCell = useCallback((row, col) => {
    if (row == null || col == null) return;
    if (originalGrid[row][col] !== 0) return;
    ...
  }, [originalGrid]);
  ```
  @spec KBD-020

- [ ] **1c. Update `clearCell` caller in `App.jsx`**
  `NumberPadToolbar` receives `onClearCell={clearCell}` — its click handler must pass coordinates:
  ```js
  onClearCell={() => selectedCell && clearCell(selectedCell.row, selectedCell.col)}
  ```
  No change to `NumberPadToolbar` props shape — the change is in `App.jsx`'s inline handler.
  @spec KBD-020

---

## Phase 2 — Create `useKeyboardInput` hook

- [ ] **2a. Create `ui/src/hooks/useKeyboardInput.js`**

  ```js
  import { useEffect } from 'react';

  // @spec KBD-001, KBD-002, KBD-003, KBD-010, KBD-011, KBD-012, KBD-013,
  //       KBD-014, KBD-015, KBD-020, KBD-021, KBD-022, KBD-030, KBD-031,
  //       KBD-032, KBD-033, KBD-034
  export function useKeyboardInput({
    selectedCell,
    inputMode,
    originalGrid,
    gameStatus,
    isPaused,
    isModalOpen,
    onDigit,
    onClear,
    onSelectCell,
  }) {
    useEffect(() => {
      function handleKeyDown(e) {
        // Guard: modal/hint open
        if (isModalOpen) return;
        // Guard: game not active
        if (gameStatus !== 'IN_PROGRESS') return;
        // Guard: paused
        if (isPaused) return;

        // Escape — deselect (works without a selected cell)
        if (e.key === 'Escape') {
          onSelectCell(null);
          return;
        }

        // All remaining actions require a selected cell
        if (!selectedCell) return;
        const { row, col } = selectedCell;

        // Arrow navigation — works on given and non-given cells
        const moves = {
          ArrowUp:    { row: Math.max(0, row - 1), col },
          ArrowDown:  { row: Math.min(8, row + 1), col },
          ArrowLeft:  { row, col: Math.max(0, col - 1) },
          ArrowRight: { row, col: Math.min(8, col + 1) },
        };
        if (moves[e.key]) {
          e.preventDefault();
          onSelectCell(moves[e.key]);
          return;
        }

        // Block writes to given cells
        if (originalGrid[row][col] !== 0) return;

        // Digit entry (1–9)
        const digit = parseInt(e.key, 10);
        if (digit >= 1 && digit <= 9) {
          onDigit(row, col, digit);
          return;
        }

        // Clear: 0, Delete, Backspace
        if (e.key === '0' || e.key === 'Delete' || e.key === 'Backspace') {
          e.preventDefault();
          onClear(row, col);
        }
      }

      document.addEventListener('keydown', handleKeyDown);
      return () => document.removeEventListener('keydown', handleKeyDown);
    }, [selectedCell, inputMode, originalGrid, gameStatus, isPaused,
        isModalOpen, onDigit, onClear, onSelectCell]);
  }
  ```

---

## Phase 3 — Compose hook in `App.jsx`

- [ ] **3a. Import `useKeyboardInput` in `App.jsx`**
  ```js
  import { useKeyboardInput } from './hooks/useKeyboardInput.js';
  ```

- [ ] **3b. Destructure `setSelectedCell` and `writeCellValue` from `useSudokuGame`**
  Add `setSelectedCell` and `writeCellValue` to the destructured return in `SudokuApp`.

- [ ] **3c. Derive `isModalOpen` and call `useKeyboardInput`**
  After the `useSudokuGame` call, add:
  ```js
  const isModalOpen = newGameModalOpen || importModalOpen || devDataOpen
    || gameStatus === 'solved'   // congrats Dialog
    || !!activeHint;             // HintDialog (inline or modal) — KBD-033

  useKeyboardInput({
    selectedCell,
    inputMode,
    originalGrid,
    gameStatus,
    isPaused,
    isModalOpen,
    onDigit: writeCellValue,
    onClear: clearCell,
    onSelectCell: setSelectedCell,
  });
  ```
  @spec KBD-030, KBD-031, KBD-032, KBD-033

---

## Phase 4 — Clear button tooltip

- [ ] **4a. Add tooltip to Clear button in `NumberPadToolbar`**
  Wrap the existing Clear `IconButton` (or `Button`) in a MUI `Tooltip`:
  ```jsx
  import Tooltip from '@mui/material/Tooltip';

  <Tooltip title="Clear cell (Del or 0)">
    <span> {/* span needed so Tooltip works on disabled buttons */}
      <IconButton onClick={onClearCell} ...>
        <BackspaceIcon />
      </IconButton>
    </span>
  </Tooltip>
  ```
  @spec KBD-040

---

## Phase 5 — Tests

- [ ] **5a. Create `ui/src/hooks/useKeyboardInput.test.js`**

  Test structure (one `describe` block per spec category):

  ```
  describe('digit entry')
    it('writes digit to empty cell in normal mode')         KBD-001
    it('toggles candidate in candidate mode')              KBD-002
    it('ignores digit on given cell')                      KBD-003

  describe('clear and deselect')
    it('clears cell on Delete')                            KBD-020
    it('clears cell on Backspace')                         KBD-020
    it('clears cell on 0')                                 KBD-020
    it('deselects on Escape')                              KBD-022

  describe('arrow navigation')
    it('moves selection up')                               KBD-010
    it('clamps at top row')                                KBD-010
    it('moves selection down')                             KBD-011
    it('clamps at bottom row')                             KBD-011
    it('moves selection left')                             KBD-012
    it('clamps at left column')                            KBD-012
    it('moves selection right')                            KBD-013
    it('clamps at right column')                           KBD-013
    it('does not change inputMode on arrow key')           KBD-015

  describe('guard conditions')
    it('suppresses all keys when gameStatus is idle')      KBD-030
    it('suppresses all keys when paused')                  KBD-031
    it('suppresses all keys when modal open')              KBD-032
    it('suppresses all keys when hint active')             KBD-033
    it('ignores digit with no cell selected')              KBD-034
  ```

  Use `renderHook` to mount `useKeyboardInput` with controlled props.
  Fire key events via `fireEvent.keyDown(document, { key: '5' })`.
  Assert on `onDigit`, `onClear`, `onSelectCell` vi.fn() spies.

- [ ] **5b. Verify `useSudokuGame.test.js` still passes** after `clearCell` signature change.
  Existing tests that call `clearCell` via the hook's returned function must pass explicit coords,
  or confirm the tests invoke it via `updateCell`/`handleNumberSelect` paths that don't call
  `clearCell` directly.

---

## Phase 6 — Arrow tracking

- [ ] **6a. Mark all 18 KBD specs `[x]` in `docs/specs/keyboard-input-specs.md`**
- [ ] **6b. Update `docs/arrows/keyboard-input.md`**
  - Set status to `OK — 2026-04-26`
  - Set EARS coverage table to 18 implemented, 0 gaps
- [ ] **6c. Update `docs/arrows/index.yaml`**
  - Set `status: OK`, `implemented: 18`, `gaps: 0`
- [ ] **6d. Update `docs/arrows/react-frontend.md`**
  - Add `useKeyboardInput` to Key Components list
  - Add `setSelectedCell` exposure and `clearCell` signature change to Key Findings
  - Add `useKeyboardInput.js` and `useKeyboardInput.test.js` to Code references
