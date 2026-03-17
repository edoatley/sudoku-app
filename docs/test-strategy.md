# Test Strategy

## Overview & Principles

- Tests live as close to the code they test as possible (co-located for unit tests, `tests/` directory for E2E)
- Playwright intercepts `fetch` via `page.route()` — **never** set `VITE_MOCK_API=true` in test environments
- Backend tests use the real Quarkus CDI container (`@QuarkusTest`), not mocks of internal services
- Validate at boundaries (HTTP layer, component output), not deep inside implementation details

---

## Environment Variable Rules

| Environment | File | `VITE_MOCK_API` | Effect |
|---|---|---|---|
| Developer (no backend) | `.env.development.local` (git-ignored) | `true` | Canned responses; no `fetch` calls fired |
| Playwright tests | `.env.test` | `false` | Real `fetch` → intercepted by `page.route()` |
| Production / CI with backend | `.env` or env vars | `false` | Real `fetch` → real backend |

**Rules:**
- `VITE_MOCK_API=true` only in `.env.development.local` (git-ignored) — developer convenience only, lets UI devs work without a running backend
- `VITE_MOCK_API=false` in `.env.test` — required so Playwright's `page.route()` interception fires
- Never set `VITE_MOCK_API=true` in CI or `.env.test`; doing so silences all `fetch` calls and makes `page.route()` stubs unreachable

---

## Layer 1 — Frontend Unit Tests (Vitest)

**Status: not yet implemented**

### What to test
- Pure logic in hooks: `useSudokuGame.js` — state transitions, action handlers, edge cases
- Utility/API branching logic in `sudokuApi.js` — mock vs real path selection

### Tool
Vitest — the natural fit for a Vite project; shares the same config and transform pipeline.

### Setup (when ready)

```bash
cd ui
npm install --save-dev vitest @vitest/coverage-v8 @testing-library/react @testing-library/user-event jsdom
```

Add to `vite.config.js`:

```js
test: {
  environment: 'jsdom',
  globals: true,
}
```

Add to `package.json`:

```json
"test:unit": "vitest run",
"test:coverage": "vitest run --coverage"
```

### Location
`ui/src/**/*.test.js` — co-located with source files.

### Scope
- `useSudokuGame` state transitions (cell selection, number entry, candidate toggle, validate result handling)
- `sudokuApi.js` branching logic — confirm the mock path returns canned data and the real path calls `fetch`

---

## Layer 2 — Frontend E2E Tests (Playwright)

**Status: in place**

### What to test
Full user journeys through a real browser: load puzzle, interact with cells, validate board, observe status feedback.

### Tool
Playwright with `page.route()` for HTTP interception. Routes are registered before navigation so no real network calls leave the browser.

### Location
`ui/tests/e2e/`

### Running

```bash
cd ui
npm run test:e2e          # headless
npm run test:e2e:ui       # interactive / trace viewer
```

### Key principle
Intercept at the HTTP layer — never duplicate validation logic inside the test. The test provides a canned JSON response and asserts on what the UI renders, not on the algorithm that produced it.

### Test files and scenarios

| File | Test | Scenario |
|---|---|---|
| `validate.spec.js` | `valid — fill row 0 with correct values and validate` | Backend returns `{ valid: true }` → success alert shown |
| `validate.spec.js` | `invalid — enter a duplicate value in row 0 and validate` | Backend returns `{ valid: false, errors: [...] }` → warning alert shown |
| `candidate-mode.spec.js` | `candidate mode — adding a candidate shows it as a small number in the cell` | Switch to Candidate mode, click empty cell → candidate digit appears inside `CandidateDisplay` |
| `candidate-mode.spec.js` | `candidate mode — normal mode fills the cell with the selected number` | Default Normal mode, click empty cell → large fill value rendered |
| `hint.spec.js` | `hint — clicking Hint fills the hinted cell with the correct value` | Dialog opens, progresses through nudge → focus → reveal stages |
| `auto-notes.spec.js` | `auto-notes — clicking Auto-Notes shows candidates in empty cells` | `**/puzzles/candidates` returns candidate grid → candidates rendered in empty cells |
| `auto-notes.spec.js` | `auto-notes — clicking Auto-Notes again hides candidates` | Second click toggles `autoNotesActive` off → no candidates shown, no second API call |
| `new-game.spec.js` | `new-game — selecting Hard difficulty and starting a new game loads the puzzle` | Change difficulty to Hard, click New Game → second generate route fires, grid reloads |
| `clear-cell.spec.js` | Clear button removes user-entered values | Clear removes a filled cell value; no-op on given cells |
| `error-cells.spec.js` | Error cell background highlighting | Single/multiple errors highlighted; clears on new value entry |
| `number-select.spec.js` | Number selection in normal mode | Selecting a number fills selected cell |
| `undo.spec.js` | Undo last move | Undo restores previous cell state |
| `solved.spec.js` | Solved board detection | Completing the puzzle shows solved state |

All spec files intercept `**/puzzles/generate**` via shared `setupGenerateRoute` helper. Per-file routes are added as needed for validate, hint, and candidates endpoints.

### Known brittle assertions
- `error-cells.spec.js` asserts `background-color: rgb(239, 83, 80)` (MUI v7 `error.light`). If the MUI theme is customised this will break. Consider asserting via `aria-invalid` or a `data-error` attribute instead.
- `EASY_PUZZLE` in `helpers.js` is a hardcoded 81-cell board. If the generate API contract changes (field names, structure), all specs will break at the same point.

---

## Layer 2b — Integration Tests (Docker Compose + Playwright)

**Status: in place (happy-path journeys only)**

### Purpose
These tests run the real backend and the real built frontend together. They catch: API contract breaks, serialisation mismatches, CORS misconfiguration, and environment variable wiring — none of which the mocked E2E tests can detect.

### Tool
Playwright (no `page.route()` mocking) against a `docker-compose.test.yml` stack.

### Location
`ui/tests/integration/` — separate from the mocked E2E tests.

### Config
`ui/playwright.integration.config.js` — targets port 5174 (UI container), no `webServer` block.

### Docker Compose stack
`docker-compose.test.yml` at the repo root:
- `backend` service — built from `backend/Dockerfile.test` (multi-stage Maven + JVM image), port 8080, healthcheck on `/api/v1/puzzles/generate`
- `ui` service — built from `ui/Dockerfile.test` (Node build + `vite preview`), port 5174, `VITE_MOCK_API=false`

### Running locally

```bash
# Start stack
docker compose -f docker-compose.test.yml up -d --build

# Run integration tests (once backend is healthy)
cd ui && npx playwright test --config playwright.integration.config.js

# Tear down
docker compose -f docker-compose.test.yml down
```

### Scope (keep small — happy paths only)
| Test | What it verifies |
|---|---|
| Load puzzle grid (9×9 cells visible) | `GET /puzzles/generate` contract, serialisation |
| Change difficulty, start new game | Query param wiring, grid reload |
| Click Check on valid partial board | `POST /puzzles/validate` contract, no error alert |
| Click Hint, dialog appears | `POST /puzzles/hint` contract, dialog renders |
| Click Auto-Notes, candidates visible | `POST /puzzles/candidates` contract, candidates render |

### Duplication policy
Do **not** duplicate edge-case scenarios from the mocked E2E tests (error highlighting, undo, clear, solved state). Those are covered faster and more reliably by Layer 2.

---

## Layer 3 — Backend Unit Tests (JUnit 5)

**Status: in place**

### What to test
Pure Java logic in service classes and domain models — puzzle generation algorithm, validation rules, hint strategies, board domain — without HTTP or CDI container overhead.

### Tool
JUnit 5 — already on the classpath via `quarkus-junit5` in `pom.xml`.

### Location
`backend/src/test/java/com/sudoku/` — plain `*Test.java` files, no `@QuarkusTest`.

### Running

```bash
cd backend
./mvnw test
```

### Test files

| File | Scope |
|---|---|
| `domain/CellTest.java` | Cell construction, candidate operations, defensive copy |
| `domain/BoardTest.java` | Board construction, row/col/block retrieval, candidate calculation |
| `puzzle/PuzzleGeneratorTest.java` | Valid 9×9 output, no clue conflicts, clue counts per difficulty, unique solution, seed reproducibility |
| `puzzle/SudokuServiceImplTest.java` | Full service integration including hint strategy chain |
| `puzzle/MockSudokuServiceTest.java` | Candidate calculation, validation (row/col/block duplicates, deduplication) |
| `puzzle/hint/FullHouseStrategyTest.java` | Detects rows/columns/blocks with exactly one empty cell |
| `puzzle/hint/NakedSingleStrategyTest.java` | Finds cells with only one candidate |
| `puzzle/hint/NakedPairStrategyTest.java` | Identifies pairs of cells with identical candidate sets |

---

## Layer 4 — Backend API Tests (Rest-Assured + @QuarkusTest)

**Status: in place**

### What to test
Full HTTP request/response cycle: request deserialisation → service logic → response serialisation → correct HTTP status codes and JSON shape.

### Tool
Rest-Assured + `@QuarkusTest` — starts the real Quarkus application in test mode with full CDI wiring.

### Location
`backend/src/test/java/com/sudoku/puzzle/PuzzleResourceTest.java`

### Running

```bash
cd backend
./mvnw test                        # unit + @QuarkusTest API tests
./mvnw verify -DskipITs=false      # also runs packaged/native integration tests
```

### Scope

| Endpoint | Tests |
|---|---|
| `GET /puzzles/generate` | 200 with 9×9 `originalGrid`; default difficulty; `easy` difficulty; `hard` difficulty |
| `POST /puzzles/validate` | 200 valid partial board (`isValid=true`, `isSolved=false`, empty errors); solved board (`isSolved=true`); board with row duplicate (errors list ≥ 2) |
| `POST /puzzles/hint` | 200 with `techniqueName` and `nudge` for partial board; 404 for solved board |
| `POST /puzzles/candidates` | 200 with 9×9 `candidatesGrid`; filled cell returns empty candidate list |

---

## Layer 5 — Coverage

### Backend (JaCoCo)

**Status: in place**

JaCoCo is configured in `pom.xml`:
- `prepare-agent` execution instruments the JVM during `test`
- `report` execution generates HTML + XML under `target/site/jacoco/` during `verify`
- `check` execution fails the build if line coverage drops below **70%**

```bash
cd backend
./mvnw verify        # runs tests + generates report + enforces threshold
open target/site/jacoco/index.html
```

CI uploads the report as a build artifact (`jacoco-report`, retained 14 days).

### Frontend (Vitest + v8)

**Status: deferred — implement after Layer 1 Vitest tests exist**

When ready:
1. `npm install --save-dev @vitest/coverage-v8`
2. Add `"test:coverage": "vitest run --coverage"` to `package.json`
3. Configure `coverage.reporter: ['text', 'lcov']` in `vite.config.js`
4. Add threshold rules to `vite.config.js` `coverage` block

---

## CI Structure

```yaml
jobs:
  ui:           # Lint + mocked Playwright E2E
  backend:      # Maven verify (unit + @QuarkusTest) + JaCoCo threshold + artifact upload
  integration:  # needs: [ui, backend] — Docker Compose stack + Playwright integration tests
  infra:        # Terraform fmt + validate
```

### Key CI notes
- `VITE_MOCK_API` must not be set (or must be `false`) in CI. The `.env.test` file already handles this for Playwright runs.
- The `integration` job runs only after both `ui` and `backend` pass, minimising unnecessary Docker builds.
- JaCoCo artifact is uploaded `if: always()` so the report is available even when the coverage threshold causes a failure.
- The `integration` job tears down the Docker Compose stack `if: always()` to avoid orphaned containers.
