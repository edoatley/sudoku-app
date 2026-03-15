import { test, expect } from '@playwright/test';
import { setupGenerateRoute, waitForGrid } from './helpers.js';

const HINT_RESPONSE = {
  coordinate: { row: 0, col: 2 },
  value: 4,
};

test('hint — clicking Hint fills the hinted cell with the correct value', async ({ page }) => {
  await setupGenerateRoute(page);
  await page.route('**/puzzles/hint', (route) =>
    route.fulfill({ json: HINT_RESPONSE })
  );
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: 'Hint' }).click();

  await expect(page.getByTestId('cell-0-2')).toContainText('4');
});
