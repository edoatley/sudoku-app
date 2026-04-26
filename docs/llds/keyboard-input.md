# Keyboard Input

**Created**: 2026-04-26
**Status**: In Progress

## Context and Current State

The Sudoku app currently accepts digit input only via the NumberPad — a pointer-based widget below the grid. There is no keyboard entry path. This LLD describes the `useKeyboardInput` hook that adds full keyboard play: digit entry, candidate toggling, arrow-key navigation, clear, and deselect.

This is the first step in decomposing `useSudokuGame` (noted as oversized in the react-frontend arrow). `useKeyboardInput` is extracted as a standalone, composable hook rather than adding more logic to `useSudokuGame`.

Files: `ui/src/hooks/useKeyboardInput.js`, `ui/src/hooks/useKeyboardInput.test.js`

## Interaction Model

### Desktop (keyboard + pointer)

The player interacts with the grid in two ways — they can be used interchangeably:

**Pointer-first:** Click a cell to select it → press a digit key to enter a value.
**Keyboard-first:** Click a cell to select it → use arrow keys to navigate → press a digit key.

The NumberPad remains fully available. Keyboard and NumberPad are not mutually exclusive — a player may click a digit on the NumberPad and then use arrow keys to move to the next cell.

### Mobile (touch only)

No keyboard is attached. The `useKeyboardInput` hook still mounts but will simply never fire (no hardware keyboard events). The NumberPad is the sole input mechanism on mobile. No virtual keyboard is triggered by cell selection. No changes are made to the mobile layout or touch interaction.

### Key Map

| Key | Condition | Action |
|---|---|---|
| `1`–`9` | Cell selected, game in progress, not paused, not a given | Write digit (normal mode) or toggle candidate (candidate mode) |
| `0` | Cell selected, game in progress, not paused, not a given | Clear cell value and candidates (same as Delete/Backspace) |
| `Delete` / `Backspace` | Cell selected, game in progress, not paused, not a given | Clear cell value and candidates |
| `ArrowUp` | Cell selected, game in progress, not paused | Move selection one row up (clamp at row 0) |
| `ArrowDown` | Cell selected, game in progress, not paused | Move selection one row down (clamp at row 8) |
| `ArrowLeft` | Cell selected, game in progress, not paused | Move selection one column left (clamp at col 0) |
| `ArrowRight` | Cell selected, game in progress, not paused | Move selection one column right (clamp at col 8) |
| `Escape` | Game in progress, not paused | Deselect (set `selectedCell` to null) |
| All other keys | Any | Ignored |

Arrow keys are **clamped** (not wrapping): pressing Up on row 0 does nothing. This matches most desktop Sudoku apps and is less surprising than wrap-around.

`inputMode` is unaffected by arrow navigation — moving between cells does not reset candidate mode to normal mode.

### Clearing via `0` key — user discoverability

The `0` key acting as clear is non-obvious. A tooltip is added to the Clear button in `NumberPadToolbar` (MUI `Tooltip` wrapping the button, `title="Clear cell (Del or 0)"`) so the keyboard shortcut is discoverable without documentation.

### Guard Conditions

The listener suppresses all key handling when any of the following are true:

| Condition | Reason |
|---|---|
| `isModalOpen === true` | Prevents digits/arrows firing while modals are open (NewGameModal, ImportModal, dialogs) |
| `!currentGrid` | No game loaded — keys have no target |
| `gameStatus === 'solved'` | Game complete — grid is read-only |
| `isPaused === true` | Paused means no actions; arrow navigation is also suppressed |

`isModalOpen` is a single boolean OR-ed from all modal open states in `SudokuApp`. The hook has no knowledge of which modals exist.

Writing to a **given cell** (`originalGrid[r][c] !== 0`) is silently blocked for digit/clear keys. Arrow navigation and Escape still work on given cells — the selection can move through them freely.

## Hook Interface

```js
useKeyboardInput({
  selectedCell,    // { row: number, col: number } | null
  inputMode,       // 'normal' | 'candidate'
  originalGrid,    // number[][] — identifies given cells (originalGrid[r][c] !== 0)
  currentGrid,     // number[][] | null — null means no game loaded
  gameStatus,      // string — 'idle' | 'valid' | 'invalid' | 'solved' | 'error'
  isPaused,        // boolean
  isModalOpen,     // boolean — OR of all modal open states in SudokuApp
  onDigit,         // (row, col, digit) => void  — writeCellValue(row, col, n)
  onClear,         // (row, col) => void          — clearCell(row, col)
  onSelectCell,    // ({ row, col } | null) => void — setSelectedCell
})
```

The hook returns nothing — it is a pure side-effect hook.

`onClear` receives explicit `(row, col)` coordinates. This requires a small change to `clearCell` in `useSudokuGame`: the existing implementation reads `selectedCell` from closure; the new signature accepts `(row, col)` as parameters directly. Both callers (NumberPad's existing clear button and `useKeyboardInput`) pass the selected cell's coordinates explicitly.

## Implementation Sketch

```js
useEffect(() => {
  function handleKeyDown(e) {
    if (isModalOpen) return;
    if (!currentGrid || gameStatus === 'solved') return;
    if (isPaused) return;

    if (e.key === 'Escape') {
      onSelectCell(null);
      return;
    }

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
      e.preventDefault();  // prevent page scroll
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

    // Clear: 0 key, Delete, or Backspace
    if (e.key === '0' || e.key === 'Delete' || e.key === 'Backspace') {
      e.preventDefault();  // prevent browser back-navigation on Backspace
      onClear(row, col);
    }
  }

  document.addEventListener('keydown', handleKeyDown);
  return () => document.removeEventListener('keydown', handleKeyDown);
}, [selectedCell, inputMode, originalGrid, gameStatus, isPaused, isModalOpen,
    onDigit, onClear, onSelectCell]);
```

## Integration in `SudokuApp`

`useSudokuGame` exposes `selectedCell`, `inputMode`, `originalGrid`, `gameStatus`, `isPaused`, `writeCellValue`, and `clearCell`. One change to `useSudokuGame`'s public interface is required:

1. **`setSelectedCell` added to return value** — currently internal; must be exported so `useKeyboardInput` can move the selection via arrow keys and Escape.
2. **`clearCell(row, col)` signature change** — currently reads `selectedCell` from closure; updated to accept explicit coordinates. The NumberPad clear button caller passes `selectedCell.row, selectedCell.col` explicitly.

`SudokuApp` composes the hook:

```js
const {
  selectedCell, inputMode, originalGrid, gameStatus, isPaused,
  writeCellValue, clearCell, setSelectedCell,
  // ... rest of game state
} = useSudokuGame(user, { onGameComplete, onForbidden });

const isModalOpen = newGameOpen || importOpen || avatarPickerOpen
  || statisticsOpen || historyOpen || /* any other modal */;

useKeyboardInput({
  selectedCell, inputMode, originalGrid, gameStatus, isPaused,
  isModalOpen,
  onDigit: writeCellValue,
  onClear: clearCell,
  onSelectCell: setSelectedCell,
});
```

## Cell Selection Visual Behaviour

The existing `isSelected` logic in `SudokuGrid` drives the full highlight system:

- **Blue background (`primary.dark`)** — only when `value === 0 AND originalGrid[row][col] === 0 AND selectedNumber === null`
- **Region highlight** — all cells in same row/col/block as `selectedCell`
- **Number highlight** — all cells containing the same digit as the selected cell's value

Arrow navigation updates `selectedCell` — all three highlights update automatically. Given cells and filled cells can be navigated through (they receive region highlight) but do not show the blue background; this is intentional and preserved.

When `onSelectCell(null)` fires (Escape), `selectedCell` becomes null and all highlights clear.

## Edge Cases

| Scenario | Behaviour |
|---|---|
| Arrow key at grid boundary | Clamped — selection does not move; no error or visual feedback |
| Digit key on a given cell | Silently blocked — `originalGrid[r][c] !== 0` guard |
| Digit key with no cell selected | Ignored — `if (!selectedCell) return` guard |
| `0` key on a given cell | Silently blocked — same given-cell guard |
| Escape with no cell selected | No-op — `onSelectCell(null)` when already null is harmless |
| Modal opens mid-selection | `isModalOpen=true` suppresses all keys; selection preserved in background |
| Modal closes | Keys resume immediately on next keydown |
| No game loaded | `!currentGrid` guard suppresses all keys |
| Game paused | `isPaused` guard suppresses all keys including arrow navigation |
| Game solved | `gameStatus === 'solved'` guard suppresses all keys |
| Backspace key | `e.preventDefault()` prevents browser back-navigation |
| Arrow keys | `e.preventDefault()` prevents page scroll |
| NumberPad click while cell selected | Works as before — `writeCellValue` / `clearCell` called directly from NumberPad |

## Decisions & Alternatives

| Decision | Chosen | Alternatives Considered | Rationale |
|---|---|---|---|
| Global `document` listener | Yes | `tabIndex` + focus on grid container | No focus management headaches; consistent with existing document-level effects in `useSudokuGame` |
| Separate `useKeyboardInput` hook | Yes | Add to `useSudokuGame` | First decomposition step; small, isolated, testable |
| Arrow keys clamp at boundary | Clamp | Wrap around | Less surprising; matches most desktop Sudoku apps |
| `0` key acts as clear | Yes | Ignored | Consistent with numpad-style entry; discoverability via Clear button tooltip |
| `isModalOpen` single boolean | Yes | Per-modal guards inside hook | Hook has no knowledge of which modals exist; cleaner contract |
| No Tab navigation | Excluded | Tab key moves selection | Tab is browser focus management; conflicts with standard accessibility patterns |
| Paused suppresses arrow navigation | Yes | Allow navigation while paused | Paused means no game actions; arrow keys moving cells would partially reveal the board |
| `clearCell(row, col)` explicit params | Yes | Keep reading from closure | Enables `useKeyboardInput` to clear without coupling to `selectedCell` internal state |
| Mobile: no change | Yes | Trigger virtual keyboard on cell select | Touch users have NumberPad; virtual keyboard would be disruptive and not requested |

## References

- `ui/src/hooks/useKeyboardInput.js` (to be created)
- `ui/src/hooks/useKeyboardInput.test.js` (to be created)
- `ui/src/hooks/useSudokuGame.js` — `setSelectedCell` added to return; `clearCell` signature updated
- `ui/src/components/SudokuApp.jsx` — composes `useKeyboardInput`; derives `isModalOpen`
- `ui/src/components/NumberPad.jsx` / `NumberPadToolbar` — tooltip added to Clear button
- Depends on: `docs/llds/react-frontend.md`
- Depended on by: nothing (leaf hook)
