# Backend — Java / Quarkus

Java 21 REST API built with Quarkus, deployed as an AWS Lambda function behind API Gateway HTTP v2.

---

## Tech Stack

| | |
|---|---|
| Language | Java 21 |
| Framework | Quarkus 3.x (`quarkus-amazon-lambda-rest`) |
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

The Quarkus Dev UI is available at <http://localhost:8080/q/dev/>.

---

## Source Structure

```
src/main/java/com/sudoku/
├── auth/
│   └── DevUserFilter.java        # Dev/IT only — injects mock SecurityContext when no JWT present
├── cors/
│   └── CorsFilter.java           # JAX-RS filter adding CORS headers
├── domain/
│   ├── Board.java                # Core 9×9 board model
│   └── Cell.java                 # Individual cell
├── dto/                          # Record types for request/response bodies
│   ├── GameState.java            # userId, gameId, grids, candidates, status …
│   ├── GameUpdateRequest.java
│   ├── PuzzleResponse.java
│   ├── HintResponse.java
│   └── …
├── game/
│   ├── GameResource.java         # POST /games, GET /games/{id}, PATCH /games/{id}
│   ├── GameService.java          # Interface
│   ├── GameServiceImpl.java
│   ├── GameRepository.java       # Interface
│   ├── DynamoDbGameRepository.java
│   └── GameItem.java             # DynamoDB enhanced client bean (userId PK + gameId SK)
├── health/
│   └── HealthResource.java       # GET /health
├── logging/
│   └── ApiLoggingFilter.java     # Request/response logging
├── player/
│   ├── PlayerResource.java       # GET /players/me
│   ├── PlayerService.java
│   ├── PlayerServiceImpl.java    # Lazy profile creation on first login
│   ├── PlayerRepository.java
│   ├── DynamoDbPlayerRepository.java
│   └── PlayerItem.java           # DynamoDB enhanced client bean (userId PK)
└── puzzle/
    ├── PuzzleResource.java       # GET /puzzles/generate, POST /puzzles/validate|hint|candidates
    ├── SudokuService.java
    ├── SudokuServiceImpl.java
    ├── PuzzleGenerator.java
    └── hint/
        ├── HintStrategy.java
        ├── FullHouseStrategy.java
        ├── NakedSingleStrategy.java
        └── NakedPairStrategy.java
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
| GET | `/api/v1/games/{gameId}` | Load a saved game |
| PATCH | `/api/v1/games/{gameId}` | Save game progress |
| GET | `/api/v1/players/me` | Get or create the current user's profile |

The JWT is validated by the API Gateway JWT authorizer (Cognito issuer/audience). Quarkus extracts the `userId` from the `SecurityContext` via `quarkus-oidc` in production, or from the `DevUserFilter` mock in `dev`/`it` profiles.

---

## Testing

### Unit tests

```bash
./mvnw test
```

Pure unit tests using Mockito — no infrastructure required.

### Integration tests

```bash
./mvnw verify -DskipITs=false
```

Uses `@QuarkusTest` + RestAssured. DynamoDB is pointed at LocalStack. Start LocalStack first:

```bash
docker run --rm -d -p 4566:4566 localstack/localstack
# then create the required tables:
aws --endpoint-url=http://localhost:4566 dynamodb create-table \
  --table-name SudokuGames \
  --attribute-definitions AttributeName=userId,AttributeType=S AttributeName=gameId,AttributeType=S \
  --key-schema AttributeName=userId,KeyType=HASH AttributeName=gameId,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST --region us-east-1

aws --endpoint-url=http://localhost:4566 dynamodb create-table \
  --table-name SudokuPlayers \
  --attribute-definitions AttributeName=userId,AttributeType=S \
  --key-schema AttributeName=userId,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST --region us-east-1
```

CI handles LocalStack setup automatically via `.github/actions/create-localstack-dynamodb/`.

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

## Configuration

Key properties in `src/main/resources/application.properties`:

| Property | Description |
|---|---|
| `quarkus.oidc.auth-server-url` | Cognito issuer URL (injected via `COGNITO_ISSUER_URL` env var) |
| `quarkus.oidc.client-id` | Cognito app client ID (injected via `COGNITO_CLIENT_ID` env var) |
| `quarkus.oidc.application-type` | `service` — Bearer token mode, no redirect |
| `sudoku.dynamodb.table-name` | `SudokuGames` table name (injected via `GAMES_TABLE_NAME` env var) |
| `sudoku.dynamodb.players-table-name` | `SudokuPlayers` table name (injected via `PLAYERS_TABLE_NAME` env var) |

In `dev` and `it` profiles, OIDC is disabled (`%dev.quarkus.oidc.enabled=false`) and proactive auth is turned off so the `DevUserFilter` can inject a mock identity.
