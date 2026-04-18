# Arrow: React Frontend

Browser SPA — game UI, hint UX, state hooks, API client, localStorage + DynamoDB dual persistence.

## Status

**MAPPED** - 2026-04-18. All source files read and documented. No component or E2E tests audited yet.

## References

### HLD
- docs/high-level-design.md — "Two-Layer Persistence", "Progressive Hint Disclosure" sections

### LLD
- docs/llds/react-frontend.md

### EARS
- docs/specs/react-frontend-specs.md (30 specs, all [x])

### Tests
- ui/src/**/*.test.jsx (not yet audited)
- ui/e2e/ (not yet audited)

### Code
- ui/src/App.jsx, ui/src/main.jsx
- ui/src/api/sudokuApi.js
- ui/src/hooks/useSudokuGame.js
- ui/src/hooks/usePlayerProfile.js
- ui/src/components/ (all component files)
- ui/src/utils/avatarIcons.js
- ui/src/mocks/cannedData.js

## Architecture

**Purpose:** Deliver the complete user experience in the browser — game play, hints, image import, profile, history, and developer tooling.

**Key Components:**
1. `useSudokuGame` — owns game state, grid logic, hint escalation, undo, auto-save, timer, localStorage sync
2. `usePlayerProfile` — owns user identity, avatar, game history
3. `sudokuApi.js` — all backend communication with mock mode, auth token injection, ForbiddenError handling
4. `SudokuGrid` / `SudokuCell` — responsive grid rendering with 6-priority highlight system
5. `HintDialog` — nudge/focus/reveal progressive disclosure; inline on desktop, modal on mobile
6. `Header` — timer, user menu, game controls, developer submenu

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

**Summary:** 39 of 39 active specs implemented; 0 deferred; 0 gaps.

## Key Findings

1. **`useSudokuGame` is oversized** — A single hook owns timer, localStorage, auto-save, hint management, undo, and game lifecycle. Decomposing into smaller hooks would improve testability without changing the external interface.
2. **Game history is localStorage-only** — `recordGame()` stores history locally; no server-side persistence. History is lost on browser storage clear. (FE-UI-042)
3. **`GameControls.jsx` is a dead stub** — The file exists but returns null. Game controls were moved to the Header hamburger. The file should be deleted.
4. **Mock mode canned data has null solutionGrid** — `CANNED_PUZZLES` have no solution grids, so mock-mode validation always uses duplicate detection, never solution-comparison. The solution-comparison path cannot be tested in mock mode.
5. **`completedNumbers` computed outside hook** — Calculated in `App.jsx` via `useMemo`, not inside `useSudokuGame`. The hook doesn't know which digits are "complete" — a subtle coupling.
6. **`TutorialModal` markdown source unclear** — Fetches `/techniques/{slug}.md` at runtime. It is not verified whether these files are committed to the repo or generated at build time.

## Work Required

### Should Fix
1. Delete `GameControls.jsx` — it is a dead stub returning null. (no spec affected)
2. Verify tutorial markdown files are committed to `ui/public/techniques/` or generated at build time and document this clearly. (FE-UI-015)

### Nice to Have
3. Persist game history to the server (e.g., via a GET /players/me/history endpoint) so it survives browser storage clears. (FE-UI-042)
4. Decompose `useSudokuGame` into smaller focused hooks (useGameTimer, useHintState, useGamePersistence) to improve testability.
