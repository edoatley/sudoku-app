/**
 * Playwright global setup for smoke tests running against the deployed Amplify app.
 *
 * When SMOKE_ID_TOKEN is set (CI only), it seeds a saved Amplify auth session into
 * the browser storage before each test navigates to the app, so the <Authenticator>
 * sees an existing session and renders the game immediately.
 *
 * When SMOKE_ID_TOKEN is not set (local Docker Compose run with VITE_SKIP_AUTH=true)
 * this file is a no-op — no auth injection is needed.
 */

import { chromium } from '@playwright/test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const STATE_FILE = path.join(
  path.dirname(fileURLToPath(import.meta.url)),
  'auth-state.json',
);

export default async function globalSetup() {
  const idToken = process.env.SMOKE_ID_TOKEN;
  const accessToken = process.env.SMOKE_ACCESS_TOKEN;
  const baseURL = process.env.INTEGRATION_BASE_URL ?? 'http://localhost:5174';

  if (!idToken) {
    // Local run — VITE_SKIP_AUTH=true handles auth bypass; nothing to do.
    return;
  }

  // Amplify v6 stores session tokens in localStorage under keys of the form:
  //   CognitoIdentityServiceProvider.<clientId>.<username>.<tokenType>
  // plus a "LastAuthUser" key. We reconstruct that structure from the raw tokens.
  const { jwtDecode } = await import('jwt-decode');
  const claims = jwtDecode(idToken);
  const username = claims['cognito:username'] ?? claims.sub;
  // Extract client ID from the token's aud claim — avoids the GitHub Actions
  // secret-masking problem where COGNITO_CLIENT_ID arrives as an empty string.
  const clientId = Array.isArray(claims.aud) ? claims.aud[0] : claims.aud;

  const prefix = `CognitoIdentityServiceProvider.${clientId}`;
  const storageEntries = {
    [`${prefix}.LastAuthUser`]: username,
    [`${prefix}.${username}.idToken`]: idToken,
    [`${prefix}.${username}.accessToken`]: accessToken,
    // Refresh token not available from env — Amplify will use idToken while valid
    [`${prefix}.${username}.clockDrift`]: '0',
    // Amplify v6 also checks for this key to determine the sign-in details
    [`${prefix}.${username}.signInDetails`]: JSON.stringify({
      loginId: username,
      authFlowType: 'USER_PASSWORD_AUTH',
    }),
  };

  // Write the storage state using a real browser so Playwright can reuse it.
  // Use addInitScript so tokens are in localStorage before any page JS runs —
  // this prevents Amplify from redirecting to the hosted UI on first load.
  const browser = await chromium.launch();
  const context = await browser.newContext({ baseURL });
  await context.addInitScript((entries) => {
    for (const [key, value] of Object.entries(entries)) {
      localStorage.setItem(key, value);
    }
  }, storageEntries);
  const page = await context.newPage();
  await page.goto('/');

  await context.storageState({ path: STATE_FILE });
  await browser.close();
}
