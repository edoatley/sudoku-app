Your goal is to run all of the tests to check the project is working correctly and provide a clear test summary report.

## Steps

### 1. Frontend Lint

Run from the `ui/` directory:

```bash
cd ui && npm run lint
```

### 2. Frontend Playwright E2E Tests

Run from the `ui/` directory:

```bash
cd ui && npm run test:e2e
```

This uses `playwright.config.js` which:
- Targets `./tests/e2e/` (specs: candidate-mode, validate, hint, auto-notes, new-game, clear-cell, error-cells, number-select, undo, solved, game-persistence)
- Automatically starts the Vite dev server on `http://localhost:5173` using `npm run dev -- --mode test`
- Runs Chromium only
- Uses `page.route()` HTTP interception — `VITE_MOCK_API` must be `false` in `.env.test`

If Playwright browsers are not installed, first run:
```bash
cd ui && npx playwright install chromium
```

### 3. Backend Maven Tests + JaCoCo Coverage

Run from the `backend/` directory:

```bash
cd backend && ./mvnw verify
```

This runs:
- Layer 3: JUnit 5 unit tests (domain, puzzle generation, hint strategies)
- Layer 4: Rest-Assured + `@QuarkusTest` API tests (`PuzzleResourceTest`)
- Layer 5: JaCoCo coverage report (HTML + XML under `target/site/jacoco/`) with a **70% line coverage threshold** — build fails if threshold is not met

To also run integration tests (`*IT.java`):
```bash
cd backend && ./mvnw verify -DskipITs=false
```

### 4. Integration Tests (Docker Compose + Playwright)

These tests require Docker. They run the real backend and built frontend together to catch API contract breaks, serialisation mismatches, and CORS issues.

```bash
# Start the stack (builds backend and UI Docker images)
docker compose -f docker-compose.test.yml up -d --build

# Wait for the backend healthcheck to pass, then run integration tests
cd ui && npx playwright test --config playwright.integration.config.js

# Tear down the stack (always, even on failure)
docker compose -f docker-compose.test.yml down
```

Config: `ui/playwright.integration.config.js` — targets port 5174 (UI container), no mocking.

Skip this step if Docker is not available; note it in the summary.

## Reporting

After running all tests, provide a summary in this format:

```
## Test Summary

### Frontend Lint
- Status: PASS / FAIL
- [List any lint errors]

### Frontend (Playwright E2E)
- Total: X | Passed: X | Failed: X | Skipped: X
- [List any failing tests with their error messages]

### Backend (Maven verify — unit + API + JaCoCo)
- Total: X | Passed: X | Failed: X | Errors: X
- JaCoCo line coverage: X% (threshold: 70%)
- [List any failing tests with their error messages]

### Integration Tests (Docker Compose + Playwright)
- Total: X | Passed: X | Failed: X | Skipped: X
- [List any failing tests with their error messages]
- [Note if skipped due to Docker not being available]

### Overall Status: PASS / FAIL
```

Highlight:
- Any test failures with the specific assertion or error
- Any tests that were skipped and why
- Any setup issues (e.g. missing dependencies, port conflicts, compilation errors, Docker not running)
- JaCoCo coverage percentage and whether it met the 70% threshold
