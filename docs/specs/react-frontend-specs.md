# React Frontend — EARS Specifications

## Game Interaction

- [x] **FE-UI-001**: The system shall display a 9×9 Sudoku grid with bold borders at every third row and column boundary.
- [x] **FE-UI-002**: When a player selects a cell, the system shall highlight all cells in the same row, column, and 3×3 block.
- [x] **FE-UI-003**: When a player selects a cell, the system shall highlight all other cells containing the same digit.
- [x] **FE-UI-004**: When a player selects a number in normal input mode and then selects an empty cell, the system shall place that digit in the cell.
- [x] **FE-UI-005**: When a player selects a number in candidate input mode and then selects a cell, the system shall toggle that digit in the cell's candidate set.
- [x] **FE-UI-006**: The system shall disable digit buttons in normal mode for any digit that already appears 9 times in the current grid.
- [x] **FE-UI-007**: The system shall display candidate digits as a 3×3 mini-grid within cells that have no placed value.
- [x] **FE-UI-008a**: The system shall render candidate digit text in a colour that meets WCAG AA contrast (≥ 4.5:1) against the cell background in both light and dark colour modes.
- [x] **FE-UI-008**: When the player triggers undo, the system shall restore the grid to the state before the most recent cell edit.
- [x] **FE-UI-053**: While a game is active, the system shall display a colour-coded chip in the header showing the capitalised difficulty (easy=green, medium=orange, hard=red, imported=purple).

## Hint UX

- [x] **FE-UI-010**: When a hint is received, the system shall display the nudge text and highlight the relevant cells without revealing the solution.
- [x] **FE-UI-011**: When the player clicks "Show Me" from the nudge stage, the system shall advance to the focus stage and display the focus text.
- [x] **FE-UI-012**: When the player clicks "Show Me" from the focus stage, the system shall advance to the reveal stage, display the reveal text, and apply the hint's eliminations and solved cells to the grid.
- [x] **FE-UI-013**: The system shall display a "Try Different Hint" button at the nudge and focus stages that requests a new hint excluding the current technique's rank.
- [x] **FE-UI-014**: Where the screen width is medium or larger, the system shall display the hint as an inline panel below the grid rather than a modal dialog.
- [x] **FE-UI-015**: When the player clicks the help icon on a hint, the system shall open a tutorial modal fetching and rendering the technique's markdown explanation.

## Persistence & Resume

- [x] **FE-BE-001**: The system shall persist gameId, currentGrid, candidateGrid, difficulty, elapsedSeconds, and hintsUsed to localStorage on every state change.
- [x] **FE-BE-002**: When the application loads, the system shall restore game state from localStorage if a saved game exists, without making an API call.
- [x] **FE-BE-003**: When the application loads and no localStorage game exists, the system shall call GET /api/v1/games/current to resume a server-side active game.
- [x] **FE-BE-004**: The system shall auto-save currentGrid, candidates, timeSpentSeconds, and hintsUsed to DynamoDB every 60 seconds while a game is in progress.
- [x] **FE-BE-005**: When the browser tab becomes hidden, the system shall save the current game state and pause the timer.
- [x] **FE-BE-006**: When the browser tab becomes visible again, the system shall resume the timer.
- [x] **FE-BE-007**: When the authenticated user changes, the system shall clear all localStorage game data and perform a fresh game check.

## Puzzle-Play Event Buffer

- [x] **FE-BE-020**: When the player places a digit in normal input mode, clears a cell, or requests a hint, the system shall append a corresponding puzzle-play event (NUMBER, NUMBER_CLEAR, or HINT_REQUEST/HINT_RESPONSE) carrying a client timestamp to an in-memory buffer; candidate-mode toggles shall not be buffered.
- [x] **FE-BE-021**: When the player requests a hint, the system shall generate a correlation id, record a HINT_REQUEST event with it, and on resolution record a HINT_RESPONSE event with the same correlation id — carrying the technique name, strategy rank, and the nudge/focus/reveal explanation text, with found=true when a hint is returned, or found=false when no strategy applies; when the request fails with a transport error, no HINT_RESPONSE is recorded.
- [x] **FE-BE-022**: The system shall include the buffered puzzle-play events as the events field of the PATCH /api/v1/games/{gameId} autosave payload, and shall clear the buffer only after the PATCH succeeds, retaining the events for the next sync if it fails.
- [x] **FE-BE-023**: When the puzzle-play event buffer exceeds 500 entries, the system shall drop the oldest entries and mark the batch as truncated.
- [x] **FE-BE-024**: The system shall reset the puzzle-play event buffer when a new game starts and when the current game is finished, so buffered events are never sent on a PATCH for a different game.
- [x] **FE-BE-025**: When the player undoes a normal-mode digit placement, the system shall append an UNDO puzzle-play event carrying the cell coordinates, the digit removed, the digit (or empty) restored, and undoneType "NUMBER"; undoing a candidate-mode toggle or a "fill candidates" bulk action shall not be buffered, matching the buffering rule for those actions in FE-BE-020.

## Timer & Pause

- [x] **FE-UI-020**: While a game is in progress and not paused, the system shall increment the elapsed time display every second.
- [x] **FE-UI-021**: When the player pauses the game, the system shall display a pause overlay covering the grid and stop the timer.
- [x] **FE-UI-022**: After 3 minutes of inactivity (no cell or number input), the system shall automatically pause the game.

## Authentication

- [x] **FE-BE-010**: The system shall attach the Cognito ID token as a Bearer token on all authenticated API requests.
- [x] **FE-BE-011**: When an API call returns HTTP 403, the system shall display a forbidden-access screen and stop further API calls.
- [x] **FE-BE-012**: Where VITE_SKIP_AUTH is false, the system shall wrap the application in the Amplify Authenticator and require login before displaying the game.
- [x] **FE-BE-013**: Where VITE_MOCK_API is true, the system shall return canned data for all API calls without making network requests.

## Image Import

- [x] **FE-UI-030**: The system shall display an "Import from Image" option in the game menu.
- [x] **FE-UI-031**: The system shall display a file picker accepting image files and show a preview of the selected image before submission.
- [x] **FE-UI-032**: While the image is being processed, the system shall display a loading indicator with a stage label (uploading / analysing).
- [x] **FE-UI-033**: When image recognition returns validPuzzle=false, the system shall proceed to import validation in the Java backend which is the authoritative validator.

## Player Profile & History

- [x] **FE-UI-040**: The system shall display the player's avatar in the header and allow selection from a predefined set of icons.
- [x] **FE-UI-041**: The system shall persist the selected avatar to localStorage under the key sudoku_avatar.
- [x] **FE-UI-042**: The system shall record up to 10 completed games in localStorage under key `sudoku_gameHistory` and display them in the Puzzle History dialog, showing each entry's difficulty, outcome, date, elapsed time (won games only), hints used, and provisional score. When authenticated, the dialog fetches from the backend on open and on refresh (→ GH-UI-003, GH-UI-005).
- [x] **FE-UI-042a**: When the Puzzle History dialog contains one or more entries, the system shall display a summary banner showing: total wins, win rate (when ≥3 games played), best completion time (when ≥1 win exists), current win streak (when streak ≥1), and average score (when ≥1 win exists).
- [x] **FE-UI-042b**: The system shall compute a provisional per-game score for won games based on difficulty base score, elapsed time, and hints used. (TODO: replace with server-side scoring system.)
- [x] **FE-UI-043**: The system shall display game statistics grouped by difficulty (total, wins, losses, average time) in the Statistics dialog.

## Mobile Layout

- [x] **FE-MOB-001**: The system shall display action buttons (Undo, Clear, Check, Hint, Fill) in a single toolbar row at all viewport sizes without overflow.
- [x] **FE-MOB-002**: The system shall display normal digit buttons (1–9) in a single row on viewports wider than the `sm` breakpoint, and in two rows (1–5, then 6–9) on narrower viewports.
- [x] **FE-MOB-004**: The system shall size the 9×9 grid so that all cells, borders, and container padding fit within the viewport width on mobile without horizontal overflow.

## Developer Tools

- [x] **FE-UI-050**: Where VITE_DEV_TOOLS is true, the system shall display a developer submenu in the game menu with entries for all 11 hint technique demos.
- [x] **FE-UI-051**: Where VITE_DEV_TOOLS is true, or the authenticated user is a member of the admin Cognito group, the system shall display a Data Browser option allowing inspection of DynamoDB game and player records (see UM-BE-060).
- [x] **FE-UI-052**: When a demo technique is selected, the system shall load the pre-baked demo grid with the technique's minRank set so simpler strategies are skipped.
