/**
 * Playwright config for running hint demo tests locally without Docker.
 *
 * Requires only the backend running locally (cd backend && ./mvnw quarkus:dev).
 * Starts Vite automatically with VITE_DEV_TOOLS=true and VITE_SKIP_AUTH=true.
 *
 * Run with:
 *   npx playwright test tests/integration/hint-demos.spec.js --config playwright.hint-demos.local.config.js
 * Or via npm script:
 *   npm run test:hint-demos:local
 */
import { defineConfig, devices } from '@playwright/test';
import process from 'node:process';

export default defineConfig({
  testDir: './tests/integration',
  testMatch: '**/hint-demos.spec.js',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'npm run dev -- --mode hint-demos --port 5173',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
  },
});
