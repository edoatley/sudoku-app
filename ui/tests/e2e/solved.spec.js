import { test, expect } from '@playwright/test';
import { waitForGrid } from './helpers.js';

// Near-complete puzzle: only cell-8-8 is empty (should be 9)
const NEAR_COMPLETE_PUZZLE = {
  difficulty: 'easy',
  originalGrid: [
    [5, 3, 4, 6, 7, 8, 9, 1, 2],
    [6, 7, 2, 1, 9, 5, 3, 4, 8],
    [1, 9, 8, 3, 4, 2, 5, 6, 7],
    [8, 5, 9, 7, 6, 1, 4, 2, 3],
    [4, 2, 6, 8, 5, 3, 7, 9, 1],
    [7, 1, 3, 9, 2, 4, 8, 5, 6],
    [9, 6, 1, 5, 3, 7, 2, 8, 4],
    [2, 8, 7, 4, 1, 9, 6, 3, 5],
    [3, 4, 5, 2, 8, 6, 1, 7, 0],
  ],
};

const VALID_RESPONSE = {
  valid: true,
  errors: [],
  message: 'Board is valid so far.',
};

async function setupRoutes(page) {
  await page.route('**/puzzles/generate**', (route) =>
    route.fulfill({ json: NEAR_COMPLETE_PUZZLE })
  );
  await page.route('**/puzzles/validate', (route) =>
    route.fulfill({ json: VALID_RESPONSE })
  );
}

test('solved state shows congratulations alert', async ({ page }) => {
  await setupRoutes(page);
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: '9', exact: true }).click();
  await page.getByTestId('cell-8-8').click();

  await page.getByRole('button', { name: 'Validate' }).click();

  const alert = page.getByTestId('status-alert');
  await expect(alert).toBeVisible();
  await expect(alert).toContainText('Congratulations — puzzle solved!');
  await expect(alert).toHaveAttribute('class', /MuiAlert-colorSuccess/);
});

test('solved alert can be dismissed', async ({ page }) => {
  await setupRoutes(page);
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: '9', exact: true }).click();
  await page.getByTestId('cell-8-8').click();

  await page.getByRole('button', { name: 'Validate' }).click();

  const alert = page.getByTestId('status-alert');
  await expect(alert).toBeVisible();

  await page.getByRole('button', { name: 'Close' }).click();
  await expect(alert).not.toBeVisible();
});
