# Arrow: API Error Handling

Cross-cutting layer: uniform `ErrorResponse` envelope, specific domain exceptions, and JAX-RS exception mappers.

## Status

**OK** - 2026-04-19. All 20 active specs implemented. AEH-EX-008/009 intentionally deferred.

## References

### HLD
- docs/high-level-design.md — all REST boundary components

### LLD
- docs/llds/api-error-handling.md

### EARS
- docs/specs/api-error-handling-specs.md (22 specs: 20 [x], 2 [ ])
- docs/specs/domain-types-specs.md — DT-DTO-* and DT-SVC-* specs also touch error surface
- docs/specs/game-lifecycle-specs.md — GL-BE-004/005/006, GL-API-004 all [x]

### Tests
- backend/src/test/java/com/sudoku/game/GameResourceTest.java — updated for new error body shape
- backend/src/test/java/com/sudoku/game/GameServiceImplTest.java — updated for specific exception types

### Code
- backend/src/main/java/.../web/ErrorResponse.java
- backend/src/main/java/.../game/InvalidPuzzleException.java (abstract)
- backend/src/main/java/.../game/DuplicateDigitsException.java
- backend/src/main/java/.../game/PuzzleHasNoSolutionException.java
- backend/src/main/java/.../game/PuzzleHasMultipleSolutionsException.java
- backend/src/main/java/.../game/GameNotFoundException.java
- backend/src/main/java/.../web/exception/InvalidGridExceptionMapper.java
- backend/src/main/java/.../web/exception/InvalidPuzzleExceptionMapper.java
- backend/src/main/java/.../web/exception/GameNotFoundExceptionMapper.java
- backend/src/main/java/.../web/exception/GlobalExceptionMapper.java

## Architecture

**Purpose:** Ensure every error that reaches the REST boundary produces a `{"code":"...","message":"...","detail":"..."}` JSON body with a consistent HTTP status. Specific domain exceptions replace generic string-message exceptions so callers can programmatically distinguish failure modes.

**Key Components:**
1. `ErrorResponse` — shared DTO record; two constructors; null-safe `detail()` accessor; error code constants
2. Exception hierarchy in `com.sudoku.game` — `InvalidPuzzleException` (abstract base), three specific subclasses, `GameNotFoundException`
3. Exception mappers in `com.sudoku.web.exception` — four `@Provider` classes; specificity rules ensure correct mapper fires
4. `GlobalExceptionMapper` — catch-all with UUID correlation ID; logs at ERROR; never leaks internals

## EARS Coverage

| Category | Spec IDs | Implemented | Deferred | Gaps |
| --- | --- | --- | --- | --- |
| ErrorResponse DTO | AEH-DTO-001 to 004 | 4 | 0 | 0 |
| Import exception hierarchy | AEH-EX-001 to 005 | 5 | 0 | 0 |
| GameNotFoundException | AEH-EX-006 to 009 | 2 | 2 | 0 |
| Exception mappers | AEH-MAP-001 to 007 | 7 | 0 | 0 |
| Wire format | AEH-WIRE-001 to 002 | 2 | 0 | 0 |

**Summary:** 20 of 22 active specs implemented; 2 deferred (AEH-EX-008/009); 0 gaps.

## Key Findings

1. **AEH-EX-008/009 deferred** — `DynamoDbGameRepository.findById()` and `update()` do not yet throw `GameNotFoundException` directly. Deferred until the real DynamoDB repository is deployed and testable end-to-end. `GameServiceImpl.loadGame()` throws it instead (AEH-EX-007).
2. **Specificity rule** — JAX-RS resolves `ExceptionMapper` by most specific type. `InvalidPuzzleExceptionMapper` catches all three subclasses; `GlobalExceptionMapper` fires only for unmatched exceptions.
3. **Correlation ID** — `GlobalExceptionMapper` generates a `UUID.randomUUID()` per 500 error and sets it in `ErrorResponse.detail`. The same ID is logged at ERROR level for correlation.

## Work Required

None. AEH-EX-008/009 are intentionally deferred — re-evaluate when real DynamoDB repository is deployed.
