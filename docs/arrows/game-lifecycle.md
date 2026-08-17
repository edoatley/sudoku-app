# Arrow: Game Lifecycle

Game state machine, single-active-game invariant, DynamoDB persistence, and import validation.

## Status

**OK** - 2026-04-19 (reconciled 2026-08). All active specs implemented, incl. AEH-EX-008/009 (DynamoDbGameRepository throws GameNotFoundException directly).

## References

### HLD
- docs/high-level-design.md — "Data Flow: New Game", "Data Flow: Import from Photo", "Single-Active-Game Invariant" sections

### LLD
- docs/llds/game-lifecycle.md

### EARS
- docs/specs/game-lifecycle-specs.md (all [x])
- docs/specs/domain-types-specs.md — DT-DTO-004/005/007, DT-SVC-003 all [x]
- docs/specs/api-error-handling-specs.md — AEH-EX-006..009 [x]

### Tests
- backend/src/test/java/com/sudoku/game/GameServiceImplTest.java — covers GL-BE-001 to 022, GL-BE-030, GL-DATA-001 to 004; @spec annotations added
- backend/src/test/java/com/sudoku/game/GameResourceTest.java — covers GL-API-001 to 005; @spec annotations added

### Code
- backend/src/main/java/.../game/web/GameResource.java
- backend/src/main/java/.../game/GameService.java
- backend/src/main/java/.../game/GameServiceImpl.java
- backend/src/main/java/.../game/GameRepository.java
- backend/src/main/java/.../game/persistence/DynamoDbGameRepository.java
- backend/src/main/java/.../game/persistence/GameItem.java
- backend/src/main/java/.../game/GameStatus.java
- backend/src/main/java/.../game/InvalidPuzzleException.java
- backend/src/main/java/.../game/DuplicateDigitsException.java
- backend/src/main/java/.../game/PuzzleHasNoSolutionException.java
- backend/src/main/java/.../game/PuzzleHasMultipleSolutionsException.java
- backend/src/main/java/.../game/GameNotFoundException.java
- backend/src/main/java/.../web/exception/GameNotFoundExceptionMapper.java
- backend/src/main/java/.../game/web/GameState.java
- backend/src/main/java/.../game/web/GameUpdateRequest.java
- backend/src/main/java/.../game/web/CreateGameFromGridRequest.java
- backend/src/main/java/.../game/web/GameHistoryEntry.java
- backend/src/main/java/.../game/web/GameHistoryResponse.java
- backend/src/main/java/.../domain/Grid.java
- backend/src/main/java/.../domain/CandidatesGrid.java

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
| Domain Types (DTOs) | DT-DTO-004/005/007 | 3 | 0 | 0 |
| Domain Types (Service) | DT-SVC-003 | 1 | 0 | 0 |
| GameNotFoundException | AEH-EX-006/007 | 2 | 0 | 0 |
| Repository exceptions | AEH-EX-008/009 | 2 | 0 | 0 |

**Summary:** 30 of 30 active specs implemented; 0 gaps. (GL-GCP-006 remains [D] in the spec — Firestore single-active-game transaction.)

## Key Findings

1. **`GameStatus.IMPORTED` removed** — Was used only as a `difficulty` string, never as a `status` value. Replaced with the plain string `"imported"` in `GameServiceImpl`. `GameStatus` now has exactly three values matching its three lifecycle states. (GL-BE-020)
2. **Silent no-op on missing game** — `DynamoDbGameRepository.update()` returns silently if the game does not exist. The caller receives no error signal. (GL-API-005)
3. **`findInProgress` client-side filter** — Queries all games for a user then filters in memory. Acceptable with the single-active invariant (≤1 IN_PROGRESS per user); a GSI would fix it at scale.
4. **Validation before abandonment in import flow** — Deliberate design: avoids wasting a DynamoDB write on grids that will be rejected. (GL-BE-003)

## Work Required

### Done
5. ~~`GameServiceImpl.loadGame()` throws JAX-RS `NotFoundException`~~ — Now throws `GameNotFoundException`; mapped by `GameNotFoundExceptionMapper` to 404. (GL-API-004, AEH-EX-006/007)
6. ~~`GameServiceImpl.createGameFromExistingGrid()` throws generic `InvalidPuzzleException`~~ — Now throws three specific subclasses (`DuplicateDigitsException`, `PuzzleHasNoSolutionException`, `PuzzleHasMultipleSolutionsException`). (GL-BE-004/005/006)
7. ~~`GameState`/`GameUpdateRequest`/`CreateGameFromGridRequest` raw list fields~~ — All updated to `Grid`/`CandidatesGrid`. (DT-DTO-004/005/007, DT-SVC-003)
8. ~~`GameState.solutionGrid` nullable~~ — Non-nullable; every persisted game has a solution. (DT-DTO-007)

### Deferred
- GL-GCP-006: enforce the single-active-game invariant in a Firestore transaction (atomic abandon+write). Deferred — orchestrated non-atomically by `GameServiceImpl`, matching AWS.
  (AEH-EX-008/009 are now implemented: `DynamoDbGameRepository.findById()`/`update()` throw `GameNotFoundException` directly.)

### Nice to Have
- Log a warning when `update()` is called for a non-existent game (precursor to AEH-EX-009). (GL-API-005)
