# Arrow: React Frontend

Browser SPA — game UI, hint UX, state hooks, API client, localStorage + DynamoDB dual persistence.

## Status

**OK** - 2026-04-19. All active specs implemented.

## References

### HLD
- docs/high-level-design.md — "Two-Layer Persistence", "Progressive Hint Disclosure" sections

### LLD
- docs/llds/react-frontend.md

### EARS
- docs/specs/react-frontend-specs.md (39 specs, all [x])
- docs/specs/domain-types-specs.md — DT-UI-001 to DT-UI-009 all [x]

### Tests
- ui/src/api/sudokuApi.test.js — wire wrapping/unwrapping, error fallback, mock mode; 76 unit tests total
- ui/src/hooks/useSudokuGame.test.js — full game loop with mocked API
- ui/src/hooks/useKeyboardInput.test.js — all 19 KBD specs; document keydown events via fireEvent

### Code
- ui/src/App.jsx, ui/src/main.jsx
- ui/src/api/sudokuApi.js
- ui/src/hooks/useSudokuGame.js
- ui/src/hooks/useKeyboardInput.js
- ui/src/hooks/usePlayerProfile.js
- ui/src/components/ (all component files)
- ui/src/utils/avatarIcons.js
- ui/src/utils/gridAdapters.js
- ui/src/mocks/cannedData.js

## Architecture

**Purpose:** Deliver the complete user experience in the browser — game play, hints, image import, profile, history, and developer tooling.

**Key Components:**
1. `useSudokuGame` — owns game state, grid logic, hint escalation, undo, auto-save, timer, localStorage sync
2. `useKeyboardInput` — pure side-effect hook; global `document` keydown listener; first decomposition of `useSudokuGame`
3. `usePlayerProfile` — owns user identity, avatar, game history
4. `sudokuApi.js` — all backend communication with mock mode, auth token injection, ForbiddenError handling, grid wire adapter
5. `gridAdapters.js` — `gridFromWire`/`gridToWire`/`candidatesFromWire`/`candidatesToWire` utility functions
6. `SudokuGrid` / `SudokuCell` — responsive grid rendering with 6-priority highlight system
7. `HintDialog` — nudge/focus/reveal progressive disclosure; inline on desktop, modal on mobile
8. `Header` — timer, user menu, game controls, developer submenu

## EARS Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
| --- | --- | --- | --- | --- |
| Game Interaction | FE-UI-001 to 008 | 8 | 0 | 0 |
| Hint UX | FE-UI-010 to 015 | 6 | 0 | 0 |
| Persistence & Resume | FE-BE-001 to 007 | 7 | 0 | 0 |
| Timer & Pause | FE-UI-020 to 022 | 3 | 0 | 0 |
| Authentication | FE-BE-010 to 013 | 4 | 0 | 0 |
| Image Import | FE-UI-030 to 033 | 4 | 0 | 0 |
| Player Profile & History | FE-UI-040 to 043 | 4 | 0 | 0 |
| Developer Tools | FE-UI-050 to 052 | 3 | 0 | 0 |
| Wire Adapters | DT-UI-001 to 004 | 4 | 0 | 0 |
| API Integration | DT-UI-005 to 007 | 3 | 0 | 0 |
| Error Handling | DT-UI-008 | 1 | 0 | 0 |
| Mock Data | DT-UI-009 | 1 | 0 | 0 |

**Summary:** 48 of 48 active specs implemented; 0 deferred; 0 gaps.

## Key Findings

1. **`useSudokuGame` decomposition started** — `useKeyboardInput` is the first hook extracted from `useSudokuGame`. Required two small interface changes: `setSelectedCell` added to the return value; `clearCell` updated to accept explicit `(row, col)` params instead of reading from closure.
2. **Game history is localStorage-only** — `recordGame()` stores history locally; no server-side persistence. History is lost on browser storage clear. (FE-UI-042)
3. **Wire adapter boundary** — `gridAdapters.js` functions are called exclusively in `sudokuApi.js`. Hook state always uses plain arrays; `Grid` wire format never propagates past the API layer. Mock paths also call unwrap helpers since `cannedData.js` is in wire format.
4. **`completedNumbers` computed outside hook** — Calculated in `App.jsx` via `useMemo`, not inside `useSudokuGame`. The hook doesn't know which digits are "complete" — a subtle coupling.
5. **`TutorialModal` markdown source** — All 11 technique files confirmed in `ui/public/techniques/`. `TutorialModal` extended with `src` and `title` props to serve non-technique pages (e.g. `ui/public/help/controls.md`).
6. **`isModalOpen` derived in `App.jsx`** — Keyboard suppression requires knowing when any modal/hint is open. A single boolean OR of `newGameModalOpen || importModalOpen || devDataOpen || helpOpen || gameStatus === 'solved' || !!activeHint` is passed to `useKeyboardInput`; the hook has no knowledge of which modals exist.

## Work Required

### Done
1. ~~Delete `GameControls.jsx`~~ — deleted; it was a dead stub returning null. (no spec affected)
2. ~~Verify tutorial markdown files~~ — confirmed all 11 files exist in `ui/public/techniques/`; added source comment in `TutorialModal.jsx`. (FE-UI-015)
3. ~~Add wire adapters~~ — `gridAdapters.js` created; `sudokuApi.js` wraps/unwraps at boundary. (DT-UI-001 to 009)
4. ~~Update error handler~~ — `apiFetch` reads `errorBody.message` first, falls back to `errorBody.error`. (DT-UI-008)
5. ~~Update `cannedData.js`~~ — All grid fields now in `{rows: [...]}` wire format. (DT-UI-009)
6. ~~Mock mode `solutionGrid: null`~~ — `cannedData.js` now includes `solutionGrid` for canned puzzles; mock mode can exercise solution-comparison validation.

### Nice to Have
7. Persist game history to the server (e.g., via a GET /players/me/history endpoint) so it survives browser storage clears. (FE-UI-042)
8. Decompose `useSudokuGame` into smaller focused hooks (useGameTimer, useHintState, useGamePersistence) to improve testability.
