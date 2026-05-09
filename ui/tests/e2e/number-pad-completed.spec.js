import { test, expect } from '@playwright/test';
import { setupGameRoutes, waitForGrid, CANNED_GAME_ID, EASY_PUZZLE, toWireGrid, toWireCandidates } from './helpers.js';

// Grid where digit 9 appears exactly 9 times (all placed).
// Original puzzle has 9s at: [1][4], [2][1], [7][5], [8][8]
// We add 5 more in empty cells:  [0][6], [3][7], [4][6], [5][6], [6][8]
const GRID_WITH_ALL_NINES_ROWS = EASY_PUZZLE.originalGrid.map((r) => [...r]);
GRID_WITH_ALL_NINES_ROWS[0][6] = 9;
GRID_WITH_ALL_NINES_ROWS[3][7] = 9;
GRID_WITH_ALL_NINES_ROWS[4][6] = 9;
GRID_WITH_ALL_NINES_ROWS[5][6] = 9;
GRID_WITH_ALL_NINES_ROWS[6][8] = 9;

// A candidate grid that still has a 9 candidate in cell [0][2]
const CANDIDATES_WITH_NINE_ROWS = Array(9).fill(null).map(() => Array(9).fill(null).map(() => []));
CANDIDATES_WITH_NINE_ROWS[0][2] = [9];

// Wire-format versions for API mocks (setupGameRoutes expects {rows:[...]} format)
const GRID_WITH_ALL_NINES = toWireGrid(GRID_WITH_ALL_NINES_ROWS);
const CANDIDATES_WITH_NINE = toWireCandidates(CANDIDATES_WITH_NINE_ROWS);

test('number pad — completed digit is disabled in normal mode', async ({ page }) => {
  await setupGameRoutes(page, {
    currentGrid: GRID_WITH_ALL_NINES,
    candidates: CANDIDATES_WITH_NINE,
  });
  await page.goto('/');
  await waitForGrid(page);

  // Normal mode is the default; 9 appears 9 times so the normal pad button should be disabled
  const nineButton = page.getByTestId('numberpad-normal').getByRole('button', { name: '9', exact: true });
  await expect(nineButton).toBeDisabled();
});

test('number pad — completed digit is enabled in candidate mode', async ({ page }) => {
  await setupGameRoutes(page, {
    currentGrid: GRID_WITH_ALL_NINES,
    candidates: CANDIDATES_WITH_NINE,
  });
  await page.goto('/');
  await waitForGrid(page);

  // Candidate row button for 9 is always enabled regardless of completion count
  const nineButton = page.getByTestId('numberpad-candidate').getByRole('button', { name: '9', exact: true });
  await expect(nineButton).toBeEnabled();
});

test('number pad — candidate mode allows removing a lingering candidate of a completed digit', async ({ page }) => {
  await setupGameRoutes(page, {
    currentGrid: GRID_WITH_ALL_NINES,
    candidates: CANDIDATES_WITH_NINE,
  });
  // Candidates are loaded from localStorage, not the API response — seed it explicitly
  // Use the plain array (not wire format) since localStorage stores internal format
  await page.addInitScript((candidates) => {
    localStorage.setItem('sudoku_candidateGrid', JSON.stringify(candidates));
  }, CANDIDATES_WITH_NINE_ROWS);
  await page.goto('/');
  await waitForGrid(page);

  // cell-0-2 has a 9 candidate; use candidate row to click 9 (switches to candidate mode)
  await page.getByTestId('cell-0-2').click();
  await page.getByTestId('numberpad-candidate').getByRole('button', { name: '9', exact: true }).click();

  // Once the only candidate is removed the cell reverts to empty (no CandidateDisplay)
  await expect(page.getByTestId('cell-0-2')).toHaveText('');
});

test('number pad — clicking candidate row switches to candidate mode; clicking normal row switches back', async ({ page }) => {
  await setupGameRoutes(page);
  await page.goto('/');
  await waitForGrid(page);

  // Clicking candidate row selects in candidate mode — cell gets a mini candidate digit
  await page.getByTestId('numberpad-candidate').getByRole('button', { name: '4', exact: true }).click();
  await page.getByTestId('cell-0-2').click();
  await expect(page.getByTestId('cell-0-2')).toContainText('4');

  await page.getByRole('button', { name: 'Undo' }).click();

  // Clicking normal row switches back — cell gets a full placed digit
  await page.getByTestId('numberpad-normal').getByRole('button', { name: '4', exact: true }).click();
  await page.getByTestId('cell-0-2').click();
  await expect(page.getByTestId('cell-0-2')).toContainText('4');
});
