import { defineConfig, devices } from '@playwright/test';
import process from 'node:process';

// Integration test config — runs against the Docker Compose stack.
// No webServer block: the stack must already be up before tests run.
export default defineConfig({
  testDir: './tests/integration',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: 'html',
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
