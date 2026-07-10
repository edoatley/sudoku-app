import { test, expect } from '@playwright/test';
import { setupGameRoutes, waitForGrid, CANNED_GAME_STATE, toWireGameState } from './helpers.js';

// @spec FE-UI-053
test('difficulty chip — shows "Easy" label when game starts', async ({ page }) => {
  await setupGameRoutes(page);
  await page.goto('/');
  await waitForGrid(page);

  await expect(page.getByText('Easy')).toBeVisible();
});

test('difficulty chip — shows "Hard" label after starting a hard game', async ({ page }) => {
  const hardGameState = toWireGameState({ ...CANNED_GAME_STATE, difficulty: 'hard' });
  await setupGameRoutes(page, hardGameState);
  await page.goto('/');
  await waitForGrid(page);

  await expect(page.getByText('Hard')).toBeVisible();
});

test('difficulty chip — not visible before game starts', async ({ page }) => {
  await page.goto('/');

  await expect(page.getByText('Easy')).not.toBeVisible();
  await expect(page.getByText('Medium')).not.toBeVisible();
  await expect(page.getByText('Hard')).not.toBeVisible();
});
