# React Frontend

**Created**: 2026-04-18
**Status**: Complete

## Context and Current State

The React Frontend is the browser-side application: all the UI, state management, API communication, and user interaction logic. It is a React 19 + Vite single-page application styled with Material UI v7, served via AWS Amplify.

Files: all `ui/src/` files, `ui/package.json`, `ui/vite.config.js`.

The infrastructure that hosts and configures this app is documented separately in `docs/llds/cloud-platform.md`. The key coupling point is a set of `VITE_*` environment variables that Terraform injects into the Amplify build at deploy time.

## Environment Feature Flags

Five `VITE_*` flags control feature visibility and are baked into the bundle at build time:

| Flag | Effect when `true` |
| --- | --- |
| `VITE_MOCK_API` | Replace all API calls with canned data — no backend needed |
| `VITE_SKIP_AUTH` | Skip Amplify Authenticator — no Cognito needed |
| `VITE_LOG_API` | Log all requests/responses to browser console |
| `VITE_DEV_TOOLS` | Show developer submenu (11 demo techniques + data browser) |

Local dev uses `.env.development` (`MOCK_API=false`, `SKIP_AUTH=false`, `DEV_TOOLS=true`). Tests use `.env.test` (`MOCK_API=true`, `SKIP_AUTH=true`).

## Component Hierarchy

```text
App (MUI theme, auth wrapper, forbidden-state handler)
└── SudokuApp
    ├── Header (timer, user menu, game controls)
    ├── SudokuGrid (9×9 cells)
    │   └── SudokuCell × 81
    ├── NumberPad (digit input, action buttons, status)
    ├── HintDialog (nudge/focus/reveal + tutorial)
    │   └── TutorialModal (markdown technique explanation)
    ├── PauseOverlay (blur screen + resume)
    ├── NewGameModal (difficulty picker)
    ├── ImportModal (image upload + preview)
    ├── DevDataDialog (DynamoDB browser, VITE_DEV_TOOLS only)
    ├── AvatarPickerDialog
    ├── StatisticsDialog
    ├── PuzzleHistoryDialog
    └── StatusBar (toast notifications)
```

`App` handles theme creation, Amplify Authenticator wrapping, and the forbidden-access error screen. `SudokuApp` is the main shell, wiring hooks to components.

## State Architecture

All application state lives in two custom hooks. No global state library (Redux, Zustand, Context) is used.

### `useSudokuGame(user, { onGameComplete, onForbidden })`

The largest hook — owns the complete game loop.

**State exposed:**

| Category | Fields |
| --- | --- |
| Grids | `originalGrid`, `currentGrid` (9×9), `candidateGrid` (9×9×variable) |
| Selection | `selectedCell` ({row,col}), `selectedNumber` (1–9 or null), `inputMode` ('normal'/'candidate') |
| Errors & highlights | `errorCells` (Set of "row,col"), `highlightCells` ({row,col}[]) |
| Hint | `activeHint`, `hintStage` ('nudge'/'focus'/'reveal'), `excludedHintRanks`, `hintsUsed` |
| Lifecycle | `gameStatus`, `gameId`, `difficulty`, `solutionGrid`, `elapsedSeconds`, `isPaused`, `timerRunning` |
| Loading | `isLoading`, `statusMessage`, `importStage` |
| Undo | move history stack (type, row, col, prevValue, prevCandidates) |

**Key methods:**

| Method | What it does |
| --- | --- |
| `startNewGame(difficulty, signal)` | `createGame` API → sets up grids, starts timer |
| `startNewGameFromImage(imageFile)` | `importPuzzle` → `createGameFromGrid` → timer |
| `loadDemoGame(technique)` | `getDemoGrid` → loads with minRank (no backend game save) |
| `updateCell(row, col)` | Toggles selectedCell or writes selectedNumber to cell |
| `writeCellValue(row, col, n)` | Mode-aware write (normal digit or candidate toggle) |
| `clearCell()` | Zeros selectedCell, clears candidates |
| `undoLastMove()` | Pops history stack, restores grid state |
| `requestValidation()` | `validatePuzzle` API, shows errors or solved state |
| `requestHint()` | `getHint` API, increments hintsUsed, adds rank to excluded list |
| `requestAlternateHint()` | `getHint` with excluded ranks, resets exclusions if all exhausted |
| `advanceHint()` | Cycles nudge → focus → reveal, applies hint changes to grids |
| `fillCandidates()` | `getCandidates` API, populates candidateGrid |
| `pauseGame()` / `resumeGame()` | Stops/starts timer, sets isPaused |
| `finishGame()` | Calls onGameComplete, clears all state |

### `usePlayerProfile(user, { onForbidden })`

Manages user identity and game history.

**State exposed:**

- `avatar` — icon name (localStorage-persisted, default `'Person'`)
- `playerProfile` — from `GET /players/me` (null in mock mode)
- `history` — last 10 games (localStorage-persisted)
- `sessionEmail` — from Cognito session token

**Methods:** `setAvatar(iconName)`, `recordGame({gameId, difficulty, outcome, elapsedSeconds, hintsUsed})`

On mount: calls `getEmailFromSession()` then `getPlayerProfile()`, then unconditionally calls `warmupImageRecognition()` to pre-warm the Python Lambda.

## Persistence Strategy

Game state is persisted in two places simultaneously:

| Store | Contents | Trigger |
| --- | --- | --- |
| `localStorage` | gameId, currentGrid, candidateGrid, difficulty, elapsedSeconds, hintsUsed | Every state change |
| DynamoDB (via API) | currentGrid, candidates, timeSpentSeconds, status, hintsUsed | Auto-save every 60s; tab hidden; game complete |

On mount, the hook checks localStorage first (instant, no network). If no local game found, calls `GET /api/v1/games/current` to resume a server-side active game. This two-tier approach gives instant resume on page reload and cross-device continuity.

### localStorage Keys

| Key | Type | Purpose |
| --- | --- | --- |
| `sudoku_gameId` | string | Resume active game |
| `sudoku_currentGrid` | JSON 9×9 | Grid state across reloads |
| `sudoku_candidateGrid` | JSON 9×9×n | Pencil marks across reloads |
| `sudoku_difficulty` | string | Last played difficulty |
| `sudoku_elapsedSeconds` | number | Timer continuity |
| `sudoku_hintsUsed` | number | Hint count continuity |
| `sudoku_colorMode` | string | Light/dark theme preference |
| `sudoku_avatar` | string | Selected avatar icon name |
| `sudoku_gameHistory` | JSON array | Last 10 completed games |

## Auto-Save & Lifecycle Side Effects

- **Auto-save:** Every 60 seconds via `setInterval` → `saveGame` API
- **Visibility-change:** `document.addEventListener('visibilitychange')` — saves and pauses on tab hidden; resumes on visible
- **Inactivity auto-pause:** After 3 minutes of no cell/number input
- **User-change detection:** If `user.username` changes (different Cognito user), localStorage cleared and game check rerun
- **AbortController:** `startNewGame` passes a signal to cancel in-flight `generatePuzzle` requests if the user opens the new game modal again before the first completes

## API Client (`api/sudokuApi.js`)

All backend communication goes through a single module. Every function checks `VITE_MOCK_API` first and returns canned data if true.

**Authentication:** `apiFetch` calls `fetchAuthSession()` (AWS Amplify) to get the Cognito ID token and attaches it as `Authorization: Bearer {token}` on authenticated requests.

**Error handling:**

- HTTP 403 → throws `ForbiddenError` (caught by `usePlayerProfile`, triggers forbidden screen)
- HTTP 204 → returns `null`
- Other errors → parses JSON error body; reads `errorBody.message` as primary error field, falls back to `errorBody.error` for backwards compatibility

**Wire adapter:** `sudokuApi.js` imports `gridFromWire`, `gridToWire`, `candidatesFromWire`, `candidatesToWire` from `utils/gridAdapters.js`. All outbound grid fields are wrapped with `gridToWire`/`candidatesToWire`; all inbound responses unwrap via `gridFromWire`/`candidatesFromWire`. Internal hook state always uses plain arrays — the `Grid` wire format never propagates past `sudokuApi.js`.

Mock paths also call the unwrap helpers so the hook receives the same plain-array format regardless of `VITE_MOCK_API`.

**Endpoints:**

| Function | Method | Path | Auth |
| --- | --- | --- | --- |
| `generatePuzzle(difficulty, signal)` | GET | `/puzzles/generate` | No |
| `validatePuzzle(currentGrid, solutionGrid)` | POST | `/puzzles/validate` | No |
| `getHint(currentGrid, minRank, excludedRanks)` | POST | `/puzzles/hint` | No |
| `getCandidates(currentGrid)` | POST | `/puzzles/candidates` | No |
| `createGame(difficulty)` | POST | `/api/v1/games` | Yes |
| `loadGame(gameId)` | GET | `/api/v1/games/{gameId}` | Yes |
| `getCurrentGame()` | GET | `/api/v1/games/current` | Yes |
| `saveGame(gameId, update)` | PATCH | `/api/v1/games/{gameId}` | Yes |
| `createGameFromGrid(originalGrid)` | POST | `/api/v1/games/from-image` | Yes |
| `importPuzzle(imageFile)` | POST | `/api/v1/puzzles/import` | Yes |
| `warmupImageRecognition()` | GET | `/api/v1/puzzles/import/warmup` | No |
| `getPlayerProfile()` | GET | `/api/v1/players/me` | Yes |
| `getDemoGrid(technique)` | GET | `/dev/hint-demo` | No |
| `getDevData(entity)` | GET | `/dev/data/{entity}` | No |

`importPuzzle` converts the file to base64 before sending.

## Component Details

### SudokuGrid & SudokuCell

`SudokuGrid` renders the 9×9 grid with thick borders at block boundaries (every 3 cells). Cell size is responsive via CSS custom properties:

- `xs`: `min(calc((100vw - 16px) / 9), 84px)`
- `md`: `min(calc((100vh - 308px) / 9), 84px)`

`SudokuCell` displays either a single digit or a 3×3 mini-grid of candidate numbers. Cell background is determined by six mutually exclusive highlight states (priority order):

| Priority | State | Colour |
| --- | --- | --- |
| 1 | `isError` | `error.light` (red) |
| 2 | `isHighlight` | `warning.light` (orange) — hint cells |
| 3 | `isSelected` | `primary.dark` (blue) |
| 4 | `isNumberHighlight` | `primary.light` (light blue) — same digit as selected |
| 5 | `isRegionHighlight` | `#e8eaf6` (light purple) — same row/col/block |
| 6 | `isGiven` | `#fffde7` (light yellow) — original puzzle clues |
| 7 | Default | `background.paper` |

### NumberPad

Two rows of digit buttons (1–5 top, 6–9 bottom). A digit button is disabled in normal mode when that digit appears 9 times in the current grid (the number is complete). Input mode toggle (Normal / Candidate) is a `ToggleButtonGroup`. Action buttons: Undo, Clear, Validate, Hint, Fill Candidates.

### HintDialog

Responsive: inline `Collapse` panel below the grid on desktop (`md+`), full `Dialog` on mobile.

Displays the active hint at the current stage (`nudge` / `focus` / `reveal`). A help icon opens `TutorialModal` which fetches `/techniques/{slug}.md` and renders it with `react-markdown`. The markdown file is stripped of YAML frontmatter before rendering.

Stage progression buttons:

- Nudge: "Try Different Hint" + "Show Me" (advances to focus)
- Focus: "Try Different Hint" + "Show Me" (advances to reveal)
- Reveal: "Got It" (dismisses)

### Header

Left: hamburger game menu (New Game, Import, Developer submenu). Centre: elapsed time chip + hints-used chip. Right: pause/resume button + avatar with account dropdown.

Developer submenu (VITE_DEV_TOOLS only): 11 technique demo entries (`loadDemoGame(slug)`) + Data Browser.

Avatar menu: Change Avatar, Puzzle History, Statistics, Dark Mode toggle, Sign Out.

### ImportModal

Stages: file pick → upload → analysis → result. Shows image preview (max 200px) after file selection. Spinner with stage label during Bedrock processing. On success, calls `startNewGameFromImage`.

## MUI Theme

Two palettes (light/dark), toggled via localStorage `sudoku_colorMode`:

- Light primary: `#3949ab` (indigo)
- Dark primary: `#9c27b0` (purple)

Theme is created with `useMemo` and only recalculated when `colorMode` changes.

## Testing Setup

- **Unit/component tests:** Vitest + jsdom + React Testing Library
- **E2E tests:** Playwright (two suites: standard + hint-demos)
- **Coverage:** V8 provider, JUnit reporter
- **localStorage mock:** `test-setup.js` implements in-memory `localStorage`/`sessionStorage` (jsdom 26 does not expose them natively)
- **Mock data:** `mocks/cannedData.js` provides canned puzzles, hint, validation, game state, and candidates for `VITE_MOCK_API=true` mode; all grid fields are in wire format (`{rows: [...]}`)
- **Test isolation:** Tests that mock `sudokuApi.js` directly (e.g., `useSudokuGame.test.js`) must provide pre-unwrapped plain arrays since the real adapter code is bypassed

## Implementation Standards

These rules apply to all frontend code. They are referenced here so the LLD is the single source of truth for the React Frontend component.

### UI / Styling

- **Strict MUI:** Use `@mui/material` components for all UI elements. Do not write custom CSS or use Tailwind/Bootstrap. Use the `sx` prop for minor layout adjustments.
- **Responsive layout:** Use MUI `<Grid>` or `<Stack>`. The Sudoku board must scale on mobile and desktop without breaking the viewport.
- **Small, focused components:** Keep components single-purpose. Separate the grid visual rendering (`SudokuGrid`, `SudokuCell`) from game logic.

### State Management

- **Hooks only:** Use `useState`, `useEffect`, `useCallback`. Do not introduce Redux, Zustand, or any global state library.
- **No prop drilling past one level:** Use hook-returned objects passed to immediate children.

### API & Data Fetching

- **Native fetch only:** Do not install Axios. All HTTP calls go through `sudokuApi.js`.
- **API URL from env:** Always read `import.meta.env.VITE_API_URL` — never hardcode a base URL.
- **Error handling:** Gracefully handle API failures (cold start timeouts, network errors) and display user-friendly messages via MUI `<Snackbar>` or `<Alert>`.

## Three-Grid State Model

The frontend owns three distinct grid representations:

| State variable | Type | Description |
| --- | --- | --- |
| `originalGrid` | `number[][]` | Puzzle as returned by the server (0 = empty). Never mutated after load. |
| `currentGrid` | `number[][]` | Working copy — updated on every cell edit. |
| `candidateGrid` | `number[][][]` | Per-cell candidate lists (pencil marks). `[]` means no candidates set. |

`inputMode` (`'normal'` | `'candidate'`) controls whether a cell click writes a digit to `currentGrid` or toggles an entry in `candidateGrid`. Writing a digit always clears that cell's `candidateGrid` entry.

All three grids are stored in localStorage on every state change and restored on mount. Only `currentGrid` and `candidateGrid` are persisted to DynamoDB (via `PATCH /api/v1/games/{gameId}`). `originalGrid` is recovered from the DynamoDB game record.

Wire format: `sudokuApi.js` wraps grids in `{rows: [...]}` (via `gridToWire`) before sending and unwraps responses (via `gridFromWire`) before returning to the hook. Internal state always uses plain arrays.

## Observed Design Decisions

| Decision | Chosen | Alternatives Considered | Rationale |
| --- | --- | --- | --- |
| Two custom hooks | `useSudokuGame` + `usePlayerProfile` | Redux, Zustand, React Context | Two hooks is sufficient complexity; avoids framework overhead for a single-page app |
| localStorage + DynamoDB dual persistence | Both layers always active | DynamoDB only | localStorage: instant resume without network round-trip; DynamoDB: cross-device continuity |
| `VITE_MOCK_API` canned data | Per-function mock in `sudokuApi.js` | MSW (Mock Service Worker) | Simpler; no service worker setup; sufficient for development and test isolation |
| HintDialog responsive layout | Inline Collapse on desktop, Dialog on mobile | Modal only | Inline keeps the grid visible while reading the hint — better UX on large screens |
| Digit buttons disabled when complete | `completedNumbers.has(n)` in normal mode | Always enabled | Prevents redundant input; visual signal that a digit is fully placed |
| `excludedHintRanks` tracking | Accumulate per session, reset if all exhausted | No tracking | Provides hint variety ("Try Different Hint") without repeating techniques already shown |
| `AbortController` for game start | Cancel in-flight `generatePuzzle` on re-open | Ignore stale response | Prevents stale puzzle arriving after user has already started a different game |

## Technical Debt & Inconsistencies

- `useSudokuGame` is a very large hook. Several concerns (timer, localStorage sync, auto-save, hint management) could each be extracted into smaller hooks without changing the external interface.
- `TutorialModal` fetches markdown from `/techniques/{slug}.md` — confirmed that all 11 files exist in `ui/public/techniques/`.
- `usePlayerProfile.recordGame()` stores the last 10 games in localStorage only — there is no API endpoint to persist game history server-side. History is lost if the user clears their browser storage.

## Behavioral Quirks

- Selecting a number then clicking an empty cell places it; clicking a filled cell selects it (does not overwrite unless selectedNumber is set and inputMode is normal). The interaction model is tap-number-then-tap-cell OR tap-cell-then-tap-number — both orderings work.
- `loadDemoGame` does not call `createGame` on the backend — demo grids are local-only. If the user refreshes after loading a demo, `localStorage` has a game with no server counterpart; `GET /games/current` will return 204, and the stale localStorage data will be discarded.
- The `completedNumbers` set (digits appearing 9 times) is computed from `currentGrid` in `App.jsx` via `useMemo`, not inside `useSudokuGame`. This means the number pad and the hook are slightly decoupled — the hook doesn't know which numbers are "complete".

## References

- `ui/src/App.jsx`, `ui/src/main.jsx`
- `ui/src/api/sudokuApi.js`
- `ui/src/hooks/useSudokuGame.js`, `ui/src/hooks/usePlayerProfile.js`
- `ui/src/components/` (all component files)
- `ui/src/utils/avatarIcons.js`
- `ui/src/utils/gridAdapters.js`
- `ui/src/mocks/cannedData.js`
- `ui/src/test-setup.js`
- `ui/package.json`, `ui/vite.config.js`
- See also: `docs/llds/cloud-platform.md` (Amplify hosting, `VITE_*` injection)
- Depends on: Cloud Platform (hosting, env vars), User Management (auth flow), Game Lifecycle (game API), Puzzle Generation (puzzle/hint/candidates API), Image Recognition (import API)
- Depended on by: nothing (user-facing leaf)
