Your goal is to run all of the tests to check the project is working correctly and provide a clear test summary report.

## Steps

### 1. Frontend Playwright E2E Tests

Run from the `ui/` directory:

```bash
cd ui && npm run test:e2e
```

This uses `playwright.config.js` which:
- Targets `./tests/e2e/` (specs: candidate-mode, validate, hint, auto-notes, new-game)
- Automatically starts the Vite dev server on `http://localhost:5173` using `npm run dev -- --mode test`
- Runs Chromium only

If Playwright browsers are not installed, first run:
```bash
cd ui && npx playwright install chromium
```

### 2. Backend Maven Tests

Run from the `backend/` directory:

```bash
cd backend && ./mvnw test
```

To also run integration tests (`*IT.java`):
```bash
cd backend && ./mvnw verify -DskipITs=false
```

Note: There is currently no Vitest or other unit test framework configured for the frontend.

## Reporting

After running all tests, provide a summary in this format:

```
## Test Summary

### Frontend (Playwright E2E)
- Total: X | Passed: X | Failed: X | Skipped: X
- [List any failing tests with their error messages]

### Backend (Maven)
- Total: X | Passed: X | Failed: X | Errors: X
- [List any failing tests with their error messages]

### Overall Status: PASS / FAIL
```

Highlight:
- Any test failures with the specific assertion or error
- Any tests that were skipped and why
- Any setup issues (e.g. missing dependencies, port conflicts, compilation errors)