import { test, expect } from '@playwright/test';
import { setupGenerateRoute, waitForGrid } from './helpers.js';

const HINT_RESPONSE = {
  techniqueName: 'Naked Single',
  markdownSlug: 'naked-single',
  difficulty: 'easy',
  nudge: 'There is a Naked Single hiding somewhere on the board.',
  focus: 'Look closely at cell (0, 2) — its row, column, and box together eliminate 8 numbers.',
  reveal: 'Cell (0, 2) can only be 4. All other numbers appear in its row, column, or 3×3 block.',
  highlightCells: [{ row: 0, col: 2 }],
  eliminatedCandidates: [],
  solvedCells: [{ row: 0, col: 2, value: 4 }],
};

test('hint — clicking Hint shows the hint dialog with nudge text', async ({ page }) => {
  await setupGenerateRoute(page);
  await page.route('**/puzzles/hint', (route) =>
    route.fulfill({ json: HINT_RESPONSE })
  );
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: 'Hint' }).click();

  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByRole('dialog')).toContainText('There is a Naked Single hiding somewhere on the board.');
});

test('hint — advancing to reveal fills the hinted cell with the correct value', async ({ page }) => {
  await setupGenerateRoute(page);
  await page.route('**/puzzles/hint', (route) =>
    route.fulfill({ json: HINT_RESPONSE })
  );
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: 'Hint' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();

  // nudge → focus
  await page.getByRole('button', { name: 'Next Hint' }).click();
  // focus → reveal (fills cell)
  await page.getByRole('button', { name: 'Show Me' }).click();

  await expect(page.getByTestId('cell-0-2')).toContainText('4');

  // dismiss
  await page.getByRole('button', { name: 'Got It' }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
});
