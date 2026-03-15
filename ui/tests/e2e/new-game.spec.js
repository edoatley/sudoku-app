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

test('new-game — selecting Hard difficulty and starting a new game loads the puzzle', async ({ page }) => {
  let callCount = 0;
  await page.route('**/puzzles/generate**', (route) => {
    callCount += 1;
    route.fulfill({ json: callCount === 1 ? EASY_PUZZLE : HARD_PUZZLE });
  });

  await page.goto('/');
  await waitForGrid(page);

  // Change difficulty to Hard
  await page.getByLabel('Difficulty').click();
  await page.getByRole('option', { name: 'Hard' }).click();

  // Start new game
  await page.getByRole('button', { name: 'New Game' }).click();

  // Grid should still be visible after reload
  await expect(page.getByTestId('cell-0-0')).toBeVisible();
});
