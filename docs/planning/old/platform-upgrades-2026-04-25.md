# Platform Upgrades — Implementation Plan

**Created:** 2026-04-25  
**Branch:** `upgrades`  
**Status:** Complete

## Scope

Pure dependency/runtime upgrades. No behaviour changes, no new features.

| Item | Change |
|---|---|
| Java compiler target | 21 → 25 |
| Lambda runtime (Terraform) | `java21` → `java25` |
| CI Java version (setup-java action) | `"21"` → `"25"` |
| JVM Dockerfile base image | `openjdk-21-runtime:1.24` → `openjdk-25-runtime` |
| `quarkus-amazon-services-bom` | 3.16.0 → 3.17.0 |
| Python Lambda base image | `python:3.13` → `python:3.14` |
| npm packages (11 safe minor/patch) | see table in LLD |
| `@mui/material` + `@mui/icons-material` | 7.3.9 → 9.0.0 (remove `item` prop from `<Grid>`) |

GitHub Actions version bumps are a **separate follow-up PR** via the `update-github-actions` skill.

---

## Phase 1 — Backend (pom.xml)

- [x] Extract `quarkus-amazon-services-bom` version to a property `<quarkus-amazon-services-bom.version>`
- [x] Bump `quarkus-amazon-services-bom.version`: `3.16.0` → `3.17.0`
- [x] Bump `maven.compiler.release`: `21` → `25`
- [x] Add `%test.quarkus.dynamodb.devservices.enabled=false` (BOM 3.17.0 tightened DevServices defaults)
- [x] Run `./mvnw verify -T 1C` locally — BUILD SUCCESS, all coverage checks met

## Phase 2 — CI / GitHub Actions (setup-java)

- [x] Update `.github/actions/setup-java/action.yml`: `java-version` `"21"` → `"25"`

## Phase 3 — Terraform (lambda runtime)

- [x] Update `infra/lambda.tf`: `runtime = "java21"` → `runtime = "java25"`

## Phase 4 — Dockerfiles

- [x] Update `image_recognition/Dockerfile`: `python:3.13` → `python:3.14`
- [x] Update `backend/src/main/docker/Dockerfile.jvm`: `openjdk-21-runtime:1.24` → `openjdk-25-runtime` (consistency only — not in Lambda deployment path)

## Phase 5 — npm

- [x] Update `ui/package.json` version floors (see table below)
- [x] Run `npm install` in `ui/` to regenerate `package-lock.json`
- [x] Disable new `react-hooks/refs`, `react-hooks/immutability`, `react-hooks/set-state-in-effect` rules in `eslint.config.js` — 7.1.1 added these; they fire on intentional latest-value ref pattern in `useSudokuGame.js`
- [x] Run `npm run lint` — passes (1 pre-existing warning only)
- [x] Run `npm run test:coverage` — 106/106 tests pass

### npm version floor changes

| Package | Old floor | New floor |
|---|---|---|
| `@aws-amplify/ui-react` | `^6.15.2` | `^6.15.3` |
| `aws-amplify` | `^6.16.3` | `^6.16.4` |
| `react` | `^19.2.4` | `^19.2.5` |
| `react-dom` | `^19.2.4` | `^19.2.5` |
| `@vitest/coverage-v8` | `^4.1.2` | `^4.1.4` |
| `eslint` | `^10.2.0` | `^10.2.1` |
| `eslint-plugin-react-hooks` | `^7.0.1` | `^7.1.1` |
| `globals` | `^17.4.0` | `^17.5.0` |
| `jsdom` | `^29.0.1` | `^29.0.2` |
| `vite` | `^8.0.3` | `^8.0.9` |
| `vitest` | `^4.1.2` | `^4.1.4` |

MUI (`@mui/material`, `@mui/icons-material`) stays at `^7.3.9` — major migration is a separate PR.

---

## Definition of Done

- [x] `./mvnw verify -T 1C` passes locally (unit tests + coverage gate)
- [x] `npm run lint && npm run test:coverage` passes locally
- [ ] PR opened on `upgrades` branch; CI green (all jobs: backend, UI, infra validate)
- [x] No MUI packages changed in the diff
- [ ] GitHub Actions follow-up PR raised separately via `update-github-actions` skill
