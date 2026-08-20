import { defineConfig, devices } from '@playwright/test';
import process from 'node:process';

const isCI = !!process.env.CI;

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  forbidOnly: isCI,
  // CI runs against the Vite dev server under 2 parallel workers, which is more prone to
  // transient timing flakiness (React state updates racing runner load) than a real bug —
  // retry there before failing the build. Never retry locally; a local failure should surface
  // immediately.
  retries: isCI ? 2 : 0,
  workers: isCI ? 2 : undefined,
  timeout: 10_000,
  expect: { timeout: 5_000 },
  reporter: isCI
    ? [['dot'], ['junit', { outputFile: 'test-results/results.xml' }]]
    : [['list'], ['junit', { outputFile: 'test-results/results.xml' }]],
  use: {
    baseURL: 'http://localhost:5174',
    trace: 'retain-on-failure',
    launchOptions: {
      args: ['--disable-dev-shm-usage'],
    },
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'npm run dev -- --mode test --port 5174',
    url: 'http://localhost:5174',
    reuseExistingServer: !isCI,
  },
});
