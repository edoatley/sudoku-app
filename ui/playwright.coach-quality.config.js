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
  // Only the Playwright entrypoint(s); lib/*.test.js are vitest unit tests (Playwright's default
  // testMatch would otherwise also collect them and fail on the missing `vi` global).
  testMatch: '**/*.spec.js',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  // Long multi-turn scenarios against a deployed backend (cold Cloud Run + real Gemini latency)
  // can exceed the 60s local default — the remote wrapper raises this via the env var.
  timeout: Number(process.env.COACH_QUALITY_TEST_TIMEOUT_MS) || 60_000,
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
