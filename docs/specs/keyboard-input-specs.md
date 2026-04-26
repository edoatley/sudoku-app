# Keyboard Input — EARS Specifications

## Digit Entry

- [x] **KBD-001**: When a player presses a digit key (1–9) and a non-given cell is selected and the game is in progress and not paused, the system shall write that digit to the selected cell in normal input mode.
- [x] **KBD-002**: When a player presses a digit key (1–9) and a non-given cell is selected and the game is in progress and not paused, the system shall toggle that digit in the selected cell's candidate set in candidate input mode.
- [x] **KBD-003**: When a player presses a digit key (1–9) and a given cell is selected, the system shall ignore the keypress and make no change to the grid.

## Clear & Deselect

- [x] **KBD-020**: When a player presses Delete, Backspace, or 0 and a non-given cell is selected and the game is in progress and not paused, the system shall clear the selected cell's value and candidates.
- [x] **KBD-021**: When a player presses Delete or Backspace, the system shall prevent the browser's default back-navigation behaviour.
- [x] **KBD-022**: When a player presses Escape and the game is in progress and not paused, the system shall deselect the currently selected cell.

## Arrow Navigation

- [x] **KBD-010**: When a player presses ArrowUp and a cell is selected and the game is in progress and not paused, the system shall move the selection one row upward, clamping at row 0.
- [x] **KBD-011**: When a player presses ArrowDown and a cell is selected and the game is in progress and not paused, the system shall move the selection one row downward, clamping at row 8.
- [x] **KBD-012**: When a player presses ArrowLeft and a cell is selected and the game is in progress and not paused, the system shall move the selection one column leftward, clamping at column 0.
- [x] **KBD-013**: When a player presses ArrowRight and a cell is selected and the game is in progress and not paused, the system shall move the selection one column rightward, clamping at column 8.
- [x] **KBD-014**: When a player presses any Arrow key, the system shall prevent the browser's default page-scroll behaviour.
- [x] **KBD-015**: When a player uses arrow keys to navigate, the system shall not change the current input mode (normal or candidate).

## Guard Conditions

- [x] **KBD-030**: While no game grid is loaded or the game is solved, the system shall suppress all keyboard input handling.
- [x] **KBD-031**: While the game is paused, the system shall suppress all keyboard input handling including arrow navigation.
- [x] **KBD-032**: While any modal or dialog is open (NewGameModal, ImportModal, DevDataDialog, congratulations dialog, HintDialog, or any Header dialog), the system shall suppress all keyboard input handling.
- [x] **KBD-033**: While the HintDialog is visible (activeHint is non-null), the system shall suppress all keyboard input handling even when the hint is displayed inline rather than as a modal dialog.
- [x] **KBD-034**: When a player presses any digit, clear, or arrow key and no cell is currently selected, the system shall ignore the keypress.

## Mode Toggle

- [x] **KBD-041**: When a player presses Space and the game is in progress and not paused, the system shall toggle the input mode between normal and candidate.

## User Discoverability

- [x] **KBD-040**: The system shall display a tooltip on the Clear button reading "Clear cell (Del or 0)" so that keyboard shortcuts for clearing are discoverable.
