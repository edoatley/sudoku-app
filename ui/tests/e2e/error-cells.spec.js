import { test, expect } from '@playwright/test';
import { setupGameRoutes, waitForGrid } from './helpers.js';

// MUI v7 default theme: error.light = rgb(239, 83, 80)
// App uses createTheme() with no overrides, so this value is stable.

async function setupRoutes(page, validateResponse) {
  await setupGameRoutes(page);
  await page.route('**/puzzles/validate', (route) =>
    route.fulfill({ json: validateResponse })
  );
}

test('single error cell gets error background', async ({ page }) => {
  await setupRoutes(page, {
    isValid: false,
    isSolved: false,
    errors: [{ row: 0, col: 2 }],
  });
  await page.goto('/');
  await waitForGrid(page);

  await page.getByTestId('numberpad-normal').getByRole('button', { name: '5', exact: true }).click();
  await page.getByTestId('cell-0-2').click();

  await page.getByRole('button', { name: 'Check' }).click();

  await expect(page.getByTestId('cell-0-2')).toHaveCSS('background-color', 'rgb(239, 83, 80)');
  await expect(page.getByTestId('cell-0-3')).not.toHaveCSS('background-color', 'rgb(239, 83, 80)');
});

test('multiple error cells all highlighted', async ({ page }) => {
  await setupRoutes(page, {
    isValid: false,
    isSolved: false,
    errors: [{ row: 0, col: 2 }, { row: 0, col: 3 }],
  });
  await page.goto('/');
  await waitForGrid(page);

  await page.getByTestId('numberpad-normal').getByRole('button', { name: '5', exact: true }).click();
  await page.getByTestId('cell-0-2').click();

  await page.getByRole('button', { name: 'Check' }).click();

  await expect(page.getByTestId('cell-0-2')).toHaveCSS('background-color', 'rgb(239, 83, 80)');
  await expect(page.getByTestId('cell-0-3')).toHaveCSS('background-color', 'rgb(239, 83, 80)');
});

test('error background clears when new value entered in error cell', async ({ page }) => {
  await setupRoutes(page, {
    isValid: false,
    isSolved: false,
    errors: [{ row: 0, col: 2 }],
  });
  await page.goto('/');
  await waitForGrid(page);

  // Trigger error on cell-0-2
  await page.getByTestId('numberpad-normal').getByRole('button', { name: '5', exact: true }).click();
  await page.getByTestId('cell-0-2').click();
  await page.getByRole('button', { name: 'Check' }).click();
  await expect(page.getByTestId('cell-0-2')).toHaveCSS('background-color', 'rgb(239, 83, 80)');

  // Enter a new value — error state should clear
  await page.getByTestId('numberpad-normal').getByRole('button', { name: '4', exact: true }).click();
  await page.getByTestId('cell-0-2').click();

  await expect(page.getByTestId('cell-0-2')).not.toHaveCSS('background-color', 'rgb(239, 83, 80)');
});
