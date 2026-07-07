# Backend — Java / Quarkus

Java 21 REST API built with Quarkus, deployed as an AWS Lambda function behind API Gateway HTTP v2.

---

## Tech Stack

| | |
|---|---|
| Language | Java 21 |
| Framework | Quarkus 3.36.1 (`quarkus-amazon-lambda-rest`) |
| Auth | `quarkus-oidc` — JWT validation via Cognito OIDC discovery |
| Database | AWS DynamoDB (`quarkus-amazon-dynamodb-enhanced`) |
| Test | JUnit 5, Mockito, RestAssured, LocalStack (integration tests) |
| Build | Maven Wrapper (`./mvnw`) |

---

## Local Development

```bash
./mvnw quarkus:dev      # hot reload on http://localhost:8080
```

In `dev` mode, OIDC is disabled and a `DevUserFilter` injects `local-dev-user` as the authenticated user — no Cognito or Google login required.

`DevDatabaseInitializer` automatically creates the required DynamoDB tables in LocalStack on startup in `dev` mode — no manual table creation needed.

The Quarkus Dev UI is available at <http://localhost:8080/q/dev/>.

---

## Source Structure

```
src/main/java/com/sudoku/
├── auth/
│   └── AllowedUsersFilter.java   # Server-side email allowlist; rejects non-allowlisted JWTs with 403
├── cors/
│   └── CorsFilter.java           # JAX-RS filter adding CORS headers
├── developer/
│   ├── DevDatabaseInitializer.java  # Dev-only: creates DynamoDB tables in LocalStack on startup
│   └── DevUserFilter.java           # Dev/test/IT only — injects mock SecurityContext when no JWT present
├── domain/
│   ├── Board.java                # Core 9×9 board model with row/col/block views and candidate logic
│   ├── Cell.java                 # Individual cell (position, value, pencil-mark candidates)
│   └── SudokuConstants.java      # UNIT_SIZE, BOX_SIZE, MIN_DIGIT, MAX_DIGIT
├── dto/                          # Record types for request/response bodies
│   ├── ActionableCell.java       # (row, col, value) — cell with a determined digit
│   ├── BoardRequest.java         # currentGrid + optional solutionGrid, minRank, excludedRanks
│   ├── CandidatesResponse.java   # 9×9 grid of candidate lists per cell
│   ├── ChatMessage.java          # (role, content) — AI coach conversation turn
│   ├── CoachRequest.java         # board + history + userMessage for AI coaching
│   ├── CoachResponse.java        # aiMessage + HintResponse + revealHint
│   ├── Coordinate.java           # (row, col) position
│   ├── CoordinateCandidate.java  # (row, col, value) pencil-mark candidate
│   ├── CreateGameFromGridRequest.java  # originalGrid for image-recognition import
│   ├── GameState.java            # userId, gameId, grids, candidates, status …
│   ├── GameUpdateRequest.java    # currentGrid, candidates, time, completion flag, hintsUsed
│   ├── HintResponse.java         # Full hint payload: technique, nudge/focus/reveal, highlighted cells
│   ├── PuzzleResponse.java       # originalGrid, solutionGrid, difficulty
│   └── ValidationResponse.java  # isValid, isSolved, error coordinates
├── game/
│   ├── GameResource.java         # POST /games, POST /games/from-image, GET /games/current,
│   │                             #   GET /games/{id}, PATCH /games/{id}
│   ├── GameService.java          # Interface
│   ├── GameServiceImpl.java
│   ├── GameStatus.java           # Enum: IN_PROGRESS, SOLVED, IMPORTED
│   ├── GameRepository.java       # Interface
│   ├── DynamoDbGameRepository.java
│   └── GameItem.java             # DynamoDB enhanced client bean (userId PK + gameId SK)
├── health/
│   └── HealthResource.java       # GET /health — liveness probe
├── logging/
│   └── ApiLoggingFilter.java     # Dev-only: logs request/response method, path, body, status
├── player/
│   ├── PlayerResource.java       # GET + PATCH /players/me
│   ├── PlayerService.java
│   ├── PlayerServiceImpl.java    # Lazy profile creation on first login
│   ├── PlayerRepository.java
│   ├── DynamoDbPlayerRepository.java
│   ├── PlayerItem.java           # DynamoDB enhanced client bean (userId PK)
│   ├── PlayerProfile.java        # Record DTO for player data
│   ├── PlayerUpdateRequest.java  # PATCH request body (displayName, avatarKey, aiCoachEnabled)
│   ├── InvalidPlayerUpdateException.java
│   └── PlayerNotFoundException.java
└── puzzle/
    ├── PuzzleResource.java       # GET /puzzles/generate, POST /puzzles/validate|hint|candidates
    ├── SudokuService.java        # Interface
    ├── SudokuServiceImpl.java
    ├── PuzzleGenerator.java      # Randomised backtracking + hole-digging with uniqueness check
    ├── CoachResource.java        # POST /ai/coach — toggle/budget/rate-limit checks + AI call
    ├── SudokuCoachService.java   # Interface (returns CoachResult sealed type)
    ├── SudokuCoachServiceImpl.java  # Orchestrates hint engine → BedrockCoachClient
    ├── BedrockCoachClient.java   # Raw BedrockRuntimeClient call, prompt caching, fallback
    ├── BedrockClientProducer.java  # CDI producer for BedrockRuntimeClient
    ├── BoardFormatter.java       # Converts Board to human-readable row-by-row string
    ├── CoachRateLimiter.java     # Per-user per-minute rate limiting via DynamoDB conditional writes
    ├── developer/
    │   ├── DevResource.java         # GET /dev/hint-demo?technique=<slug>
    │   ├── HintDemoGrids.java       # Loads pre-baked grids from resources/developer/
    │   └── MockSudokuService.java   # Test helper with hardcoded grids
    └── hint/
        ├── HintStrategy.java        # Interface: evaluate(Board), rank, difficulty, name, slug
        ├── BoardUtils.java          # Static helpers: candidateColumnsInRow, isVisible
        ├── Difficulty.java          # Enum: EASY, MEDIUM, HARD
        ├── FullHouseStrategy.java   # Rank 10 — one empty cell remaining in a unit
        ├── NakedSingleStrategy.java # Rank 20 — cell with exactly one candidate
        ├── NakedPairStrategy.java   # Rank 30 — two cells sharing same two candidates
        ├── HiddenSingleStrategy.java # Rank 40 — digit in exactly one cell of a unit
        ├── PointingPairStrategy.java # Rank 50 — digit confined to one row/col within a block
        ├── NakedTripleStrategy.java  # Rank 60 — three cells with three shared candidates
        ├── HiddenPairStrategy.java   # Rank 70 — two digits confined to two cells
        ├── HiddenTripleStrategy.java # Rank 80 — three digits confined to three cells
        ├── XWingStrategy.java        # Rank 90 — 2×2 fish pattern
        ├── SwordfishStrategy.java    # Rank 100 — 3×3 fish pattern
        └── YWingStrategy.java        # Rank 110 — pivot cell with two pincers
```

---

## API Endpoints

**Public** (no authentication):

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/health` | Health check |
| GET | `/api/v1/puzzles/generate` | Generate a puzzle (`?difficulty=easy\|medium\|hard\|expert`) |
| POST | `/api/v1/puzzles/validate` | Validate a board state |
| POST | `/api/v1/puzzles/hint` | Get a progressive hint |
| POST | `/api/v1/puzzles/candidates` | Calculate all valid candidates |

**Protected** (JWT Bearer token required — enforced by API Gateway, not Lambda):

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/games` | Create a new game |
| POST | `/api/v1/games/from-image` | Create a game from an externally recognised grid |
| GET | `/api/v1/games/current` | Get the player's current in-progress game |
| GET | `/api/v1/games/{gameId}` | Load a saved game by ID |
| PATCH | `/api/v1/games/{gameId}` | Save game progress |
| GET | `/api/v1/players/me` | Get or create the current user's profile |
| PATCH | `/api/v1/players/me` | Update profile (displayName, avatarKey, aiCoachEnabled) |
| POST | `/api/v1/ai/coach` | AI coaching message (rc-* workspaces only) |
| POST | `/api/v1/ai/scan` | Submit image for puzzle grid extraction (rc-* workspaces only) |
| GET | `/api/v1/ai/scan/warmup` | Warm up the image recognition Lambda — public, no JWT (rc-* only) |

**Developer-only** (`dev` profile):

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/dev/hint-demo` | Return a pre-baked grid for a given hint technique (`?technique=<slug>`) |

The JWT is validated by the API Gateway JWT authorizer (Cognito issuer/audience). Quarkus extracts the `userId` from the `SecurityContext` via `quarkus-oidc` in production, or from the `DevUserFilter` mock in `dev`/`test`/`it` profiles.

---

## Testing

### Unit tests (no infrastructure required)

```bash
./mvnw test
```

Pure unit tests using JUnit 5 and Mockito for domain logic and service classes, plus `@QuarkusTest` API tests (RestAssured) that start the full Quarkus CDI container. The `@QuarkusTest` tests target LocalStack via `src/test/resources/application.properties` — LocalStack is started automatically by `DevDatabaseInitializer`.

### Integration tests

```bash
./mvnw verify -DskipITs=false
```

Uses `@QuarkusIntegrationTest` to test the packaged Lambda zip against LocalStack. CI handles LocalStack setup automatically via `.github/actions/create-localstack-dynamodb/`.

---

## Building

```bash
# JVM jar (used for Lambda deployment)
./mvnw package

# Lambda deployment zip (produced by quarkus-amazon-lambda-rest)
# Output: target/function.zip
./mvnw package -DskipTests

# Native executable (requires GraalVM — optional, reduces cold starts)
./mvnw package -Dnative

# Native in Docker (no local GraalVM needed)
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

The CI deploy workflow builds `target/function.zip` and uploads it to S3 for Terraform to reference.

---

## Quarkus Build Profiles

Quarkus selects a profile at build/run time. This controls which beans are included and which `application.properties` overrides apply.

| Profile | Activated by | OIDC | `DevUserFilter` | DynamoDB target | Purpose |
|---------|-------------|------|-----------------|-----------------|---------|
| `dev` | `./mvnw quarkus:dev` | Disabled | Active (injects `local-dev-user`) | LocalStack `:4566` | Hot-reload local development — no Cognito required |
| `test` | `@QuarkusTest` (Maven `test` phase) | Disabled | Active (injects `local-dev-user`) | LocalStack `:4566` (via `src/test/resources/application.properties`) | `@QuarkusTest` API tests run in CI and locally against LocalStack |
| `it` | `@QuarkusIntegrationTest` (`-DskipITs=false`) | Disabled | Active (injects `local-dev-user`) | LocalStack `:4566` | Packaged JAR integration tests; Lambda zip runs in-process against LocalStack |
| `prod` | Default (no active profile flag) | Enabled (Cognito issuer) | Excluded from build | Real DynamoDB (env-var injected) | Lambda deployed to AWS — API Gateway validates JWTs before invocation |

`DevUserFilter` is compiled into the `dev`, `test`, and `it` builds only (`@IfBuildProfile`). It is absent from the production Lambda zip.

---

## Configuration

Key properties in `src/main/resources/application.properties`:

| Property | Description |
|---|---|
| `quarkus.oidc.auth-server-url` | Cognito issuer URL (injected via `COGNITO_ISSUER_URL` env var) |
| `quarkus.oidc.client-id` | Cognito app client ID (injected via `COGNITO_CLIENT_ID` env var) |
| `quarkus.oidc.application-type` | `service` — Bearer token mode, no redirect |
| `sudoku.dynamodb.table-name` | `SudokuGames` table name (injected via `GAMES_TABLE_NAME` env var) |
| `sudoku.dynamodb.players-table-name` | `SudokuPlayers` table name (injected via `PLAYERS_TABLE_NAME` env var) |
| `sudoku.cors.allowed-origins` | Comma-separated list of allowed CORS origins (injected via `CORS_ALLOWED_ORIGINS` env var) |
| `app.allowed.emails` | Comma-separated email allowlist; empty string disables the check (dev/test default) |
| `coach.monthly-token-limit` | Max Bedrock tokens per player per month (default: 100,000; injected via `COACH_MONTHLY_TOKEN_LIMIT`) |
| `coach.rate-limit.table-name` | DynamoDB table for per-minute rate limiting (injected via `COACH_RATE_LIMIT_TABLE_NAME`) |
| `coach.rate-limit.per-minute` | Max AI coach calls per user per UTC minute (default: 5; dev default: 1000) |
| `coach.bedrock.model-id` | Bedrock inference profile ID for Claude Haiku (injected via `COACH_BEDROCK_MODEL_ID`) |

In `dev`, `test`, and `it` profiles, OIDC is disabled and proactive auth is turned off so the `DevUserFilter` can inject a mock identity.
