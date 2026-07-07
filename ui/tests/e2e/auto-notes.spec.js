import { test, expect } from '@playwright/test';
import { setupGameRoutes, waitForGrid } from './helpers.js';

// Row 0, col 2 candidates: [1, 2, 4, 6]
// candidatesGrid uses {rows:[...]} wire format to match what the backend returns
// (candidatesFromWire in sudokuApi.js converts it to plain array internally)
const CANNED_CANDIDATES = {
  candidatesGrid: {
    rows: [
      [[], [], [1, 2, 4, 6], [], [], [], [], [], []],
      [[], [], [], [], [], [], [], [], []],
      [[], [], [], [], [], [], [], [], []],
      [[], [], [], [], [], [], [], [], []],
      [[], [], [], [], [], [], [], [], []],
      [[], [], [], [], [], [], [], [], []],
      [[], [], [], [], [], [], [], [], []],
      [[], [], [], [], [], [], [], [], []],
      [[], [], [], [], [], [], [], [], []],
    ],
  },
};

async function setupRoutes(page) {
  await setupGameRoutes(page);
  await page.route('**/puzzles/candidates', (route) => route.fulfill({ json: CANNED_CANDIDATES }));
}

test('fill — clicking Fill fetches candidates and populates player candidates', async ({ page }) => {
  await setupRoutes(page);
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: 'Fill' }).click();

  // Row 0 col 2 has candidate 4 — should be rendered by CandidateDisplay
  await expect(page.getByTestId('cell-0-2')).toContainText('4');
});

test('fill — clicking Undo after Fill restores empty candidate grid', async ({ page }) => {
  await setupRoutes(page);
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: 'Fill' }).click();
  await expect(page.getByTestId('cell-0-2')).toContainText('4');

  await page.getByRole('button', { name: 'Undo' }).click();
  await expect(page.getByTestId('cell-0-2')).not.toContainText('4');
});
