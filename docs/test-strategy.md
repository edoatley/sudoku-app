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
npm install --save-dev vitest @testing-library/react @testing-library/user-event jsdom
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
"test:unit": "vitest run"
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
| `hint.spec.js` | `hint — clicking Hint fills the hinted cell with the correct value` | `**/puzzles/hint` returns `{ coordinate: {row,col}, value }` → cell updated |
| `auto-notes.spec.js` | `auto-notes — clicking Auto-Notes shows candidates in empty cells` | `**/puzzles/candidates` returns candidate grid → candidates rendered in empty cells |
| `auto-notes.spec.js` | `auto-notes — clicking Auto-Notes again hides candidates` | Second click toggles `autoNotesActive` off → no candidates shown, no second API call |
| `new-game.spec.js` | `new-game — selecting Hard difficulty and starting a new game loads the puzzle` | Change difficulty to Hard, click New Game → second generate route fires, grid reloads |

All spec files intercept `**/puzzles/generate**` via shared `setupGenerateRoute` helper. Per-file routes are added as needed for validate, hint, and candidates endpoints.

---

## Layer 3 — Backend Unit Tests (JUnit 5)

**Status: not yet implemented**

### What to test
Pure Java logic in service classes — puzzle generation algorithm, validation rules, hint logic — without HTTP or CDI container overhead.

### Tool
JUnit 5 — already on the classpath via `quarkus-junit5` in `pom.xml`.

### Location
`backend/src/test/java/com/sudoku/` — plain `*Test.java` files, no `@QuarkusTest`.

### Running

```bash
cd backend
./mvnw test
```

### Scope (when service classes are built)
- `SudokuService.generatePuzzle()` — returns a valid, solvable puzzle for each difficulty
- `SudokuService.validateBoard()` — correct error detection for row, column, and box conflicts
- `SudokuService.getHint()` — returns a valid cell/value pair from the solution

---

## Layer 4 — Backend API / Integration Tests (Rest-Assured + @QuarkusTest)

**Status: scaffolded (`GreetingResourceTest.java`), real tests not yet implemented**

### What to test
Full HTTP request/response cycle: request deserialization → service logic → response serialization → correct HTTP status codes and JSON shape.

### Tool
Rest-Assured + `@QuarkusTest` — starts the real Quarkus application in test mode with full CDI wiring.

### Location
`backend/src/test/java/com/sudoku/`
- `*Test.java` — runs against the JVM-mode application (`./mvnw test`)
- `*IT.java` — runs against the packaged or native binary (`./mvnw verify -DskipITs=false`)

### Running

```bash
cd backend
./mvnw test                        # unit + @QuarkusTest API tests
./mvnw verify -DskipITs=false      # also runs packaged/native integration tests
```

### Scope (when real endpoints replace `GreetingResource`)
- `GET /api/v1/puzzles/generate?difficulty=easy` → 200 with valid puzzle JSON
- `POST /api/v1/puzzles/validate` → 200 with `{ valid, errors, message }`
- Malformed request bodies → 400 with useful error message

---

## CI Considerations (future)

```yaml
# Frontend
- run: npm ci && npm run test:e2e
  env:
    CI: true          # Playwright uses single worker and retries on flake

# Backend
- run: ./mvnw verify -DskipITs=false   # includes unit + integration tests
```

`VITE_MOCK_API` must not be set (or must be `false`) in CI. The `.env.test` file already handles this for Playwright runs.
