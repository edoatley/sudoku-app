import { expect } from '@playwright/test';

/** Wrap a plain 2D array in the {rows:[...]} wire format the backend emits. */
export function toWireGrid(grid) {
  return { rows: grid };
}

/** Wrap a plain 3D candidates array in the {rows:[...]} wire format the backend emits. */
export function toWireCandidates(candidates) {
  return { rows: candidates };
}

/**
 * Convert a game state object whose grid fields are plain arrays into the wire
 * format that the backend returns, so unwrapGameState() can correctly convert them.
 */
export function toWireGameState(state) {
  return {
    ...state,
    ...(state.originalGrid && !state.originalGrid.rows && { originalGrid: toWireGrid(state.originalGrid) }),
    ...(state.currentGrid && !state.currentGrid.rows && { currentGrid: toWireGrid(state.currentGrid) }),
    ...(state.solutionGrid && !state.solutionGrid.rows && { solutionGrid: toWireGrid(state.solutionGrid) }),
    ...(state.candidates && !state.candidates.rows && { candidates: toWireCandidates(state.candidates) }),
  };
}

// Easy puzzle — row 0: [5, 3, 0, 0, 7, 0, 0, 0, 0]
// Row 0 solution:       [5, 3, 4, 6, 7, 8, 9, 1, 2]
// Given cells (not clickable): col 0 (5), col 1 (3), col 4 (7)
// Empty cells to fill:  col 2→4, col 3→6, col 5→8, col 6→9, col 7→1, col 8→2
// Wire-format grid rows (as the real backend returns them: {rows: [...]})
const EASY_ROWS = [
  [5, 3, 0, 0, 7, 0, 0, 0, 0],
  [6, 0, 0, 1, 9, 5, 0, 0, 0],
  [0, 9, 8, 0, 0, 0, 0, 6, 0],
  [8, 0, 0, 0, 6, 0, 0, 0, 3],
  [4, 0, 0, 8, 0, 3, 0, 0, 1],
  [7, 0, 0, 0, 2, 0, 0, 0, 6],
  [0, 6, 0, 0, 0, 0, 2, 8, 0],
  [0, 0, 0, 4, 1, 9, 0, 0, 5],
  [0, 0, 0, 0, 8, 0, 0, 7, 9],
];

// Easy puzzle — row 0: [5, 3, 0, 0, 7, 0, 0, 0, 0]
// Row 0 solution:       [5, 3, 4, 6, 7, 8, 9, 1, 2]
// Given cells (not clickable): col 0 (5), col 1 (3), col 4 (7)
// Empty cells to fill:  col 2→4, col 3→6, col 5→8, col 6→9, col 7→1, col 8→2
export const EASY_PUZZLE = {
  difficulty: 'easy',
  originalGrid: EASY_ROWS,
};

export const CANNED_GAME_ID = '123e4567-e89b-12d3-a456-426614174000';

// Wire format matching what the real Java backend returns — grids are {rows:[...]} objects.
// unwrapGameState() in sudokuApi.js converts them to plain arrays for internal app state.
export const CANNED_GAME_STATE = {
  gameId: CANNED_GAME_ID,
  difficulty: 'easy',
  originalGrid: { rows: EASY_ROWS },
  currentGrid: { rows: EASY_ROWS.map((r) => [...r]) },
  solutionGrid: { rows: EASY_ROWS.map((r) => [...r]) },
  candidates: {
    rows: Array(9)
      .fill(null)
      .map(() =>
        Array(9)
          .fill(null)
          .map(() => [])
      ),
  },
  timeSpentSeconds: 0,
  status: 'IN_PROGRESS',
};

/**
 * Mock all three /games endpoints and seed localStorage so the game loads on page.goto('/').
 * Registers an initScript that sets sudoku_gameId before each navigation, so the app
 * resumes the saved game via GET /games/:id rather than showing an empty board.
 *
 * POST /games      → 201 gameState  (for new-game flows)
 * GET  /games/:id  → 200 gameState  (for resume / all other tests)
 * PATCH /games/:id → 200 (empty body)
 */
export async function setupGameRoutes(page, overrides = {}) {
  const gameState = { ...CANNED_GAME_STATE, ...overrides };

  // Seed the gameId into localStorage before each page load so the hook resumes it
  await page.addInitScript((gameId) => {
    localStorage.setItem('sudoku_gameId', gameId);
  }, gameState.gameId);

  await page.route('**/games', (route) => {
    if (route.request().method() === 'POST') {
      route.fulfill({ status: 201, json: gameState });
    } else {
      route.continue();
    }
  });
  await page.route('**/games/**', (route) => {
    if (route.request().method() === 'GET') {
      route.fulfill({ status: 200, json: gameState });
    } else if (route.request().method() === 'PATCH') {
      route.fulfill({ status: 200, body: '' });
    } else {
      route.continue();
    }
  });
}

export async function waitForGrid(page) {
  await expect(page.getByTestId('cell-0-0')).toBeVisible();
}
