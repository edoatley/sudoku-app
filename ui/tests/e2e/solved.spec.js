import { test, expect } from '@playwright/test';
import { CANNED_GAME_ID, waitForGrid, toWireGameState } from './helpers.js';

// Near-complete puzzle: only cell-8-8 is empty (should be 9)
const NEAR_COMPLETE_GRID = [
  [5, 3, 4, 6, 7, 8, 9, 1, 2],
  [6, 7, 2, 1, 9, 5, 3, 4, 8],
  [1, 9, 8, 3, 4, 2, 5, 6, 7],
  [8, 5, 9, 7, 6, 1, 4, 2, 3],
  [4, 2, 6, 8, 5, 3, 7, 9, 1],
  [7, 1, 3, 9, 2, 4, 8, 5, 6],
  [9, 6, 1, 5, 3, 7, 2, 8, 4],
  [2, 8, 7, 4, 1, 9, 6, 3, 5],
  [3, 4, 5, 2, 8, 6, 1, 7, 0],
];

const NEAR_COMPLETE_GAME_STATE = toWireGameState({
  gameId: CANNED_GAME_ID,
  difficulty: 'easy',
  originalGrid: NEAR_COMPLETE_GRID,
  currentGrid: NEAR_COMPLETE_GRID.map((r) => [...r]),
  solutionGrid: NEAR_COMPLETE_GRID.map((r) => [...r]),
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

async function setupRoutes(page) {
  await page.addInitScript((gameId) => {
    localStorage.setItem('sudoku_gameId', gameId);
  }, CANNED_GAME_ID);
  await page.route(`**/games/${CANNED_GAME_ID}`, (route) => {
    if (route.request().method() === 'GET') {
      route.fulfill({ status: 200, json: NEAR_COMPLETE_GAME_STATE });
    } else if (route.request().method() === 'PATCH') {
      route.fulfill({ status: 200, body: '' });
    } else {
      route.continue();
    }
  });
  await page.route('**/puzzles/validate', (route) =>
    route.fulfill({ json: { isValid: true, isSolved: true, errors: [] } })
  );
}

test('solved state shows congratulations dialog', async ({ page }) => {
  await setupRoutes(page);
  await page.goto('/');
  await waitForGrid(page);

  await page.getByTestId('numberpad-normal').getByRole('button', { name: '9', exact: true }).click();
  await page.getByTestId('cell-8-8').click();

  const dialog = page.getByTestId('congrats-dialog');
  await expect(dialog).toBeVisible();
  await expect(dialog).toContainText('Congratulations');
  await expect(dialog).toContainText(/\d+m \d+s/);
});

test('solved dialog disappears and clears grid after Finish', async ({ page }) => {
  await setupRoutes(page);
  await page.goto('/');
  await waitForGrid(page);

  await page.getByTestId('numberpad-normal').getByRole('button', { name: '9', exact: true }).click();
  await page.getByTestId('cell-8-8').click();

  await expect(page.getByTestId('congrats-dialog')).toBeVisible();

  await page.getByTestId('finish-button').click();

  await expect(page.getByTestId('congrats-dialog')).not.toBeVisible();
  await expect(page.getByTestId('cell-0-0')).not.toBeVisible();
});
