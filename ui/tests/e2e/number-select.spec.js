import { test, expect } from '@playwright/test';
import { setupGenerateRoute, waitForGrid } from './helpers.js';

// EASY_PUZZLE row 0: [5, 3, 0, 0, 7, 0, 0, 0, 0]
// Given cells: col 0 (5), col 1 (3), col 4 (7)
// Empty cells: col 2, 3, 5, 6, 7, 8

test('number-first flow — select number then cell', async ({ page }) => {
  await setupGenerateRoute(page);
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: '6', exact: true }).click();
  await page.getByTestId('cell-0-3').click();

  await expect(page.getByTestId('cell-0-3')).toContainText('6');
});

test('cell-first flow — select cell then number', async ({ page }) => {
  await setupGenerateRoute(page);
  await page.goto('/');
  await waitForGrid(page);

  await page.getByTestId('cell-0-2').click();
  await page.getByRole('button', { name: '4', exact: true }).click();

  await expect(page.getByTestId('cell-0-2')).toContainText('4');
});

test('clicking same number twice deselects it — cell stays empty', async ({ page }) => {
  await setupGenerateRoute(page);
  await page.goto('/');
  await waitForGrid(page);

  // Click 4 to select, then click 4 again to deselect
  await page.getByRole('button', { name: '4', exact: true }).click();
  await page.getByRole('button', { name: '4', exact: true }).click();

  // Now clicking the cell should not fill it
  await page.getByTestId('cell-0-2').click();
  await expect(page.getByTestId('cell-0-2')).not.toContainText('4');
});

test('candidate toggle-off — adding then removing a candidate', async ({ page }) => {
  await setupGenerateRoute(page);
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: 'Candidate' }).click();

  // First cycle: add candidate 4 to cell-0-2
  await page.getByRole('button', { name: '4', exact: true }).click();
  await page.getByTestId('cell-0-2').click();
  await expect(page.getByTestId('cell-0-2')).toContainText('4');

  // Second cycle: remove candidate 4 from cell-0-2
  await page.getByRole('button', { name: '4', exact: true }).click();
  await page.getByTestId('cell-0-2').click();
  await expect(page.getByTestId('cell-0-2')).not.toContainText('4');
});

test('given cell cannot be overwritten', async ({ page }) => {
  await setupGenerateRoute(page);
  await page.goto('/');
  await waitForGrid(page);

  // cell-0-0 is a given cell with value 5
  await page.getByRole('button', { name: '9', exact: true }).click();
  await page.getByTestId('cell-0-0').click();

  await expect(page.getByTestId('cell-0-0')).toContainText('5');
  await expect(page.getByTestId('cell-0-0')).not.toContainText('9');
});
