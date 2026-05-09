import { test, expect } from '@playwright/test';
import { setupGameRoutes, waitForGrid } from './helpers.js';

// EASY_PUZZLE row 0: [5, 3, 0, 0, 7, 0, 0, 0, 0]
// Given cells: col 0 (5), col 1 (3), col 4 (7)
// Empty cells: col 2, 3, 5, 6, 7, 8

test('undo is disabled initially', async ({ page }) => {
  await setupGameRoutes(page);
  await page.goto('/');
  await waitForGrid(page);

  await expect(page.getByRole('button', { name: 'undo' })).toBeDisabled();
});

test('undo restores a normal-mode fill', async ({ page }) => {
  await setupGameRoutes(page);
  await page.goto('/');
  await waitForGrid(page);

  await page.getByTestId('numberpad-normal').getByRole('button', { name: '4', exact: true }).click();
  await page.getByTestId('cell-0-2').click();
  await expect(page.getByTestId('cell-0-2')).toContainText('4');

  await page.getByRole('button', { name: 'undo' }).click();
  await expect(page.getByTestId('cell-0-2')).not.toContainText('4');
});

test('undo re-disables after undoing the only move', async ({ page }) => {
  await setupGameRoutes(page);
  await page.goto('/');
  await waitForGrid(page);

  await page.getByTestId('numberpad-normal').getByRole('button', { name: '4', exact: true }).click();
  await page.getByTestId('cell-0-2').click();

  const undoBtn = page.getByRole('button', { name: 'undo' });
  await expect(undoBtn).toBeEnabled();

  await undoBtn.click();
  await expect(undoBtn).toBeDisabled();
});

test('undo restores candidates in candidate mode', async ({ page }) => {
  await setupGameRoutes(page);
  await page.goto('/');
  await waitForGrid(page);

  await page.getByTestId('numberpad-candidate').getByRole('button', { name: '3', exact: true }).click();
  await page.getByTestId('cell-0-2').click();

  // Candidate 3 should now be visible in cell-0-2
  const cell = page.getByTestId('cell-0-2');
  await expect(cell).toContainText('3');

  await page.getByRole('button', { name: 'undo' }).click();

  // After undo the candidate text should be transparent/gone
  await expect(cell).not.toContainText('3');
});

test('undo button remains disabled when no moves made', async ({ page }) => {
  await setupGameRoutes(page);
  await page.goto('/');
  await waitForGrid(page);

  const undoBtn = page.getByRole('button', { name: 'undo' });
  await expect(undoBtn).toBeDisabled();

  // Click a given cell — should not enable undo
  await page.getByTestId('cell-0-0').click();
  await expect(undoBtn).toBeDisabled();
});
