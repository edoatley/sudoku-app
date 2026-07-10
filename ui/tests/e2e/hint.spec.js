import { test, expect } from '@playwright/test';
import { setupGameRoutes, waitForGrid } from './helpers.js';

const HINT_RESPONSE = {
  techniqueName: 'Naked Single',
  markdownSlug: 'naked-single',
  difficulty: 'easy',
  strategyRank: 20,
  nudge: 'There is a Naked Single hiding somewhere on the board.',
  focus: 'Look closely at cell (0, 2) — its row, column, and box together eliminate 8 numbers.',
  reveal: 'Cell (0, 2) can only be 4. All other numbers appear in its row, column, or 3×3 block.',
  highlightCells: [{ row: 0, col: 2 }],
  eliminatedCandidates: [],
  solvedCells: [{ row: 0, col: 2, value: 4 }],
};

const ALTERNATE_HINT_RESPONSE = {
  techniqueName: 'Hidden Single',
  markdownSlug: 'hidden-single',
  difficulty: 'easy',
  strategyRank: 40,
  nudge: 'A digit appears in only one cell of a unit.',
  focus: 'Look at row 1.',
  reveal: 'Cell (1, 1) must be 7.',
  highlightCells: [{ row: 1, col: 1 }],
  eliminatedCandidates: [],
  solvedCells: [{ row: 1, col: 1, value: 7 }],
};

test('hint — clicking Hint shows the hint dialog with nudge text', async ({ page }) => {
  await setupGameRoutes(page);
  await page.route('**/puzzles/hint', (route) => route.fulfill({ json: HINT_RESPONSE }));
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: 'Hint' }).click();

  await expect(page.getByTestId('hint-panel')).toBeVisible();
  await expect(page.getByTestId('hint-panel')).toContainText('There is a Naked Single hiding somewhere on the board.');
});

test('hint — advancing to reveal fills the hinted cell with the correct value', async ({ page }) => {
  await setupGameRoutes(page);
  await page.route('**/puzzles/hint', (route) => route.fulfill({ json: HINT_RESPONSE }));
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: 'Hint' }).click();
  await expect(page.getByTestId('hint-panel')).toBeVisible();

  // nudge → focus → reveal (fills cell). Wait for the focus-stage text to actually render
  // before the second click — firing both clicks back-to-back races the first stage
  // transition's React commit under a slow/loaded runner (observed flake in CI).
  // Note: formatHintText converts 0-indexed coordinates in the raw text to 1-indexed for
  // display (HINT_RESPONSE.focus's "(0, 2)" renders as "(1, 3)"), so match on a
  // coordinate-free substring rather than the raw fixture text.
  await page.getByRole('button', { name: 'Show Me' }).click();
  await expect(page.getByTestId('hint-panel')).toContainText('eliminate 8 numbers');
  await page.getByRole('button', { name: 'Show Me' }).click();

  await expect(page.getByTestId('cell-0-2')).toContainText('4');

  // dismiss
  await page.getByRole('button', { name: 'Got It' }).click();
  await expect(page.getByTestId('hint-panel')).not.toBeVisible();
});

test('hint — "Try Different Hint" button appears at nudge stage', async ({ page }) => {
  await setupGameRoutes(page);
  await page.route('**/puzzles/hint', (route) => route.fulfill({ json: HINT_RESPONSE }));
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: 'Hint' }).click();
  await expect(page.getByTestId('hint-panel')).toBeVisible();

  await expect(page.getByRole('button', { name: 'Try Different Hint' })).toBeVisible();
});

test('hint — "Try Different Hint" not visible at focus stage', async ({ page }) => {
  await setupGameRoutes(page);
  await page.route('**/puzzles/hint', (route) => route.fulfill({ json: HINT_RESPONSE }));
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: 'Hint' }).click();
  await expect(page.getByTestId('hint-panel')).toBeVisible();
  await page.getByRole('button', { name: 'Show Me' }).click();

  await expect(page.getByRole('button', { name: 'Try Different Hint' })).not.toBeVisible();
});

test('hint — "Try Different Hint" sends new request and shows different technique', async ({ page }) => {
  await setupGameRoutes(page);
  let callCount = 0;
  await page.route('**/puzzles/hint', (route) => {
    callCount++;
    route.fulfill({ json: callCount === 1 ? HINT_RESPONSE : ALTERNATE_HINT_RESPONSE });
  });
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: 'Hint' }).click();
  await expect(page.getByTestId('hint-panel')).toContainText('Naked Single');

  await page.getByRole('button', { name: 'Try Different Hint' }).click();

  await expect(page.getByTestId('hint-panel')).toContainText('Hidden Single');
});

test('hint — second Hint request auto-skips first technique', async ({ page }) => {
  await setupGameRoutes(page);
  const requests = [];
  await page.route('**/puzzles/hint', (route) => {
    requests.push(JSON.parse(route.request().postData()));
    route.fulfill({ json: requests.length === 1 ? HINT_RESPONSE : ALTERNATE_HINT_RESPONSE });
  });
  await page.goto('/');
  await waitForGrid(page);

  // First hint: get Naked Single, dismiss
  await page.getByRole('button', { name: 'Hint' }).click();
  await expect(page.getByTestId('hint-panel')).toBeVisible();
  await page.getByRole('button', { name: 'Close' }).click();
  await expect(page.getByTestId('hint-panel')).not.toBeVisible();

  // Second hint request — should include excludedRanks
  await page.getByRole('button', { name: 'Hint', exact: true }).click();

  expect(requests).toHaveLength(2);
  expect(requests[1].excludedRanks).toContain(20);
});
