# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 8-Rule Architecture

These rules apply to every task in this project unless explicitly overridden.
Bias: caution over speed on non-trivial work. Use judgment on trivial tasks.

### Rule 1 — Think Before Coding

State assumptions explicitly. If uncertain, ask rather than guess.
Push back when a simpler approach exists. Stop when confused.

### Rule 2 — Simplicity First

Minimum code that solves the problem. Nothing speculative.
No features beyond what was asked. No abstractions for single-use code.

### Rule 3 — Surgical Changes

Touch only what you must. Clean up only your own mess.
Don't "improve" adjacent code, comments, or formatting. Match existing style.

### Rule 4 — Goal-Driven Execution

Define success criteria. Loop until verified.
Don't follow steps. Define success and iterate independently.

### Rule 5 — Token budgets are not advisory

Per-task: 4,000 tokens. Per-session: 30,000 tokens.
If approaching budget, summarize and start fresh. Surface the breach.

### Rule 6 — Read before you write

Before adding code, read exports, immediate callers, shared utilities.
If unsure why code is structured a certain way, ask.

### Rule 7 — Checkpoint after every significant step

Summarize what was done, what's verified, what's left.
Don't continue from a state you can't describe back. Stop and restate.

### Rule 8 — Fail loud

"Completed" is wrong if anything was skipped silently.
"Tests pass" is wrong if any were skipped.
Default to surfacing uncertainty, not hiding it.

## Project Overview

A serverless Sudoku app with a Java/Quarkus backend deployed to AWS Lambda, React + Vite frontend hosted on AWS Amplify, and DynamoDB as the database. Target AWS region: **eu-west-2** (London).

## Repository Structure

- `backend/` — Java 21 + Quarkus REST API (Lambda-optimized)
- `ui/` — React 19 + Vite frontend with MUI (Material UI)
- `infra/` — Terraform IaC (AWS provider configured, resources TBD)
- `image_recognition` - Python based AWS lambda

## Backend (Java/Quarkus)

```bash
cd backend

# Run in dev mode (hot reload)
./mvnw quarkus:dev

# Run unit tests
./mvnw test

# Run integration tests (skipped by default)
./mvnw verify -DskipITs=false

# Build native image (requires GraalVM)
./mvnw package -Pnative

# Build JVM jar
./mvnw package
```

Key dependencies: `quarkus-amazon-lambda-rest`, `quarkus-rest-jackson`, `quarkus-arc`.

## Frontend (React/Vite)

```bash
cd ui

# Install dependencies
npm install

# Start dev server
npm run dev

# Build for production
npm run build

# Lint
npm run lint

# Preview production build
npm run preview
```

No test framework configured yet (Vitest would be the natural choice given Vite).

## Infrastructure (Terraform)

```bash
cd infra
terraform init
terraform plan
terraform apply
```

Provider configured for AWS `eu-west-2`.

## Pre-Push Testing (MANDATORY)

**Run `scripts/local/local-alltests.sh` before every `git push`.** CI is not a substitute for local testing — a broken push blocks the branch and wastes CI minutes.

```bash
# Full suite (requires Docker + AWS sandbox credentials):
bash scripts/local/local-alltests.sh

# Fast path for UI-only changes:
bash scripts/local/local-alltests.sh --skip-backend --skip-integration --skip-image-recognition

# Skip infra if no .tf files changed:
bash scripts/local/local-alltests.sh --skip-infra
```

The script runs: image recognition (pytest), frontend lint, npm audit, E2E (Playwright), backend (Maven + DynamoDB Local), integration (Docker Compose + Playwright), and Terraform fmt/validate. All must pass (or be explicitly skipped with justification) before pushing.

**Never rely on hiding a failure with `--skip-*` unless that suite is genuinely inapplicable to the change.**

## Security & Cost Protection Standards

**`docs/security.md` must be followed for all generated code.**

## Infrastructure Coding Standards

**`docs/infrastructure.md` must be followed for all generated infrastructure code.**

## Backend Coding Standards

**`docs/standards/java-quarkus.md` must be followed for all generated backend code.**

## Frontend Coding Standards

**`docs/llds/react-frontend.md` (Implementation Standards section) must be followed for all generated frontend code.**

## API Usage Standards

**`docs/llds/react-frontend.md` (Three-Grid State Model section) and `docs/specs/` must be followed for all frontend↔API integration code.**

## Testing Standards

**`docs/arrows/testing-strategy.md` must be followed for all test code.**

## User Authentication Standards

**`docs/llds/user-management.md` must be followed for all authentication and identity code.**

## Architecture Notes

- Backend uses `quarkus-amazon-lambda-rest` to expose REST endpoints directly via API Gateway → Lambda (no server process)
- Native compilation with GraalVM is supported via the `native` Maven profile for cold-start optimization
- Integration tests (`*IT.java`) are skipped by default; enable with `-DskipITs=false`
- Frontend uses plain JSX (no TypeScript); ESLint uses flat config format (ESLint 9+)
- MUI v7 with Emotion as the styling engine

## Linked-Intent Development (MANDATORY)

**Consult the `linked-intent-dev` skill for ALL code changes.** All changes start with intent:

```
HLD → LLDs → EARS → Tests → Code
```

- **New features**: Full workflow (HLD → LLD → EARS → Plan)
- **Bug fixes**: Coherence check only (verify existing specs/tests/code align)
- **If unsure**: Use the full workflow

Mutation, not accumulation — docs reflect current intent, not history.

### Navigation

| What you need | Where to look |
|---|---|
| High-level design | `docs/high-level-design.md` |
| Low-level designs | `docs/llds/` |
| EARS specs | `docs/specs/` |
| Implementation plans | `docs/planning/` |
| Arrow of intent tracking | `docs/arrows/index.yaml` |

### Terminology

- **LLD**: Low-level design — detailed component design docs in `docs/llds/`
- **EARS**: Easy Approach to Requirements Syntax — structured requirements in `docs/specs/`. Markers: `[x]` implemented, `[ ]` active gap, `[D]` deferred
- **Arrow**: A traced dependency from HLD through code, tracked in `docs/arrows/`

### Code Annotations

Annotate code with `@spec` comments linking to EARS IDs:

```
// @spec AUTH-UI-001, AUTH-UI-002
```

Test files also reference specs for traceability.
