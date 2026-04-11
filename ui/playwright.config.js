import { defineConfig, devices } from '@playwright/test';
import process from 'node:process';

const isCI = !!process.env.CI;

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  forbidOnly: isCI,
  retries: 0,
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
