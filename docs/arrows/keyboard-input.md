# Arrow: Keyboard Input

Keyboard entry for cell selection, digit placement, arrow navigation, clear, and deselect.

## Status

**OK** — 2026-04-26. All 25 active specs implemented.

## References

### HLD
- docs/high-level-design.md — React Frontend component section

### LLD
- docs/llds/keyboard-input.md

### EARS
- docs/specs/keyboard-input-specs.md (19 specs, all [x])

### Tests
- ui/src/hooks/useKeyboardInput.test.js — 19 KBD specs; document keydown events via fireEvent

### Code
- ui/src/hooks/useKeyboardInput.js
- ui/src/hooks/useSudokuGame.js — `setSelectedCell` and `writeCellValue` added to return; `clearCell(row, col)` signature updated
- ui/src/App.jsx — composes `useKeyboardInput`; derives `isModalOpen`; manages `helpOpen` state
- ui/src/components/NumberPad.jsx — Clear button tooltip; Help (`?`) button; mode toggle icons
- ui/src/components/TutorialModal.jsx — `src` and `title` props added
- ui/public/help/controls.md — controls reference page

## Architecture

**Purpose:** Allow players to interact with the Sudoku grid entirely via keyboard on desktop — digit entry, candidate toggling, arrow navigation, clear, and deselect — without changing the mobile touch experience.

**Key Components:**
1. `useKeyboardInput` — pure side-effect hook; global `document` keydown listener; translates keys into `onDigit` / `onClear` / `onSelectCell` calls
2. `useSudokuGame` (modified) — exports `setSelectedCell` and updates `clearCell` to accept explicit `(row, col)` params
3. `SudokuApp` (modified) — composes `useKeyboardInput`; passes `isModalOpen` derived from all modal open states

## Key Map Summary

| Key | Action |
|---|---|
| `1`–`9` | Enter digit / toggle candidate |
| `0`, `Delete`, `Backspace` | Clear cell |
| `ArrowUp/Down/Left/Right` | Move selection (clamped at grid edges) |
| `Space` | Toggle Normal ↔ Candidate mode |
| `Escape` | Deselect cell |
| `U` | Undo last move |
| `C` | Check / validate the board |
| `H` | Request a hint |
| `F` | Fill all valid candidates |
| `?` / `/` | Open help/controls page |
| `P` | Toggle pause / resume |

All keys suppressed when: `isModalOpen`, `!currentGrid || gameStatus === 'solved'`, or `isPaused`.
`P` (pause toggle) is exempt from the `isPaused` guard so the player can resume via keyboard.
Digit/clear keys additionally suppressed on given cells.

## EARS Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
|---|---|---|---|---|
| Digit entry | KBD-001 to KBD-003 | 3 | 0 | 0 |
| Clear & deselect | KBD-020 to KBD-022 | 3 | 0 | 0 |
| Arrow navigation | KBD-010 to KBD-015 | 6 | 0 | 0 |
| Mode toggle | KBD-041 | 1 | 0 | 0 |
| Guard conditions | KBD-030 to KBD-034 | 5 | 0 | 0 |
| User discoverability | KBD-040 | 1 | 0 | 0 |
| Toolbar shortcuts | KBD-050 to KBD-055 | 6 | 0 | 0 |

**Summary:** 25 of 25 active specs implemented; 0 deferred; 0 gaps.

## Key Findings

1. **First `useSudokuGame` decomposition** — `useKeyboardInput` is the first hook extracted from the planned decomposition of the oversized `useSudokuGame`. Minimal interface: `selectedCell`, `inputMode`, `originalGrid`, `gameStatus`, `isPaused`, `isModalOpen`, and three callbacks.
2. **`setSelectedCell` must become public** — Currently internal to `useSudokuGame`; must be added to the return value to enable arrow navigation and Escape.
3. **`clearCell` signature change** — Updated from closure-based (reads `selectedCell` internally) to explicit `(row, col)` params. Enables keyboard hook to clear without coupling to internal state.
4. **`0` key discoverability** — Non-obvious shortcut; surfaced via MUI `Tooltip` on the Clear button: `"Clear cell (Del or 0)"`.
5. **No mobile changes** — Hook mounts on all devices but is a no-op without a hardware keyboard. Touch interaction unchanged.
6. **`isSelected` visual quirk preserved** — Blue background only on empty non-given cells; arrow navigation works through all cells regardless.

## Work Required

All items complete.
