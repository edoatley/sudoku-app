import { test, expect } from '@playwright/test';
import { setupGameRoutes, waitForGrid } from './helpers.js';

test('candidate mode — adding a candidate shows it as a small number in the cell', async ({ page }) => {
  await setupGameRoutes(page);
  await page.goto('/');
  await waitForGrid(page);

  // Select 4 from candidate row (switches to candidate mode automatically)
  await page.getByTestId('numberpad-candidate').getByRole('button', { name: '4', exact: true }).click();
  // Click empty cell at row 0, col 2
  await page.getByTestId('cell-0-2').click();

  // Candidate 4 should be rendered inside CandidateDisplay
  await expect(page.getByTestId('cell-0-2')).toContainText('4');
});

test('candidate mode — normal mode fills the cell with the selected number', async ({ page }) => {
  await setupGameRoutes(page);
  await page.goto('/');
  await waitForGrid(page);

  // Select number 4 from normal row — Normal mode is the default
  await page.getByTestId('numberpad-normal').getByRole('button', { name: '4', exact: true }).click();
  // Click empty cell at row 0, col 2
  await page.getByTestId('cell-0-2').click();

  // Cell should show the large fill value
  await expect(page.getByTestId('cell-0-2')).toContainText('4');
});
