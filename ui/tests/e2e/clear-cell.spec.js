import { test, expect } from '@playwright/test';
import { setupGenerateRoute, waitForGrid } from './helpers.js';

test('clear button removes a user-entered value from the selected cell', async ({ page }) => {
  await setupGenerateRoute(page);
  await page.goto('/');
  await waitForGrid(page);

  // Enter 4 into empty cell at row 0, col 2
  await page.getByRole('button', { name: '4', exact: true }).click();
  await page.getByTestId('cell-0-2').click();
  await expect(page.getByTestId('cell-0-2')).toContainText('4');

  // Cell is still selected; press the clear button
  await page.getByRole('button', { name: 'Clear' }).click();

  // Cell should no longer show 4
  await expect(page.getByTestId('cell-0-2')).not.toContainText('4');
});

test('clear button does not remove a given (pre-filled) cell value', async ({ page }) => {
  await setupGenerateRoute(page);
  await page.goto('/');
  await waitForGrid(page);

  // cell-0-0 is a given (value 5)
  await page.getByTestId('cell-0-0').click();

  // Press the clear button
  await page.getByRole('button', { name: 'Clear' }).click();

  // Given cell should still show 5
  await expect(page.getByTestId('cell-0-0')).toContainText('5');
});

test('clear button is a no-op when no cell is selected', async ({ page }) => {
  await setupGenerateRoute(page);
  await page.goto('/');
  await waitForGrid(page);

  // Press clear without selecting any cell — should not throw
  await page.getByRole('button', { name: 'Clear' }).click();

  // App should still be functional — grid is visible
  await expect(page.getByTestId('cell-0-0')).toBeVisible();
});
