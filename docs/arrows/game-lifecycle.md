# Arrow: Game Lifecycle

Game state machine, single-active-game invariant, DynamoDB persistence, and import validation.

## Status

**MAPPED** - 2026-04-18. All source files read and documented. No tests audited yet.

## References

### HLD
- docs/high-level-design.md — "Data Flow: New Game", "Data Flow: Import from Photo", "Single-Active-Game Invariant" sections

### LLD
- docs/llds/game-lifecycle.md

### EARS
- docs/specs/game-lifecycle-specs.md (20 specs, all [x])

### Tests
- backend/src/test/java/.../game/ (not yet audited)

### Code
- backend/src/main/java/.../game/GameResource.java
- backend/src/main/java/.../game/GameService.java
- backend/src/main/java/.../game/GameServiceImpl.java
- backend/src/main/java/.../game/GameRepository.java
- backend/src/main/java/.../game/DynamoDbGameRepository.java
- backend/src/main/java/.../game/GameItem.java
- backend/src/main/java/.../game/GameStatus.java
- backend/src/main/java/.../game/InvalidPuzzleException.java
- backend/src/main/java/.../game/InvalidPuzzleExceptionMapper.java
- backend/src/main/java/.../dto/GameState.java
- backend/src/main/java/.../dto/GameUpdateRequest.java
- backend/src/main/java/.../dto/CreateGameFromGridRequest.java

## Architecture

**Purpose:** Manage the full lifecycle of a game from creation through completion or abandonment, enforcing the single-active-game invariant and persisting all state to DynamoDB.

**Key Components:**
1. `GameResource` — five JWT-protected REST endpoints; extracts userId from JWT only
2. `GameServiceImpl` — enforces business rules (single-active-game, import validation order)
3. `DynamoDbGameRepository` — fetch-mutate-save pattern for DynamoDB operations
4. `GameItem` — mutable DynamoDB entity with JSON-serialised grid fields
5. `GameStatus` — IN_PROGRESS / SOLVED / ABANDONED state enum

## EARS Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
| --- | --- | --- | --- | --- |
| Game Creation | GL-BE-001 to 006, GL-API-001 to 002 | 8 | 0 | 0 |
| Retrieval & Resumption | GL-API-003 to 004 | 2 | 0 | 0 |
| Game Progress | GL-API-005, GL-BE-010 to 012 | 4 | 0 | 0 |
| State Machine | GL-BE-020 to 022 | 3 | 0 | 0 |
| Security | GL-BE-030 | 1 | 0 | 0 |
| DynamoDB Persistence | GL-DATA-001 to 004 | 4 | 0 | 0 |

**Summary:** 22 of 22 active specs implemented; 0 deferred; 0 gaps.

## Key Findings

1. **`GameStatus.IMPORTED` is a dead enum value** — Used as a `difficulty` string ("imported"), never written to the `status` attribute. Imported games have status IN_PROGRESS. The enum value is misleading. (GL-BE-020)
2. **Silent no-op on missing game** — `DynamoDbGameRepository.update()` returns silently if the game does not exist. The caller receives no error signal. (GL-API-005)
3. **`findInProgress` client-side filter** — Queries all games for a user then filters in memory. Acceptable with the single-active invariant (≤1 IN_PROGRESS per user); a GSI would fix it at scale.
4. **Validation before abandonment in import flow** — Deliberate design: avoids wasting a DynamoDB write on grids that will be rejected. (GL-BE-003)

## Work Required

### Should Fix
1. Remove or repurpose `GameStatus.IMPORTED` — it is never used as a status and creates a false impression that imported games have a distinct status. (GL-BE-020)

### Nice to Have
2. Return a typed error or log a warning when `update()` is called for a non-existent game, rather than silently no-oping. (GL-API-005)
