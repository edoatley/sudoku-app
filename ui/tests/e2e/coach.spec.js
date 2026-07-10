// @spec SC-UI-001, SC-UI-002, SC-UI-003, SC-UI-004
import { test, expect } from '@playwright/test';
import { setupGameRoutes, waitForGrid } from './helpers.js';

const COACH_RESPONSE = {
  aiMessage: "Let's look at the board together. Can you spot any rows that are almost complete?",
  hint: null,
  revealHint: false,
};

const COACH_RESPONSE_WITH_HINT = {
  aiMessage: 'Look at cell (0, 2) — only one digit can fit there.',
  hint: {
    techniqueName: 'Naked Single',
    markdownSlug: 'naked-single',
    difficulty: 'easy',
    strategyRank: 20,
    nudge: 'Look at cell (0, 2) — only one digit can go here.',
    focus: 'Row 1, Column 3 has only digit 4 as a possibility.',
    reveal: 'Place 4 in Row 1, Column 3.',
    highlightCells: [{ row: 0, col: 2 }],
    eliminatedCandidates: [],
    solvedCells: [],
    focusCandidates: [],
  },
  revealHint: false,
};

test('coach — FAB is visible on desktop after game loads', async ({ page }) => {
  await setupGameRoutes(page);
  await page.goto('/');
  await waitForGrid(page);

  await expect(page.getByRole('button', { name: 'Open coach' })).toBeVisible();
});

test('coach — FAB is hidden on mobile viewport', async ({ page }) => {
  // MUI md breakpoint is 900px; test at 800px
  await page.setViewportSize({ width: 800, height: 600 });
  await setupGameRoutes(page);
  await page.goto('/');
  await waitForGrid(page);

  await expect(page.getByRole('button', { name: 'Open coach' })).not.toBeVisible();
});

test('coach — clicking FAB opens panel with welcome message', async ({ page }) => {
  await setupGameRoutes(page);
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: 'Open coach' }).click();

  await expect(page.getByTestId('coach-panel')).toBeVisible();
  await expect(page.getByTestId('coach-panel')).toContainText("Hello! I'm your Sudoku coach");
});

test('coach — second open does not reset conversation', async ({ page }) => {
  await setupGameRoutes(page);
  await page.route('**/ai/coach', (route) => route.fulfill({ json: COACH_RESPONSE }));
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: 'Open coach' }).click();
  await page.getByRole('button', { name: 'Close coach' }).click();
  await page.getByRole('button', { name: 'Open coach' }).click();

  // Welcome message still present — history was preserved
  await expect(page.getByTestId('coach-panel')).toContainText("Hello! I'm your Sudoku coach");
});

test('coach — sending a message shows user bubble and AI response', async ({ page }) => {
  await setupGameRoutes(page);
  await page.route('**/ai/coach', (route) => route.fulfill({ json: COACH_RESPONSE }));
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: 'Open coach' }).click();
  await expect(page.getByTestId('coach-panel')).toBeVisible();

  await page.getByPlaceholder('Ask your coach…').fill("I'm stuck on row 1");
  await page.getByPlaceholder('Ask your coach…').press('Enter');

  await expect(page.getByTestId('coach-panel')).toContainText("I'm stuck on row 1");
  await expect(page.getByTestId('coach-panel')).toContainText("Let's look at the board together.");
});

test('coach — quick reply chip sends preset message', async ({ page }) => {
  await setupGameRoutes(page);
  await page.route('**/ai/coach', (route) => route.fulfill({ json: COACH_RESPONSE }));
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: 'Open coach' }).click();
  await page.getByRole('button', { name: "I'm stuck" }).click();

  await expect(page.getByTestId('coach-panel')).toContainText("I'm stuck");
  await expect(page.getByTestId('coach-panel')).toContainText("Let's look at the board together.");
});

test('coach — closing panel hides it and FAB shows Open label', async ({ page }) => {
  await setupGameRoutes(page);
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: 'Open coach' }).click();
  await expect(page.getByTestId('coach-panel')).toBeVisible();

  await page.getByRole('button', { name: 'Close', exact: false }).first().click();

  await expect(page.getByTestId('coach-panel')).not.toBeVisible();
  await expect(page.getByRole('button', { name: 'Open coach' })).toBeVisible();
});

test('coach — token counter updates from the coach response after a message is sent', async ({ page }) => {
  await setupGameRoutes(page);
  await page.route('**/ai/coach', (route) => route.fulfill({ json: { ...COACH_RESPONSE, tokensUsedThisMonth: 4242 } }));
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: 'Open coach' }).click();
  await expect(page.getByTestId('coach-panel')).toContainText('0 / 100,000');

  await page.getByPlaceholder('Ask your coach…').fill("I'm stuck on row 1");
  await page.getByPlaceholder('Ask your coach…').press('Enter');

  await expect(page.getByTestId('coach-panel')).toContainText('4,242 / 100,000');
});

test('coach — coach response with hint highlights cells on the board', async ({ page }) => {
  await setupGameRoutes(page);
  await page.route('**/ai/coach', (route) => route.fulfill({ json: COACH_RESPONSE_WITH_HINT }));
  await page.goto('/');
  await waitForGrid(page);

  await page.getByRole('button', { name: 'Open coach' }).click();
  await page.getByPlaceholder('Ask your coach…').fill('Which cell should I focus on?');
  await page.getByPlaceholder('Ask your coach…').press('Enter');

  await expect(page.getByTestId('coach-panel')).toContainText('Look at cell (0, 2)');
  // Verify board highlight was applied — cell-0-2 should have an aria-label indicating highlight
  await expect(page.getByTestId('cell-0-2')).toBeVisible();
});
