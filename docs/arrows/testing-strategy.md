# Arrow: Testing Strategy

Cross-cutting test pyramid — unit, integration, and E2E layers across frontend and backend.

## Status

**OK** — 2026-04-22. Test infrastructure in place across all layers.

## References

### HLD
- docs/high-level-design.md — Dev/Test Isolation section

### LLD
- docs/llds/react-frontend.md — Testing Setup section
- docs/llds/cloud-platform.md — CI/CD Deployment Pipeline section (integration test native approach)

### Code
- `ui/src/**/*.test.js` — frontend unit tests (Vitest + React Testing Library)
- `ui/tests/e2e/` — frontend E2E tests (Playwright, mocked backend)
- `ui/tests/integration/` — integration tests (Playwright, real backend)
- `ui/playwright.integration.config.js`
- `ui/tests/coach-quality/` — AI coach quality tests (Playwright, real backend + real Bedrock)
- `ui/playwright.coach-quality.config.js`, `docker-compose.coach-quality.yml`, `scripts/local/coach-quality-test.sh`
- `backend/src/test/java/com/sudoku/` — backend unit + API tests (JUnit 5, Rest-Assured)
- `.github/actions/integration-tests/action.yml`

## Architecture

**Purpose:** Define where and how each layer of the test pyramid is implemented, what each layer covers, and how environment variables must be configured.

---

## Environment Variable Rules

| Environment | File | `VITE_MOCK_API` | `VITE_SKIP_AUTH` | Effect |
| --- | --- | --- | --- | --- |
| Developer (no backend) | `.env.development.local` (git-ignored) | `true` | — | Canned responses; no fetch calls; auth bypassed |
| Playwright E2E tests | `.env.test` | `false` | `true` | Real fetch → intercepted by `page.route()`; auth bypassed |
| Integration tests | build args / env vars | `false` | `true` | Real fetch → real backend; backend DevUserFilter injects mock user |
| Production / CI with backend | `.env` or env vars | `false` | `false` / unset | Real fetch → real backend; Cognito Authenticator active |

**Rules:**
- `VITE_MOCK_API=true` only in `.env.development.local` (git-ignored) — developer convenience only
- `VITE_MOCK_API=false` in `.env.test` — required so `page.route()` interception fires
- Never set `VITE_MOCK_API=true` in CI or `.env.test`; it silences all fetch calls and makes `page.route()` stubs unreachable
- `VITE_SKIP_AUTH=true` bypasses the Authenticator wrapper — use in test environments only; never in production

---

## Layer 1 — Frontend Unit Tests (Vitest)

**Status: in place**

### What to test
- Pure logic in hooks: `useSudokuGame.js` — state transitions, action handlers, edge cases
- Utility/API branching logic in `sudokuApi.js` — mock vs real path selection

### Tool
Vitest with jsdom environment and React Testing Library.

### Location
`ui/src/**/*.test.js` — co-located with source files.

### Running
```bash
cd ui
npm run test:coverage
```

### Key setup
- `ui/src/test-setup.js` provides in-memory `localStorage`/`sessionStorage` (jsdom 26 does not expose them natively)
- Tests that mock `sudokuApi.js` directly must provide pre-unwrapped plain arrays since the real wire adapter code is bypassed
- Mock data in `mocks/cannedData.js` is in wire format (`{rows: [...]}`) — same as production API responses

---

## Layer 2 — Frontend E2E Tests (Playwright, mocked backend)

**Status: in place**

### What to test
Full user journeys through a real browser: load puzzle, interact with cells, validate board, observe status feedback.

### Tool
Playwright with `page.route()` for HTTP interception. Routes registered before navigation so no real network calls leave the browser.

### Location
`ui/tests/e2e/`

### Running
```bash
cd ui
npm run test:e2e       # headless
npm run test:e2e:ui    # interactive / trace viewer
```

### Key principle
Intercept at the HTTP layer — never duplicate validation logic inside the test. The test provides a canned JSON response and asserts on what the UI renders.

### Known brittle assertions
- `error-cells.spec.js` asserts `background-color: rgb(239, 83, 80)` (MUI `error.light` — verified against v9). If the MUI theme changes this will break — consider asserting via `aria-invalid` or a `data-error` attribute instead.
- `EASY_PUZZLE` in `helpers.js` is a hardcoded 81-cell board. If the generate API contract changes, all specs will break at the same point.

---

## Layer 2b — Integration Tests (Native + Playwright)

**Status: in place (happy-path journeys only)**

### Purpose
Run the real backend and real frontend together. Catches: API contract breaks, serialisation mismatches, CORS misconfiguration, and environment variable wiring — none of which mocked E2E tests can detect.

### Approach (CI)
Integration tests run natively — no Docker builds:

1. **LocalStack** as a GitHub Actions service container
2. **Backend:** `./mvnw quarkus:dev` in background (LocalStack at `localhost:4566`, OIDC disabled)
3. **Frontend:** `npm run dev --port 5174` with `VITE_MOCK_API=false VITE_SKIP_AUTH=true`
4. **Playwright:** against `http://localhost:5174`

### Running locally (matching CI)
```bash
# 1. Start LocalStack
docker run -d -p 4566:4566 -e SERVICES=dynamodb localstack/localstack

# 2. Create DynamoDB tables
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
  bash .github/actions/create-localstack-dynamodb/run.sh

# 3. Start backend
cd backend
CORS_ALLOWED_ORIGINS=http://localhost:5174 \
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 \
  ./mvnw quarkus:dev -Dquarkus.http.host=0.0.0.0 -Ddebug=false &

# 4. Start UI
cd ../ui
VITE_API_URL=http://localhost:8080/api/v1 VITE_MOCK_API=false VITE_SKIP_AUTH=true VITE_DEV_TOOLS=true \
  npm run dev -- --port 5174 &

# 5. Run integration tests
npx playwright test --config playwright.integration.config.js
```

### Location
`ui/tests/integration/` — separate from mocked E2E tests.

### Scope (happy paths only)
Do not duplicate edge-case scenarios from the mocked E2E layer. Keep to:
- Load puzzle grid (9×9 cells visible)
- Change difficulty, start new game
- Click Check on valid partial board
- Click Hint, dialog appears
- Click Auto-Notes, candidates visible

---

## Layer 2c — AI Coach Quality Suite (opt-in, API-only diagnostic runner + real Bedrock)

**Status: in place**

### Purpose
A diagnostic runner, not a conventional pass/fail test suite — it exists to replace the manual
walkthroughs in `docs/tests/ai-coach.md` (play in a browser, download CloudWatch logs by hand)
with a scripted scenario against the real backend, real DynamoDB Local, and real Bedrock, that
writes out everything the system did (every request/response, every correlated structured log
line) for manual analysis and to drive bug fixes. Assertions exist too, but the report is the
primary deliverable — written whether or not they all pass.

Deliberately **not** part of the CI gate or `local-alltests.sh`: real LLM calls are
non-deterministic prose, cost real (small) tokens, and require AWS credentials with
`bedrock:InvokeModel`. Run on demand when evaluating coach behaviour.

### No browser
Nothing here drives a UI. `POST /puzzles/hint` and `POST /ai/coach` both take the board
directly in the request body — no persisted game state needed — and `PATCH /games/{id}`'s
`events` field is untrusted, client-supplied observability data that `PuzzleEventLogger` logs
verbatim regardless of where it came from. So scenarios construct
`NUMBER`/`NUMBER_CLEAR`/`UNDO`/`HINT_REQUEST`/`HINT_RESPONSE` events directly and PATCH them
in — no real UI interaction ever happens. `docker-compose.coach-quality.yml` forwards real AWS
credentials into the `backend` container (keeping DynamoDB local) so coach replies are genuine,
and `scripts/local/coach-quality-test.sh` never starts the `ui` container.

### What it captures and asserts
- The full trace of every action, request/response, and correlated structured log line,
  written to `ui/tests/coach-quality/reports/` (JSON + human-readable Markdown) regardless of
  outcome — see the README for the report contents.
- `fallback` — read from the backend's structured log, since `CoachResponse` never returns
  this field to the caller (see `backend/.../coach/web/CoachResponse.java`).
- The hint engine's chosen technique cross-checked against the technique the coach's own
  request context was built from, for the same board.
- Board validity read back via `GET /games/{id}`, not just what was sent.

### Location
`ui/tests/coach-quality/` — scenarios as a starting grid + ordered action array
(`scenarios/*.js`), interpreted by `lib/runner.js` and driven by the thin
`coach-quality.spec.js` wrapper. Grids are seeded via the existing `POST /games/from-image`
endpoint (real, persisted `gameId`) rather than the Layer 2b demo-grid loader, which
intentionally never persists (`gameId: null`) and so can't be correlated against logs.

See `ui/tests/coach-quality/README.md` for the full action/assertion reference and how to add
a scenario.

### Running
```bash
AWS_PROFILE=sandbox bash scripts/local/coach-quality-test.sh
```

---

## Layer 3 — Backend Unit Tests (JUnit 5)

**Status: in place**

### What to test
Pure Java logic in service classes and domain models — puzzle generation, validation rules, hint strategies, board domain — without HTTP or CDI container overhead.

### Tool
JUnit 5 — via `quarkus-junit5` in `pom.xml`. No `@QuarkusTest` annotation on plain unit tests.

### Location
`backend/src/test/java/com/sudoku/` — `*Test.java` files.

### Running
```bash
cd backend
./mvnw test
```

### Key test files

| File | Scope |
| --- | --- |
| `domain/CellTest.java` | Cell construction, candidate operations |
| `domain/BoardTest.java` | Board construction, row/col/block retrieval, candidate calculation |
| `puzzle/PuzzleGeneratorTest.java` | Valid 9×9 output, clue counts, unique solution, seed reproducibility |
| `puzzle/SudokuServiceImplTest.java` | Full service integration including hint strategy chain |
| `puzzle/hint/FullHouseStrategyTest.java` | Rows/columns/blocks with exactly one empty cell |
| `puzzle/hint/NakedSingleStrategyTest.java` | Cells with only one candidate |
| `puzzle/hint/NakedPairStrategyTest.java` | Pairs of cells with identical candidate sets |

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
./mvnw test                      # unit + @QuarkusTest API tests
./mvnw verify -DskipITs=false    # also runs integration tests
```

---

## Layer 5 — Coverage

### Backend (JaCoCo)
**Status: in place**

JaCoCo configured in `pom.xml`: line coverage threshold 70%, HTML + XML reports under `target/site/jacoco/`.

```bash
cd backend
./mvnw verify        # tests + report + threshold enforcement
open target/site/jacoco/index.html
```

CI uploads the report as a build artifact (`jacoco-report`, 14-day retention), uploaded `if: always()` so the report is available even when the threshold causes a build failure.

### Frontend (Vitest + v8)
**Status: in place**

```bash
cd ui
npm run test:coverage
```

---

## CI Structure

```
jobs:
  gate-ui:      # Lint + mocked Playwright E2E
  gate-backend: # Maven verify (unit + @QuarkusTest) + JaCoCo threshold
  integration:  # needs: [gate-ui, gate-backend] — native quarkus:dev + Playwright
  infra:        # Terraform fmt + validate
```

**Key CI invariants:**
- `VITE_MOCK_API` must not be set (or must be `false`) in CI — `.env.test` handles this for Playwright
- `VITE_SKIP_AUTH=true` is set in `.env.test` and for integration tests — never in production
- The `integration` job runs only after both `gate-ui` and `gate-backend` pass
- JaCoCo artifact uploaded `if: always()` so the report is available even on threshold failure
- Integration tests are always skipped for Dependabot branches
