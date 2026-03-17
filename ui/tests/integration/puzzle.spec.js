/**
 * Integration tests — run against the real backend via Docker Compose.
 * No page.route() mocking: all HTTP calls go to the actual API.
 *
 * Run with:
 *   npx playwright test tests/integration/ --config playwright.integration.config.js
 */
import { test, expect } from '@playwright/test';

async function waitForGrid(page) {
  await expect(page.getByTestId('cell-0-0')).toBeVisible({ timeout: 15_000 });
}

test.describe('Generate puzzle', () => {
  test('loads a 9×9 grid from the real backend', async ({ page }) => {
    await page.goto('/');
    await waitForGrid(page);

    // All 81 cells should be present
    for (let row = 0; row < 9; row++) {
      for (let col = 0; col < 9; col++) {
        await expect(page.getByTestId(`cell-${row}-${col}`)).toBeVisible();
      }
    }
  });

  test('changing difficulty and starting a new game loads a fresh puzzle', async ({ page }) => {
    await page.goto('/');
    await waitForGrid(page);

    await page.getByRole('combobox').selectOption('hard');
    await page.getByRole('button', { name: /new game/i }).click();

    // Grid should still be visible after reload
    await waitForGrid(page);
  });
});

test.describe('Validate board', () => {
  test('valid partial board shows no error alert', async ({ page }) => {
    await page.goto('/');
    await waitForGrid(page);

    await page.getByRole('button', { name: 'Check' }).click();

    // Should not see an error/warning alert (valid partial board)
    await expect(page.getByRole('alert')).not.toContainText(/invalid|error/i);
  });
});

test.describe('Hint', () => {
  test('clicking Hint opens the hint dialog with nudge text', async ({ page }) => {
    await page.goto('/');
    await waitForGrid(page);

    await page.getByRole('button', { name: /hint/i }).click();

    // Dialog should appear with some hint content
    await expect(page.getByRole('dialog')).toBeVisible({ timeout: 10_000 });
    const dialog = page.getByRole('dialog');
    await expect(dialog).not.toBeEmpty();
  });
});

test.describe('Auto-notes (candidates)', () => {
  test('clicking Auto-Notes shows candidates in empty cells', async ({ page }) => {
    await page.goto('/');
    await waitForGrid(page);

    await page.getByRole('button', { name: /auto.?notes/i }).click();

    // At least one cell should now contain candidate digits
    // Find a cell that is empty (no large digit) and check it has a candidate
    const candidateDisplay = page.locator('[data-testid^="cell-"] [class*="candidate"], [data-testid^="cell-"] small').first();
    await expect(candidateDisplay).toBeVisible({ timeout: 10_000 });
  });
});
