/**
 * End-to-end test: full player journey
 *
 * 1.  New user visits — no grid shown
 * 2.  Opens hamburger menu → New Game → Medium → grid appears
 * 3.  Player fills a couple of cells
 * 4.  Player clicks Pause → pause overlay shown; tab goes hidden → PATCH synced
 * 5.  Player "closes tab" (simulated as page.goto with seeded localStorage)
 * 6.  On reload the saved game is loaded: grid + partial completions + elapsed time restored
 * 7.  Player fills the last cell → congratulations dialog appears with time taken
 * 8.  Game marked complete (PATCH with isComplete=true sent to backend)
 * 9.  Player clicks Finish → no grid shown; hamburger menu needed for next game
 */
import { test, expect } from '@playwright/test';
import { CANNED_GAME_ID } from './helpers.js';

// ─── Puzzle fixtures ──────────────────────────────────────────────────────────

// Medium puzzle (taken from CANNED_PUZZLES.medium in cannedData.js)
// originalGrid has zeros marking empty cells that the player must fill
const MEDIUM_ORIGINAL = [
  [0, 0, 0, 2, 6, 0, 7, 0, 1],
  [6, 8, 0, 0, 7, 0, 0, 9, 0],
  [1, 9, 0, 0, 0, 4, 5, 0, 0],
  [8, 2, 0, 1, 0, 0, 0, 4, 0],
  [0, 0, 4, 6, 0, 2, 9, 0, 0],
  [0, 5, 0, 0, 0, 3, 0, 2, 8],
  [0, 0, 9, 3, 0, 0, 0, 7, 4],
  [0, 4, 0, 0, 5, 0, 0, 3, 6],
  [7, 0, 3, 0, 1, 8, 0, 0, 0],
];

// Player has filled 2 cells: [0][0]=4, [0][1]=3 (both were 0 in originalGrid)
const MEDIUM_PARTIAL = MEDIUM_ORIGINAL.map((r) => [...r]);
MEDIUM_PARTIAL[0][0] = 4;
MEDIUM_PARTIAL[0][1] = 3;

// Solved medium grid (all 81 cells correct) — only [8][8] left empty for near-complete
const MEDIUM_SOLVED = [
  [4, 3, 5, 2, 6, 9, 7, 8, 1],
  [6, 8, 2, 5, 7, 1, 4, 9, 3],
  [1, 9, 7, 8, 3, 4, 5, 6, 2],
  [8, 2, 6, 1, 9, 5, 3, 4, 7],
  [3, 7, 4, 6, 8, 2, 9, 1, 5],
  [9, 5, 1, 7, 4, 3, 6, 2, 8],
  [5, 1, 9, 3, 2, 6, 8, 7, 4],
  [2, 4, 8, 9, 5, 7, 1, 3, 6],
  [7, 6, 3, 4, 1, 8, 2, 5, 0], // [8][8] = 0 — last cell for player to fill
];

const GAME_STATE_PARTIAL = {
  gameId: CANNED_GAME_ID,
  difficulty: 'medium',
  originalGrid: MEDIUM_ORIGINAL,
  currentGrid: MEDIUM_PARTIAL,
  candidates: Array(9).fill(null).map(() => Array(9).fill(null).map(() => [])),
  timeSpentSeconds: 47,
  status: 'IN_PROGRESS',
};

// State returned on resume (GET): near-complete, one cell left
const GAME_STATE_RESUME = {
  ...GAME_STATE_PARTIAL,
  currentGrid: MEDIUM_SOLVED,
};

const ELAPSED_AT_PAUSE = 47; // seconds stored in localStorage

// ─── Helpers ─────────────────────────────────────────────────────────────────

/**
 * Wire up all route mocks needed for the first session.
 * POST /games  → returns the partial game state (medium difficulty)
 * PATCH /games/:id → captures bodies + 200
 */
async function setupFirstSessionRoutes(page, patchBodies) {
  await page.route('**/games', (route) => {
    if (route.request().method() === 'POST') {
      route.fulfill({ status: 201, json: GAME_STATE_PARTIAL });
    } else {
      route.continue();
    }
  });
  await page.route('**/games/**', async (route) => {
    if (route.request().method() === 'PATCH') {
      patchBodies.push(JSON.parse(route.request().postData() || '{}'));
      route.fulfill({ status: 200, body: '' });
    } else {
      route.continue();
    }
  });
  // validate endpoint — medium is not solved yet during first session
  await page.route('**/puzzles/validate', (route) =>
    route.fulfill({ json: { isValid: true, isSolved: false, errors: [] } })
  );
}

/**
 * Wire up route mocks for the resumed session.
 * GET /games/:id → near-complete resume state
 * PATCH /games/:id → capture bodies + 200
 * validate → isValid=true (used for auto-complete check)
 */
async function setupResumeSessionRoutes(page, patchBodies) {
  await page.route('**/games', (route) => {
    // POST should NOT be called on resume
    route.request().method() === 'POST'
      ? route.fulfill({ status: 500, body: 'unexpected POST' })
      : route.continue();
  });
  await page.route(`**/games/${CANNED_GAME_ID}`, async (route) => {
    if (route.request().method() === 'GET') {
      route.fulfill({ status: 200, json: GAME_STATE_RESUME });
    } else if (route.request().method() === 'PATCH') {
      patchBodies.push(JSON.parse(route.request().postData() || '{}'));
      route.fulfill({ status: 200, body: '' });
    } else {
      route.continue();
    }
  });
  await page.route('**/puzzles/validate', (route) =>
    route.fulfill({ json: { isValid: true, isSolved: true, errors: [] } })
  );
}

// ─── Test ─────────────────────────────────────────────────────────────────────

test('full player journey: new user → play → pause → resume → complete → finish', async ({ page }) => {
  // ── Session 1: new user visit ──────────────────────────────────────────────

  const session1Patches = [];
  await setupFirstSessionRoutes(page, session1Patches);

  await page.goto('/');

  // 1. New user — no grid displayed (originalGrid is null)
  await expect(page.getByTestId('cell-0-0')).not.toBeVisible();

  // 2. Open hamburger menu, select New Game, pick Medium, click Start
  await page.getByRole('button', { name: 'Game menu' }).click();
  await page.getByRole('menuitem', { name: 'New Game' }).click();
  await page.getByLabel('Medium').click();
  await page.getByRole('button', { name: 'Start' }).click();

  // Grid should now appear
  await expect(page.getByTestId('cell-0-0')).toBeVisible();

  // 3. Player fills a couple of cells (originalGrid[0][0] and [0][1] are 0 so editable)
  await page.getByRole('button', { name: '4', exact: true }).click();
  await page.getByTestId('cell-0-0').click();
  await expect(page.getByTestId('cell-0-0')).toContainText('4');

  await page.getByRole('button', { name: '3', exact: true }).click();
  await page.getByTestId('cell-0-1').click();
  await expect(page.getByTestId('cell-0-1')).toContainText('3');

  // 4. Player pauses the game (clicks Pause button in header)
  await page.getByRole('button', { name: 'Pause game' }).click();
  await expect(page.getByTestId('pause-overlay')).toBeVisible();

  // 5. Tab goes hidden — triggers immediate PATCH sync with current state
  await page.evaluate(() => {
    Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true });
    document.dispatchEvent(new Event('visibilitychange'));
  });

  // Wait for the PATCH sync to arrive (should be near-instant on visibility change)
  await expect.poll(() => session1Patches.length, { timeout: 3000 }).toBeGreaterThan(0);

  // The sync should contain the player's progress (no isComplete flag)
  const syncPatch = session1Patches[session1Patches.length - 1];
  expect(syncPatch).toMatchObject({
    currentGrid: expect.any(Array),
    timeSpentSeconds: expect.any(Number),
  });
  expect(syncPatch.isComplete).toBeFalsy();

  // ── Session 2: user re-opens app (new page load with saved localStorage) ────

  // Seed localStorage to simulate what was saved during session 1
  const session2Patches = [];
  await setupResumeSessionRoutes(page, session2Patches);

  await page.addInitScript((args) => {
    localStorage.setItem('sudoku_gameId', args.gameId);
    localStorage.setItem('sudoku_elapsedSeconds', String(args.elapsed));
    localStorage.setItem('sudoku_difficulty', 'medium');
    localStorage.setItem('sudoku_currentGrid', JSON.stringify(args.currentGrid));
  }, {
    gameId: CANNED_GAME_ID,
    elapsed: ELAPSED_AT_PAUSE,
    currentGrid: MEDIUM_PARTIAL,
  });

  await page.goto('/');

  // 6. Game resumes — grid visible with player's partial completions
  await expect(page.getByTestId('cell-0-0')).toBeVisible({ timeout: 5000 });
  await expect(page.getByTestId('cell-0-0')).toContainText('4');
  await expect(page.getByTestId('cell-0-1')).toContainText('3');

  // Timer chip should be visible and show the restored elapsed time (MM:SS format)
  await expect(page.locator('[class*="MuiChip-root"]').filter({ hasText: /\d{2}:\d{2}/ }).first()).toBeVisible();

  // 7. Player fills the final cell ([8][8] = 0 in MEDIUM_SOLVED)
  await page.getByRole('button', { name: '9', exact: true }).click();
  await page.getByTestId('cell-8-8').click();

  // 8. Congratulations dialog appears with time taken
  await expect(page.getByTestId('congrats-dialog')).toBeVisible({ timeout: 5000 });
  await expect(page.getByTestId('congrats-dialog')).toContainText('Congratulations');
  await expect(page.getByTestId('congrats-dialog')).toContainText(/\d+m \d+s/);

  // Game flagged as complete — PATCH with isComplete=true sent
  await expect.poll(
    () => session2Patches.some((b) => b.isComplete === true),
    { timeout: 5000 }
  ).toBe(true);

  // 9. Player clicks Finish
  await page.getByTestId('finish-button').click();

  // 10. No grid displayed — user must use hamburger menu for next game
  await expect(page.getByTestId('cell-0-0')).not.toBeVisible();
  // localStorage is cleared
  const savedId = await page.evaluate(() => localStorage.getItem('sudoku_gameId'));
  expect(savedId).toBeNull();
});
