/**
 * Game persistence e2e tests.
 *
 * Verifies that:
 *  - On load the UI calls POST /games and stores the returned gameId
 *  - Entering a cell value persists to localStorage immediately
 *  - Tab hide triggers an immediate PATCH /games/:id
 *  - Completing a puzzle triggers a PATCH with isComplete=true
 *  - On reload with a gameId in localStorage the UI calls GET /games/:id to resume
 */
import { test, expect } from '@playwright/test';
import { CANNED_GAME_STATE, CANNED_GAME_ID, waitForGrid } from './helpers.js';

// Near-complete grid: only cell-8-8 is empty (value = 9 to solve)
const NEAR_COMPLETE_GRID = [
  [5, 3, 4, 6, 7, 8, 9, 1, 2],
  [6, 7, 2, 1, 9, 5, 3, 4, 8],
  [1, 9, 8, 3, 4, 2, 5, 6, 7],
  [8, 5, 9, 7, 6, 1, 4, 2, 3],
  [4, 2, 6, 8, 5, 3, 7, 9, 1],
  [7, 1, 3, 9, 2, 4, 8, 5, 6],
  [9, 6, 1, 5, 3, 7, 2, 8, 4],
  [2, 8, 7, 4, 1, 9, 6, 3, 5],
  [3, 4, 5, 2, 8, 6, 1, 7, 0],
];

test.describe('Game lifecycle — API calls', () => {
  test('page load triggers POST /games and stores gameId in localStorage', async ({ page }) => {
    let postGamesCalled = false;

    await page.route('**/games', (route) => {
      if (route.request().method() === 'POST') {
        postGamesCalled = true;
        route.fulfill({ status: 201, json: CANNED_GAME_STATE });
      } else {
        route.continue();
      }
    });
    await page.route('**/games/**', (route) => {
      route.request().method() === 'PATCH'
        ? route.fulfill({ status: 200, body: '' })
        : route.continue();
    });

    await page.goto('/');
    await waitForGrid(page);

    expect(postGamesCalled).toBe(true);

    const storedId = await page.evaluate(() => localStorage.getItem('sudoku_gameId'));
    expect(storedId).toBe(CANNED_GAME_ID);
  });

  test('entering a cell value saves currentGrid to localStorage', async ({ page }) => {
    await page.route('**/games', (route) => {
      route.request().method() === 'POST'
        ? route.fulfill({ status: 201, json: CANNED_GAME_STATE })
        : route.continue();
    });
    await page.route('**/games/**', (route) => {
      route.request().method() === 'PATCH'
        ? route.fulfill({ status: 200, body: '' })
        : route.continue();
    });

    await page.goto('/');
    await waitForGrid(page);

    await page.getByRole('button', { name: '4', exact: true }).click();
    await page.getByTestId('cell-0-2').click();
    await expect(page.getByTestId('cell-0-2')).toContainText('4');

    const stored = await page.evaluate(() => {
      const raw = localStorage.getItem('sudoku_currentGrid');
      return raw ? JSON.parse(raw) : null;
    });
    expect(stored).not.toBeNull();
    expect(stored[0][2]).toBe(4);
  });

  test('tab becoming hidden fires PATCH /games/:id immediately', async ({ page }) => {
    const patchBodies = [];

    await page.route('**/games', (route) => {
      route.request().method() === 'POST'
        ? route.fulfill({ status: 201, json: CANNED_GAME_STATE })
        : route.continue();
    });
    await page.route('**/games/**', async (route) => {
      if (route.request().method() === 'PATCH') {
        patchBodies.push(JSON.parse(route.request().postData() || '{}'));
        route.fulfill({ status: 200, body: '' });
      } else {
        route.continue();
      }
    });

    await page.goto('/');
    await waitForGrid(page);

    // Simulate the tab going hidden via the Page Visibility API
    await page.evaluate(() => {
      Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true });
      document.dispatchEvent(new Event('visibilitychange'));
    });

    // Wait for the PATCH to arrive
    await expect.poll(() => patchBodies.length, { timeout: 3000 }).toBeGreaterThan(0);

    expect(patchBodies[0]).toMatchObject({
      currentGrid: expect.any(Array),
      candidates: expect.any(Array),
      timeSpentSeconds: expect.any(Number),
    });
  });

  test('completing a puzzle fires PATCH /games/:id with isComplete=true', async ({ page }) => {
    const patchBodies = [];
    const nearCompleteState = {
      ...CANNED_GAME_STATE,
      originalGrid: NEAR_COMPLETE_GRID,
      currentGrid: NEAR_COMPLETE_GRID.map((r) => [...r]),
    };

    await page.route('**/games', (route) => {
      route.request().method() === 'POST'
        ? route.fulfill({ status: 201, json: nearCompleteState })
        : route.continue();
    });
    await page.route('**/games/**', async (route) => {
      if (route.request().method() === 'PATCH') {
        patchBodies.push(JSON.parse(route.request().postData() || '{}'));
        route.fulfill({ status: 200, body: '' });
      } else {
        route.continue();
      }
    });
    await page.route('**/puzzles/validate', (route) =>
      route.fulfill({ json: { isValid: true, isSolved: true, errors: [] } })
    );

    await page.goto('/');
    await waitForGrid(page);

    // Fill the last cell and validate
    await page.getByRole('button', { name: '9', exact: true }).click();
    await page.getByTestId('cell-8-8').click();
    await page.getByRole('button', { name: 'Check' }).click();

    // Wait for the completion PATCH
    await expect.poll(
      () => patchBodies.some((b) => b.isComplete === true),
      { timeout: 3000 }
    ).toBe(true);
  });
});

test.describe('Game resume — localStorage', () => {
  test('reload with saved gameId calls GET /games/:id instead of POST /games', async ({ page }) => {
    let postGamesCalled = false;
    let getGamesCalled = false;

    await page.route('**/games', (route) => {
      if (route.request().method() === 'POST') {
        postGamesCalled = true;
        route.fulfill({ status: 201, json: CANNED_GAME_STATE });
      } else {
        route.continue();
      }
    });
    await page.route(`**/games/${CANNED_GAME_ID}`, (route) => {
      if (route.request().method() === 'GET') {
        getGamesCalled = true;
        route.fulfill({ status: 200, json: CANNED_GAME_STATE });
      } else if (route.request().method() === 'PATCH') {
        route.fulfill({ status: 200, body: '' });
      } else {
        route.continue();
      }
    });

    // Seed localStorage with a saved gameId before navigating
    await page.addInitScript((gameId) => {
      localStorage.setItem('sudoku_gameId', gameId);
    }, CANNED_GAME_ID);

    await page.goto('/');
    await waitForGrid(page);

    expect(postGamesCalled).toBe(false);
    expect(getGamesCalled).toBe(true);
  });

  test('reload with invalid/expired gameId falls back to POST /games', async ({ page }) => {
    let postGamesCalled = false;

    await page.route('**/games', (route) => {
      if (route.request().method() === 'POST') {
        postGamesCalled = true;
        route.fulfill({ status: 201, json: CANNED_GAME_STATE });
      } else {
        route.continue();
      }
    });
    await page.route('**/games/**', (route) => {
      if (route.request().method() === 'GET') {
        route.fulfill({ status: 404, body: '' });
      } else if (route.request().method() === 'PATCH') {
        route.fulfill({ status: 200, body: '' });
      } else {
        route.continue();
      }
    });

    await page.addInitScript(() => {
      localStorage.setItem('sudoku_gameId', '00000000-dead-beef-dead-000000000000');
    });

    await page.goto('/');
    await waitForGrid(page);

    expect(postGamesCalled).toBe(true);

    // localStorage should now hold the new gameId (not the stale one)
    const storedId = await page.evaluate(() => localStorage.getItem('sudoku_gameId'));
    expect(storedId).toBe(CANNED_GAME_ID);
  });
});
