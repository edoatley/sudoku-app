import { test, expect } from '@playwright/test';
import { EASY_PUZZLE, waitForGrid } from './helpers.js';

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

function makeGameState(puzzle) {
  return {
    gameId: crypto.randomUUID ? crypto.randomUUID() : '00000000-0000-0000-0000-000000000001',
    difficulty: puzzle.difficulty,
    originalGrid: puzzle.originalGrid,
    currentGrid: puzzle.originalGrid.map((r) => [...r]),
    candidates: Array(9).fill(null).map(() => Array(9).fill(null).map(() => [])),
    timeSpentSeconds: 0,
    status: 'IN_PROGRESS',
  };
}

test('new-game — selecting Hard difficulty and starting a new game loads the puzzle', async ({ page }) => {
  let callCount = 0;
  await page.route('**/games', (route) => {
    if (route.request().method() === 'POST') {
      callCount += 1;
      route.fulfill({ status: 201, json: makeGameState(callCount === 1 ? EASY_PUZZLE : HARD_PUZZLE) });
    } else {
      route.continue();
    }
  });
  await page.route('**/games/**', (route) => {
    if (route.request().method() === 'PATCH') {
      route.fulfill({ status: 200, body: '' });
    } else {
      route.continue();
    }
  });

  await page.goto('/');
  await waitForGrid(page);

  // Open new game modal and select Hard difficulty
  await page.getByRole('button', { name: 'New Game' }).click();
  await page.getByRole('radio', { name: 'Hard' }).click();
  await page.getByRole('button', { name: 'Start' }).click();

  // Grid should still be visible after reload
  await expect(page.getByTestId('cell-0-0')).toBeVisible();
});
