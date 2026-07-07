import { test, expect } from '@playwright/test';
import { setupGameRoutes, waitForGrid, toWireGameState } from './helpers.js';

const HARD_PUZZLE = {
  difficulty: 'hard',
  originalGrid: [
    [0, 0, 0, 0, 0, 0, 6, 8, 0],
    [0, 0, 0, 0, 7, 3, 0, 0, 9],
    [3, 0, 9, 0, 0, 0, 0, 4, 5],
    [4, 9, 0, 0, 0, 0, 0, 0, 0],
    [8, 0, 3, 0, 5, 0, 9, 0, 2],
    [0, 0, 0, 0, 0, 0, 0, 3, 6],
    [9, 6, 0, 0, 0, 0, 3, 0, 8],
    [7, 0, 0, 6, 8, 0, 0, 0, 0],
    [0, 2, 8, 0, 0, 0, 0, 0, 0],
  ],
};

// Fixed ID so the GET re-fetch triggered by the useEffect dependency change can be mocked.
const HARD_GAME_ID = '00000000-0000-0000-0000-000000000002';

function makeGameState(puzzle) {
  return toWireGameState({
    gameId: HARD_GAME_ID,
    difficulty: puzzle.difficulty,
    originalGrid: puzzle.originalGrid,
    currentGrid: puzzle.originalGrid.map((r) => [...r]),
    solutionGrid: puzzle.originalGrid.map((r) => [...r]),
    candidates: Array(9)
      .fill(null)
      .map(() =>
        Array(9)
          .fill(null)
          .map(() => [])
      ),
    timeSpentSeconds: 0,
    status: 'IN_PROGRESS',
  });
}

test('new-game — selecting Hard difficulty and starting a new game loads the puzzle', async ({ page }) => {
  // Start with an existing easy game loaded (via GET), then trigger a new Hard game via menu.
  // After startNewGame() resolves, the difficulty state change causes the load useEffect to
  // re-run and re-fetch GET /games/{hardGameId} — mock that too so it returns Hard data.
  const hardGameState = makeGameState(HARD_PUZZLE);

  await setupGameRoutes(page);

  await page.route('**/games/**', (route) => {
    if (route.request().method() === 'GET' && route.request().url().includes(HARD_GAME_ID)) {
      route.fulfill({ status: 200, json: hardGameState });
    } else {
      route.fallback();
    }
  });

  await page.route('**/games', (route) => {
    if (route.request().method() === 'POST') {
      route.fulfill({ status: 201, json: hardGameState });
    } else {
      route.fallback();
    }
  });

  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: 'Game menu' }).click();
  await page.getByRole('menuitem', { name: 'New Game' }).click();
  await page.getByRole('radio', { name: 'Hard' }).click();
  await page.getByRole('button', { name: 'Start' }).click();

  // Hard puzzle cell (0,0) is empty in HARD_PUZZLE.originalGrid
  await expect(page.getByTestId('cell-0-0')).toBeVisible();
  await expect(page.getByTestId('cell-0-0')).not.toContainText(/[1-9]/);
});
