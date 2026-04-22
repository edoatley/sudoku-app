# API Error Handling

**Created**: 2026-04-19
**Status**: Complete

## Context and Current State

The API Error Handling layer is a cross-cutting concern that ensures every error reaching the REST boundary produces a consistent `{"code":"...","message":"...","detail":"..."}` JSON body with a predictable HTTP status code. Specific domain exceptions replace previously generic string-message exceptions so callers can programmatically distinguish failure modes.

Files: `dto/ErrorResponse.java`, `game/InvalidPuzzleException.java`, `game/DuplicateDigitsException.java`, `game/PuzzleHasNoSolutionException.java`, `game/PuzzleHasMultipleSolutionsException.java`, `game/GameNotFoundException.java`, `exception/InvalidGridExceptionMapper.java`, `exception/InvalidPuzzleExceptionMapper.java`, `exception/GameNotFoundExceptionMapper.java`, `exception/GlobalExceptionMapper.java`.

## ErrorResponse DTO

`ErrorResponse` is the single wire format for all API errors. It is a Java record with three fields:

| Field | Type | Meaning |
| --- | --- | --- |
| `code` | `String` | Machine-readable error category (e.g. `GAME_NOT_FOUND`) |
| `message` | `String` | Human-readable explanation |
| `detail` | `String` | Optional extra context (correlation ID for 500s, null otherwise) |

Two constructors:
- `ErrorResponse(code, message, detail)` — full
- `ErrorResponse(code, message)` — sets `detail` to null

`detail()` returns empty string when null (never propagates null to JSON consumers).

Static constants for all known codes: `INVALID_GRID`, `INVALID_PUZZLE`, `GAME_NOT_FOUND`, `INTERNAL_ERROR`.

Wire format: `detail` serializes as `null` in JSON when not set (field present, value null — not omitted).

## Exception Hierarchy

All domain exceptions live in `com.sudoku.game`. The hierarchy is:

```text
RuntimeException
└── InvalidGridException           (com.sudoku.domain)  — bad grid shape/values at Board.fromGrid()
└── InvalidPuzzleException (abstract)                   — any import validation failure
    ├── DuplicateDigitsException                        — duplicate in row/col/block
    ├── PuzzleHasNoSolutionException                    — no valid solution exists
    └── PuzzleHasMultipleSolutionsException             — more than one solution
└── GameNotFoundException                               — (userId, gameId) not found
```

Each concrete subclass carries a fixed, user-readable message set in the no-arg constructor. Callers do not supply a message string — the exception type is the message.

| Exception | HTTP Status | Fixed Message |
| --- | --- | --- |
| `InvalidGridException` | 400 | `"Invalid grid: ..."` (message from Board.fromGrid validation) |
| `DuplicateDigitsException` | 422 | `"Puzzle contains duplicate digits — check rows, columns, and boxes for conflicts"` |
| `PuzzleHasNoSolutionException` | 422 | `"Puzzle has no valid solution — it may have been scanned incorrectly"` |
| `PuzzleHasMultipleSolutionsException` | 422 | `"Puzzle has multiple solutions — a valid sudoku must have exactly one solution"` |
| `GameNotFoundException` | 404 | `"Game not found: {gameId}"` |

## Exception Mappers

All mappers reside in `com.sudoku.exception` and implement `ExceptionMapper<T>`. All are annotated `@Provider` for automatic JAX-RS registration. All set `Content-Type: application/json`.

| Mapper | Catches | HTTP | Code |
| --- | --- | --- | --- |
| `InvalidGridExceptionMapper` | `InvalidGridException` | 400 | `INVALID_GRID` |
| `InvalidPuzzleExceptionMapper` | `InvalidPuzzleException` (all subclasses) | 422 | `INVALID_PUZZLE` |
| `GameNotFoundExceptionMapper` | `GameNotFoundException` | 404 | `GAME_NOT_FOUND` |
| `GlobalExceptionMapper` | `Exception` (catch-all) | 500 | `INTERNAL_ERROR` |

**Specificity:** JAX-RS resolves `ExceptionMapper` by the most specific matching type. `InvalidPuzzleExceptionMapper` catches all `InvalidPuzzleException` subclasses. `GlobalExceptionMapper` only fires for exceptions not matched by any other mapper.

**GlobalExceptionMapper behaviour:**
- Generates a UUID correlation ID
- Logs the full exception at ERROR level including the correlation ID
- Returns `ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred", correlationId)`
- Never includes the original exception message or stack trace in the HTTP response body

## Where Exceptions Are Thrown

| Throw site | Exception | Trigger |
| --- | --- | --- |
| `Board.fromGrid(Grid)` | `InvalidGridException` | Grid is not 9×9 or contains out-of-range values |
| `GameServiceImpl.createGameFromExistingGrid()` | `DuplicateDigitsException` | `validatePuzzle()` returns `isValid=false` |
| `GameServiceImpl.createGameFromExistingGrid()` | `PuzzleHasNoSolutionException` | `solveGrid()` returns empty Optional |
| `GameServiceImpl.createGameFromExistingGrid()` | `PuzzleHasMultipleSolutionsException` | `hasSingleSolution()` returns false |
| `GameServiceImpl.loadGame()` | `GameNotFoundException` | `gameRepository.findById()` returns empty Optional |

Note: `DynamoDbGameRepository.findById()` and `update()` do not yet throw `GameNotFoundException` directly (specs AEH-EX-008/009 are deferred to a future release when the real DynamoDB repository is deployed and testable end-to-end).

## Wire Format Examples

**404 — Game not found:**
```json
{
  "code": "GAME_NOT_FOUND",
  "message": "Game not found: 00000000-0000-0000-0000-000000000000",
  "detail": null
}
```

**422 — Import validation failure:**
```json
{
  "code": "INVALID_PUZZLE",
  "message": "Puzzle contains duplicate digits — check rows, columns, and boxes for conflicts",
  "detail": null
}
```

**500 — Unhandled exception:**
```json
{
  "code": "INTERNAL_ERROR",
  "message": "An unexpected error occurred",
  "detail": "a3f9b2c1-4d5e-6f7a-8b9c-0d1e2f3a4b5c"
}
```

## Observed Design Decisions

| Decision | Chosen | Alternatives Considered | Rationale |
| --- | --- | --- | --- |
| Specific exception subclasses | Three `InvalidPuzzleException` subclasses | Single exception with an enum discriminator | Subclasses are idiomatic Java; each type carries its own fixed message; callers can `catch` the specific type they care about |
| Fixed messages in constructors | No-arg constructors with literal message strings | Caller supplies message | Prevents message drift across call sites; single source of truth per failure mode |
| `GlobalExceptionMapper` as catch-all | Catches `Exception` base class | Catch `RuntimeException` only | Ensures checked exceptions and framework exceptions all produce consistent 500 bodies |
| UUID correlation ID in detail | `UUID.randomUUID()` per error | Sequential counter, request ID from header | UUID is globally unique without shared state; fits serverless (no shared counter); correlates with log entry |
| `detail` serializes as `null`, not omitted | Jackson default serialization | `@JsonInclude(NON_NULL)` to omit | Consistent response shape — callers always see three fields; no conditional null-check needed |

## Decisions & Alternatives

**Why not a single `AppException` with a code field?**
A single exception type with an enum discriminator would require callers to check the code at runtime. Specific subclasses allow `catch (DuplicateDigitsException e)` which is more idiomatic and statically checkable. The tradeoff is more classes, but each is trivial (no-arg constructor + fixed message).

**Why are mappers in `com.sudoku.exception`, not `com.sudoku.game`?**
The mappers are infrastructure — they translate domain exceptions to HTTP responses. Keeping them in a separate package prevents the game domain from depending on JAX-RS types, and allows a future extraction of shared infrastructure without touching domain code.

## References

- `backend/src/main/java/.../dto/ErrorResponse.java`
- `backend/src/main/java/.../domain/InvalidGridException.java`
- `backend/src/main/java/.../game/InvalidPuzzleException.java`
- `backend/src/main/java/.../game/DuplicateDigitsException.java`
- `backend/src/main/java/.../game/PuzzleHasNoSolutionException.java`
- `backend/src/main/java/.../game/PuzzleHasMultipleSolutionsException.java`
- `backend/src/main/java/.../game/GameNotFoundException.java`
- `backend/src/main/java/.../exception/InvalidGridExceptionMapper.java`
- `backend/src/main/java/.../exception/InvalidPuzzleExceptionMapper.java`
- `backend/src/main/java/.../exception/GameNotFoundExceptionMapper.java`
- `backend/src/main/java/.../exception/GlobalExceptionMapper.java`
- `backend/src/test/java/.../game/GameResourceTest.java`
- `backend/src/test/java/.../game/GameServiceImplTest.java`
- Depends on: nothing (cross-cutting layer, no domain dependencies)
- Depended on by: Game Lifecycle (throws GameNotFoundException, InvalidPuzzleException subclasses), Puzzle Generation (throws InvalidGridException via Board)
