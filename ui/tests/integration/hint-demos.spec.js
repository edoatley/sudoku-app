/**
 * Hint demo integration tests — verify each developer menu demo loads a grid
 * and produces the correct hint dialog when the Hint button is clicked.
 *
 * Requires: docker-compose stack running with VITE_DEV_TOOLS=true.
 *
 * Run standalone:
 *   npm run test:hint-demos
 *
 * Or as part of the full local test suite via scripts/test-local.sh.
 */
import { test, expect } from '@playwright/test';

const HINT_DEMOS = [
  { slug: 'full-house',    label: 'Full House demo',    techniqueName: 'Full House' },
  { slug: 'naked-single',  label: 'Naked Single demo',  techniqueName: 'Naked Single' },
  { slug: 'naked-pair',    label: 'Naked Pair demo',    techniqueName: 'Naked Pair' },
  { slug: 'hidden-single', label: 'Hidden Single demo', techniqueName: 'Hidden Single' },
  { slug: 'pointing-pair', label: 'Pointing Pair demo', techniqueName: 'Pointing Pair' },
  { slug: 'naked-triple',  label: 'Naked Triple demo',  techniqueName: 'Naked Triple' },
  { slug: 'hidden-pair',   label: 'Hidden Pair demo',   techniqueName: 'Hidden Pair' },
  { slug: 'hidden-triple', label: 'Hidden Triple demo', techniqueName: 'Hidden Triple' },
  { slug: 'x-wing',        label: 'X-Wing demo',        techniqueName: 'X-Wing' },
  { slug: 'swordfish',     label: 'Swordfish demo',     techniqueName: 'Swordfish' },
  { slug: 'y-wing',        label: 'Y-Wing demo',        techniqueName: 'Y-Wing' },
];

async function waitForGrid(page) {
  await expect(page.getByTestId('cell-0-0')).toBeVisible({ timeout: 15_000 });
}

async function loadHintDemo(page, label) {
  await page.getByRole('button', { name: /game menu/i }).click();
  await page.getByRole('menuitem', { name: /developer/i }).click();
  await page.getByRole('menuitem', { name: label }).click();
  await waitForGrid(page);
}

for (const { label, techniqueName } of HINT_DEMOS) {
  test(`hint demo — ${label} shows correct hint dialog`, async ({ page }) => {
    await page.goto('/');
    // Wait for the page to fully settle (either a grid appears or an error state —
    // either way the header with the Game menu button will be present)
    await expect(page.getByRole('button', { name: /game menu/i })).toBeVisible({ timeout: 15_000 });

    await loadHintDemo(page, label);

    await page.getByRole('button', { name: /hint/i }).click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible({ timeout: 10_000 });
    await expect(dialog).toContainText(techniqueName);
  });
}
