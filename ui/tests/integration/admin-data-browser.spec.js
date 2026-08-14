/**
 * Integration test — validates the admin data-browser gate against the real backend.
 *
 * The smoke-test Cognito user is intentionally NOT a member of the "administrators"
 * group (docs/planning/admin-namespace-security-fix.md §4.4 — "Do not make the native
 * smoke-test user an admin"). This test asserts that a regular authenticated user never
 * sees the admin Data Browser menu entry — the UI-side guard against the PII exposure
 * this feature fixed (docs/planning/old/infra-review.md finding H1).
 *
 * Only meaningful against a real Cognito session (SMOKE_ID_TOKEN, CI only) on the "main"
 * environment: rc-* workspaces set VITE_DEV_TOOLS=true, which shows the Data Browser to
 * everyone regardless of admin status, so the negative assertion doesn't hold there.
 * Positive "an admin sees it" coverage is manual — no admin test user is provisioned.
 */
import { test, expect } from '@playwright/test';

test.describe('Admin data browser gate', () => {
  test.skip(
    !process.env.SMOKE_ID_TOKEN || process.env.ENV_TYPE !== 'main',
    'requires a real Cognito session against the main (VITE_DEV_TOOLS=false) environment'
  );

  test('a non-admin authenticated user does not see the Data Browser menu entry', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('cell-0-0')).toBeVisible({ timeout: 15_000 });

    await page.getByRole('button', { name: /game menu/i }).click();
    await expect(page.getByRole('menuitem', { name: /data browser/i })).toHaveCount(0);
  });
});
