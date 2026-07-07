import { test, expect } from '@playwright/test';
import { setupGameRoutes, waitForGrid } from './helpers.js';

const VALID_RESPONSE = {
  isValid: true,
  isSolved: false,
  errors: [],
};

const INVALID_RESPONSE = {
  isValid: false,
  isSolved: false,
  errors: [
    { row: 0, col: 0 },
    { row: 0, col: 2 },
  ],
};

async function setupRoutes(page, validateResponse) {
  await setupGameRoutes(page);
  await page.route('**/puzzles/validate', (route) => route.fulfill({ json: validateResponse }));
}

async function fillRow0Correctly(page) {
  const fills = [
    { col: 2, number: 4 },
    { col: 3, number: 6 },
    { col: 5, number: 8 },
    { col: 6, number: 9 },
    { col: 7, number: 1 },
    { col: 8, number: 2 },
  ];
  for (const { col, number } of fills) {
    await page
      .getByTestId('numberpad-normal')
      .getByRole('button', { name: String(number), exact: true })
      .click();
    await page.getByTestId(`cell-0-${col}`).click();
  }
}

test('valid — fill row 0 with correct values and validate', async ({ page }) => {
  await setupRoutes(page, VALID_RESPONSE);
  await page.goto('/');
  await waitForGrid(page);

  await fillRow0Correctly(page);

  await page.getByRole('button', { name: 'Check' }).click();

  const alert = page.getByTestId('status-alert');
  await expect(alert).toBeVisible();
  await expect(alert).toHaveAttribute('class', /MuiAlert-colorSuccess/);
  await expect(alert).toContainText('Board is valid so far.');
});

test('invalid — enter a duplicate value in row 0 and validate', async ({ page }) => {
  await setupRoutes(page, INVALID_RESPONSE);
  await page.goto('/');
  await waitForGrid(page);

  // Row 0 col 0 is already given as 5 — enter 5 at col 2 to create a duplicate
  await page.getByTestId('numberpad-normal').getByRole('button', { name: '5', exact: true }).click();
  await page.getByTestId('cell-0-2').click();

  await page.getByRole('button', { name: 'Check' }).click();

  const alert = page.getByTestId('status-alert');
  await expect(alert).toBeVisible();
  await expect(alert).toHaveAttribute('class', /MuiAlert-colorWarning/);
  await expect(alert).toContainText('The board has 2 errors. Check the highlighted cells.');
});
