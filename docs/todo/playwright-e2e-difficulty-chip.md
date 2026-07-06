# Playwright E2E Test for Difficulty Chip in Header

**Summary:** Add a Playwright e2e spec that verifies the colour-coded difficulty chip renders in the header when a game is active, so the feature is regression-protected.

**Branch context:** `rc-ai-coach` — difficulty chip added to Header in commit `9e6cd5f` alongside AI coach interaction logging

## Why deferred

Out of scope for the current commit; the feature shipped without a dedicated e2e test covering the chip's visibility, label text, and per-difficulty colour coding.

## Context

**Relevant files:**
- `ui/src/components/Header.jsx` — renders the `<Chip>` at lines 236–247; it only appears when `gameStarted && difficulty` are both truthy; label is `difficulty` capitalised; border colour comes from `DIFFICULTY_COLORS` (`easy`→green, `medium`→orange, `hard`→red, `imported`→purple)
- `ui/tests/e2e/helpers.js` — exports `setupGameRoutes`, `waitForGrid`, `toWireGameState`, `CANNED_GAME_STATE` (easy game, `difficulty: 'easy'`)
- `ui/tests/e2e/new-game.spec.js` — closest existing test; shows pattern for overriding difficulty in POST response
- `ui/tests/e2e/player-journey.spec.js` — shows full game-state fixture construction pattern

**Current state:**
The Header renders a MUI `<Chip variant="outlined" size="small">` with the capitalised difficulty label and a coloured border only after a game has loaded (`gameStarted && difficulty`). The chip has no `data-testid`; selectors must use `getByRole('generic', { name: ... })` or `page.getByText` / `locator('.MuiChip-label')`. No existing e2e test exercises this chip at all.

**Key constraints:**
- No spec ID exists yet for this chip — if one is needed, add `FE-UI-053` to `docs/specs/react-frontend-specs.md` following the pattern on line 56.
- The chip is conditional on `gameStarted`; `setupGameRoutes` + `waitForGrid` are sufficient to reach that state.
- Colour is applied as a CSS `borderColor` inline style via MUI `sx`; assert it via `toHaveCSS` or inspect the `style` attribute if needed (optional — label + visibility may be enough).

## What to do

1. Create `ui/tests/e2e/difficulty-chip.spec.js`.
2. Import `test, expect` from `@playwright/test` and `setupGameRoutes, waitForGrid, CANNED_GAME_STATE, toWireGameState` from `./helpers.js`.
3. Write a test **"difficulty chip — shows 'Easy' label when game starts"**: call `setupGameRoutes(page)` (which seeds an easy game), `page.goto('/')`, `waitForGrid(page)`, then assert `page.getByText('Easy')` is visible.
4. Write a test **"difficulty chip — shows 'Hard' label after starting a hard game"**: override the GET route to return a hard-difficulty game state (clone `CANNED_GAME_STATE` with `difficulty: 'hard'`), navigate, and assert `page.getByText('Hard')` is visible.
5. (Optional) Write a test **"difficulty chip — not visible before game starts"**: skip `setupGameRoutes`, go to `/` without a seeded `sudoku_gameId`, and assert the chip text (`Easy`, `Medium`, `Hard`) is not present.
6. Run `npx playwright test difficulty-chip` from `ui/` to verify all pass.

## Acceptance criteria

- [ ] `ui/tests/e2e/difficulty-chip.spec.js` exists and has at least 2 passing tests
- [ ] Easy game shows chip labelled "Easy"
- [ ] Hard game (overridden fixture) shows chip labelled "Hard"
- [ ] Tests do not require network — all routes are mocked via `page.route`
- [ ] `npx playwright test` green with no regressions in other specs

## Related specs / docs

- [`docs/specs/react-frontend-specs.md`](../specs/react-frontend-specs.md) — FE-UI section (add FE-UI-053 if a spec entry is required)
- [`docs/llds/react-frontend.md`](../llds/react-frontend.md) — Implementation Standards section governs Header component conventions
