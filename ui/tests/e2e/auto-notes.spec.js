import { test, expect } from '@playwright/test';
import { setupGameRoutes, waitForGrid } from './helpers.js';

// Row 0, col 2 candidates: [1, 2, 4, 6]
const CANNED_CANDIDATES = {
  candidatesGrid: [
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
};

async function setupRoutes(page) {
  await setupGameRoutes(page);
  await page.route('**/puzzles/candidates', (route) =>
    route.fulfill({ json: CANNED_CANDIDATES })
  );
}

test('auto-notes — clicking Auto-Notes shows candidates in empty cells', async ({ page }) => {
  await setupRoutes(page);
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: 'Notes' }).click();

  // Row 0 col 2 has candidate 4 — should be rendered by CandidateDisplay
  await expect(page.getByTestId('cell-0-2')).toContainText('4');
});

test('auto-notes — clicking Auto-Notes again hides candidates', async ({ page }) => {
  await setupRoutes(page);
  await page.goto('/');
  await waitForGrid(page);

  // Activate
  await page.getByRole('button', { name: 'Notes' }).click();
  await expect(page.getByTestId('cell-0-2')).toContainText('4');

  // Deactivate — no second API call, autoNotesActive toggles off
  await page.getByRole('button', { name: 'Notes' }).click();
  await expect(page.getByTestId('cell-0-2')).not.toContainText('4');
});
