import { defineConfig, devices } from '@playwright/test';
import process from 'node:process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const AUTH_STATE_FILE = path.join(path.dirname(fileURLToPath(import.meta.url)), 'tests/integration/auth-state.json');

// Integration test config — runs against the Docker Compose stack locally,
// or against the deployed Amplify app in CI (INTEGRATION_BASE_URL set).
// No webServer block: the stack must already be up before tests run.
export default defineConfig({
  testDir: './tests/integration',
  // global setup seeds Amplify auth tokens into browser storage when running
  // against the real deployed app (SMOKE_ID_TOKEN present in CI)
  globalSetup: './tests/integration/auth-setup.js',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['html'], ['junit', { outputFile: 'test-results/integration-results.xml' }]],
  use: {
    baseURL: process.env.INTEGRATION_BASE_URL ?? 'http://localhost:5174',
    trace: 'on-first-retry',
    // Reuse the seeded auth storage state when it exists (CI runs only)
    storageState: process.env.SMOKE_ID_TOKEN ? AUTH_STATE_FILE : undefined,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
