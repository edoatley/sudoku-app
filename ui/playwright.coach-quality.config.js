/**
 * Playwright config for the opt-in AI coach quality suite — real backend, real DynamoDB
 * Local, real Bedrock (see docker-compose.coach-quality.yml). Not part of playwright.
 * integration.config.js's testDir so the mocked-none, real-Bedrock cost/non-determinism
 * of these tests never leaks into the mandatory integration suite.
 *
 * The docker-compose stack must already be up (see scripts/local/coach-quality-test.sh) —
 * no webServer block here, matching playwright.integration.config.js.
 *
 * Run with:
 *   bash scripts/local/coach-quality-test.sh
 * Or, with the stack already running:
 *   npm run test:coach-quality
 */
import { defineConfig, devices } from '@playwright/test';
import process from 'node:process';

export default defineConfig({
  testDir: './tests/coach-quality',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  timeout: 60_000,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.INTEGRATION_BASE_URL ?? 'http://localhost:5174',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
