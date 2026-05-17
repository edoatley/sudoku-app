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

    await page.getByRole('button', { name: /game menu/i }).click();
    await page.getByRole('menuitem', { name: /new game/i }).click();
    await page.getByRole('radio', { name: /hard/i }).click();
    await page.getByRole('button', { name: /start/i }).click();

    // Grid should still be visible after new game loads
    await waitForGrid(page);
  });
});

test.describe('Validate board', () => {
  test('valid partial board shows no error alert', async ({ page }) => {
    // Clear any saved game so the app generates a fresh puzzle with no user entries.
    // Without this, a previously modified game in localStorage could have errors,
    // causing Check to report conflicts on the restored board.
    await page.goto('/');
    await page.evaluate(() => localStorage.removeItem('sudoku_gameId'));
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

    // Hint panel should appear with some hint content
    await expect(page.getByTestId('hint-panel')).toBeVisible({ timeout: 10_000 });
    const hintPanel = page.getByTestId('hint-panel');
    await expect(hintPanel).not.toBeEmpty();
  });
});

test.describe('Fill candidates', () => {
  test('clicking Fill shows candidates in empty cells', async ({ page }) => {
    // Intercept the candidates API call to verify it is made
    const candidatesResponse = page.waitForResponse(
      (res) => res.url().includes('/puzzles/candidates') && res.status() === 200,
      { timeout: 10_000 }
    );

    await page.goto('/');
    await waitForGrid(page);

    await page.getByRole('button', { name: /^fill$/i }).click();

    // Verify the candidates endpoint was called successfully
    await candidatesResponse;

    // After Fill, at least one empty cell should contain a 3×3 grid
    // of candidate numbers (9 child boxes inside the cell)
    await expect(async () => {
      const cells = await page.locator('[data-testid^="cell-"]').all();
      let foundCandidate = false;
      for (const cell of cells) {
        const childCount = await cell.locator('> div > div').count();
        if (childCount === 9) { foundCandidate = true; break; }
      }
      expect(foundCandidate).toBe(true);
    }).toPass({ timeout: 10_000 });
  });
});

test.describe('Game persistence (real backend + DynamoDB via LocalStack)', () => {
  test('POST /games returns a gameId and the UI stores it in localStorage', async ({ page }) => {
    await page.goto('/');
    await waitForGrid(page);

    const storedId = await page.evaluate(() => localStorage.getItem('sudoku_gameId'));
    expect(storedId).toBeTruthy();
    // UUID format
    expect(storedId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);
  });

  test('GET /games/:id returns the same game state after a page reload', async ({ page }) => {
    await page.goto('/');
    await waitForGrid(page);

    const gameId = await page.evaluate(() => localStorage.getItem('sudoku_gameId'));
    expect(gameId).toBeTruthy();

    // Reload — the hook should call GET /games/:id and restore the board
    await page.reload();
    await waitForGrid(page);

    // gameId in localStorage should be unchanged
    const gameIdAfterReload = await page.evaluate(() => localStorage.getItem('sudoku_gameId'));
    expect(gameIdAfterReload).toBe(gameId);
  });

  test('entering a cell value and hiding the tab sends PATCH /games/:id', async ({ page }) => {
    const patchRequests = [];
    page.on('request', (req) => {
      if (req.method() === 'PATCH' && req.url().includes('/games/')) {
        patchRequests.push(req);
      }
    });

    await page.goto('/');
    await waitForGrid(page);

    // Enter a value in an empty cell (scope to normal pad to avoid ambiguity with candidate row)
    await page.getByTestId('numberpad-normal').getByRole('button', { name: '4', exact: true }).click();
    await page.getByTestId('cell-0-2').click();

    // Simulate tab hide to trigger immediate sync
    await page.evaluate(() => {
      Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true });
      document.dispatchEvent(new Event('visibilitychange'));
    });

    await expect.poll(() => patchRequests.length, { timeout: 5000 }).toBeGreaterThan(0);

    const body = JSON.parse(patchRequests[0].postData() || '{}');
    expect(body.currentGrid).toBeDefined();
    expect(body.timeSpentSeconds).toBeGreaterThanOrEqual(0);
  });
});
