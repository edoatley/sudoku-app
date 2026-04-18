# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

## Security & Cost Protection Standards

**`docs/security.md` must be followed for all generated code.**

## Infrastructure Coding Standards

**`docs/infrastructure.md` must be followed for all generated infrastructure code.**

## Backend Coding Standards

**`docs/backend.md` must be followed for all generated backend code.**

## Frontend Coding Standards

**`docs/frontend.md` must be followed for all generated frontend code.**

## API Usage Standards

**`docs/api-use.md` must be followed for all frontend↔API integration code.**

## Testing Standards

**`docs/test-strategy.md` must be followed for all test code.**

## User Authentication Standards

**`docs/user-authentication.md` must be followed for all authentication and identity code.**

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