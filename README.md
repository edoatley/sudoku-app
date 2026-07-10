# Serverless Sudoku

![CI](https://github.com/edoatley/sudoku-app/actions/workflows/ci-deploy.yml/badge.svg)

A serverless Sudoku application with a Java/Quarkus backend on AWS Lambda and a React frontend on AWS Amplify.

## Contents

- [High-Level Architecture](#high-level-architecture)
- [Screenshots](#screenshots)
- [Tech Stack](#tech-stack)
- [Repository Structure](#repository-structure)
- [Documentation](#documentation)
- [Prerequisites](#prerequisites)
- [Local Development](#local-development)
- [API Reference](#api-reference)
- [Testing](#testing)
- [Key Files](#key-files)
- [Infrastructure](#infrastructure)
- [Deployment](#deployment)
- [Contributing](#contributing)

---

## High-Level Architecture

```mermaid
flowchart LR
    subgraph aws["☁️ AWS London"]
        amplify["🌐 Amplify"]
        cognito["🔒 Cognito"]
        apigw["🌐 API Gateway + JWT Authorizer"]
        lambda["⚙️ Lambda\nQuarkus Java 25"]
        imgrec["🖼️ Image Recognition Lambda\nPython 3.14"]
        bedrock["🤖 Amazon Bedrock\nClaude Haiku"]
        dynamo[("🗄️ DynamoDB")]
    end

    amplify --> cognito
    amplify --> apigw
    apigw --> lambda
    apigw --> imgrec
    lambda --> dynamo
    lambda --> bedrock
    imgrec --> bedrock
```

See [`infra/README.md`](infra/README.md) for the full deployment-level architecture diagram (DNS, Amplify branches, IAM).

---

## Screenshots

| Desktop | Mobile |
|---------|--------|
| ![Desktop screenshot](docs/images/desktop-final.png) | ![Mobile screenshot](docs/images/mobile-final.png) |

---

## Tech Stack

| Layer      | Technology                                                         |
|------------|--------------------------------------------------------------------|
| Frontend   | React 19, Vite 8, MUI v9, aws-amplify v6                          |
| Backend    | Java 25, Quarkus 3.36.1, quarkus-amazon-lambda-rest, quarkus-oidc |
| Auth       | Amazon Cognito User Pool (Google social login via OAuth 2.0)       |
| Database   | AWS DynamoDB (`SudokuGames`, `SudokuPlayers`, `SudokuLeaderboard`, `SudokuCoachRateLimits`) |
| Hosting    | AWS Amplify (frontend), AWS Lambda (backend)                       |
| IaC        | Terraform (AWS provider, eu-west-2)                                |

---

## Repository Structure

```
sudoku-app/
├── backend/          # Java 25 + Quarkus REST API (Lambda-optimized)  → backend/README.md
│   └── src/
├── ui/               # React 19 + Vite frontend with MUI               → ui/README.md
│   ├── src/
│   └── e2e/          # Playwright E2E tests
├── image_recognition/  # Python AWS Lambda for puzzle photo scanning
├── infra/            # Terraform IaC (AWS eu-west-2)                   → infra/README.md
├── scripts/          # Local dev, infra, and CI/CD helper scripts      → scripts/README.md
├── docs/             # HLD, LLDs, EARS specs, arrows, openapi.yaml, coding standards
├── Makefile          # Combined dev workflow
└── CLAUDE.md         # AI assistant instructions
```

See the component READMEs for detailed usage:
- [`backend/README.md`](backend/README.md) — Java/Quarkus API, source structure, build profiles, testing
- [`ui/README.md`](ui/README.md) — React/Vite frontend, env vars, component structure
- [`infra/README.md`](infra/README.md) — Terraform resources, multi-env strategy, bootstrap, deploy
- [`scripts/README.md`](scripts/README.md) — Local dev scripts, CI/CD helpers, secret management

---

## Documentation

This project follows a **Linked-Intent** approach, where intent flows `HLD → LLDs → EARS → Tests → Code` and docs are kept in sync with the code rather than left to rot. See [`CLAUDE.md`](CLAUDE.md) for the full workflow.

| Artifact                  | Location                                          | Purpose                                            |
|----------------------------|---------------------------------------------------|-----------------------------------------------------|
| High-Level Design (HLD)    | [`docs/high-level-design.md`](docs/high-level-design.md) | System overview and component map           |
| Low-Level Designs (LLDs)   | [`docs/llds/`](docs/llds/)                        | Per-component detailed design                       |
| EARS specs                 | [`docs/specs/`](docs/specs/)                      | Structured requirements, traced to code and tests   |
| Arrow tracking             | [`docs/arrows/index.yaml`](docs/arrows/index.yaml)| Traces each component from HLD through to code, flags gaps |
| Planning                   | [`docs/planning/`](docs/planning/)                | Implementation plans                                |
| Backlog                    | [`docs/todo/`](docs/todo/)                        | Deferred and future work items                      |

---

## Prerequisites

| Tool          | Version  | Notes                                  |
|---------------|----------|----------------------------------------|
| Java          | 25       | Temurin distribution recommended       |
| Node.js       | 22       |                                        |
| Maven Wrapper | bundled  | Use `./mvnw` — no separate install     |
| Terraform     | latest   | Required for infra work only           |
| GraalVM       | optional | Required for native image builds only  |

---

## Local Development

### Backend

```bash
cd backend
./mvnw quarkus:dev   # hot reload on http://localhost:8080
```

### Frontend

```bash
cd ui
npm install
npm run dev          # dev server on http://localhost:5173
```

### Combined (both services)

```bash
make dev             # starts backend (8080) and frontend (5173) in parallel
```

### Environment Variables

Copy `ui/.env.example` to `ui/.env.development.local` and adjust as needed:

```bash
cp ui/.env.example ui/.env.development.local
```

| Variable                    | Default                        | Description                                      |
|-----------------------------|--------------------------------|--------------------------------------------------|
| `VITE_API_URL`              | `http://localhost:8080/api/v1` | Backend API base URL                             |
| `VITE_MOCK_API`             | `false`                        | Set to `true` to bypass auth and use mock data   |
| `VITE_SKIP_AUTH`            | `false`                        | Set to `true` to bypass the Authenticator wrapper (test environments only) |
| `VITE_DEV_TOOLS`            | `false`                        | Set to `true` to enable developer-only menu items |
| `VITE_AI_COACH`             | `false`                        | Set to `true` to enable the AI Sudoku Coach panel (desktop only) |
| `VITE_COGNITO_USER_POOL_ID` | _(set by Terraform)_           | Cognito User Pool ID (not needed in mock mode)   |
| `VITE_COGNITO_CLIENT_ID`    | _(set by Terraform)_           | Cognito App Client ID (not needed in mock mode)  |
| `VITE_COGNITO_DOMAIN`       | _(set by Terraform)_           | Cognito hosted UI domain (not needed in mock mode) |

---

## API Reference

Base path: `/api/v1`. Full contract: [`docs/openapi.yaml`](docs/openapi.yaml).

**Public routes** (no authentication required):

| Method | Path                  | Description                                                      |
|--------|-----------------------|------------------------------------------------------------------|
| GET    | `/health`             | Health check                                                      |
| GET    | `/puzzles/generate`   | Generate a new puzzle (`?difficulty=easy\|medium\|hard\|expert`) |
| POST   | `/puzzles/validate`   | Validate the current board state                                 |
| POST   | `/puzzles/hint`       | Get a progressive logical hint (Nudge / Focus / Reveal)          |
| POST   | `/puzzles/candidates` | Calculate all valid candidates for empty cells                   |

**AI routes** (all under `/ai/*`, JWT required unless noted, Bedrock-backed):

| Method | Path                  | Description                                  |
|--------|-----------------------|----------------------------------------------|
| POST   | `/ai/coach`           | Send a message to the AI Sudoku coach (desktop only) |
| POST   | `/ai/image-to-puzzle`            | Extract a 9×9 grid from a puzzle photo       |
| GET    | `/ai/image-to-puzzle/warmup` | Warm up the image recognition Lambda (no auth required) |

**Protected routes** (JWT Bearer token required — issued by Cognito after Google login):

| Method | Path                  | Description                                  |
|--------|-----------------------|----------------------------------------------|
| POST   | `/games`              | Create a new game for the authenticated user |
| POST   | `/games/from-image`   | Create a new game from a scanned image grid  |
| GET    | `/games/current`      | Get the current in-progress game             |
| GET    | `/games/history`      | Get the player's completed game history      |
| GET    | `/games/{gameId}`     | Load a saved game                            |
| PATCH  | `/games/{gameId}`     | Save game progress                           |
| GET    | `/players/me`         | Get or create the current user's profile     |
| PATCH  | `/players/me`         | Update the current user's profile            |
| GET    | `/leaderboard`        | Get the league leaderboard                   |

---

## Testing

### Backend — Unit Tests

```bash
cd backend
./mvnw test
```

### Backend — Integration Tests

```bash
cd backend
./mvnw verify -DskipITs=false
```

### Frontend — E2E Tests (Playwright)

```bash
cd ui
npm run test:e2e       # headless Chromium
npm run test:e2e:ui    # interactive Playwright UI
```

CI runs all three test suites on every push. Playwright reports are uploaded as artifacts on failure.

---

## Key Files

| File                                               | Purpose                     |
|----------------------------------------------------|-----------------------------|
| [`docs/openapi.yaml`](docs/openapi.yaml)                             | API contract (source of truth)           |
| [`docs/high-level-design.md`](docs/high-level-design.md)             | System overview and component map        |
| [`docs/standards/java-quarkus.md`](docs/standards/java-quarkus.md)   | Backend coding standards                 |
| [`docs/llds/react-frontend.md`](docs/llds/react-frontend.md)         | Frontend LLD + coding standards          |
| [`docs/arrows/security-standards.md`](docs/arrows/security-standards.md) | Security standards (IAM, throttling, IDOR) |
| [`docs/arrows/testing-strategy.md`](docs/arrows/testing-strategy.md) | Test strategy                            |
| [`docs/llds/user-management.md`](docs/llds/user-management.md)       | Auth standards (Cognito, JWT, OIDC)      |
| [`docs/llds/cloud-platform.md`](docs/llds/cloud-platform.md)         | IaC standards + CI/CD pipeline           |
| [`CLAUDE.md`](CLAUDE.md)                                             | AI assistant instructions                |

---

## Infrastructure

Terraform configuration lives in `infra/`. Target region: `eu-west-2` (London).

See [`infra/README.md`](infra/README.md) for the full infrastructure reference including architecture diagram, resource details, bootstrap instructions, and local operations.

---

## Deployment

Deployment is automated via GitHub Actions on push to `main` or `rc-*` branches:

1. Maven builds `backend/target/function.zip`
2. Terraform authenticates via OIDC and runs `init → plan → apply`
3. A post-apply step tightens API Gateway CORS to the exact Amplify URL
4. A post-apply step pins Cognito callback URLs to the exact Amplify URL
5. Amplify build is triggered and waited on
6. Workflow summary prints the API Gateway and Amplify URLs

A manual **Teardown** workflow is available via GitHub Actions (`workflow_dispatch`) for full `terraform destroy`.

See [`infra/README.md`](infra/README.md) for bootstrap steps required before the first deploy.

---

## Contributing

This is a personal project. The linked-intent development workflow in [`CLAUDE.md`](CLAUDE.md) describes how code changes are made and how specs/LLDs are kept in sync.

### Inner-loop tooling

One-time setup (requires [Homebrew](https://brew.sh)):

```bash
make setup   # installs biome, ruff, trivy, checkov, terraform, pre-commit and wires up git hooks
```

After that, pre-commit hooks run automatically on every `git commit`, covering:
trailing whitespace · YAML check · Terraform fmt/validate · Ruff (Python) · Biome (JS/JSX) · Trivy security scan

On-demand checks:

```bash
make lint    # biome check + ruff + terraform fmt
make secure  # trivy fs + checkov
```

---

## TODO

See [`docs/todo/`](docs/todo/) for the backlog of deferred and upcoming work items.
