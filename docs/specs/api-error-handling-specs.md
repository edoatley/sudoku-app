# API Error Handling — EARS Specifications

## Error Response Structure

- [x] **AEH-DTO-001**: The system shall represent all API error responses as a JSON object with fields `code` (string), `message` (string), and `detail` (string or null).
- [x] **AEH-DTO-002**: The system shall provide a two-argument constructor for ErrorResponse that sets `detail` to null.
- [x] **AEH-DTO-003**: When `detail()` is called on an ErrorResponse with a null detail field, the system shall return an empty string rather than null.
- [x] **AEH-DTO-004**: The system shall define error code constants `INVALID_GRID`, `INVALID_PUZZLE`, `GAME_NOT_FOUND`, and `INTERNAL_ERROR` as static fields on ErrorResponse.

## Exception Hierarchy — Import Validation

- [x] **AEH-EX-001**: The system shall define `InvalidPuzzleException` as an abstract base class in `com.sudoku.game` for all puzzle validity failures.
- [x] **AEH-EX-002**: The system shall define `DuplicateDigitsException` extending `InvalidPuzzleException`, thrown when an imported grid contains duplicate digits in any row, column, or block.
- [x] **AEH-EX-003**: The system shall define `PuzzleHasNoSolutionException` extending `InvalidPuzzleException`, thrown when an imported grid has no valid solution.
- [x] **AEH-EX-004**: The system shall define `PuzzleHasMultipleSolutionsException` extending `InvalidPuzzleException`, thrown when an imported grid has more than one valid solution.
- [x] **AEH-EX-005**: Each of `DuplicateDigitsException`, `PuzzleHasNoSolutionException`, and `PuzzleHasMultipleSolutionsException` shall carry a fixed, user-readable message set in the no-arg constructor without requiring the caller to supply a message string.

## Exception Hierarchy — Game Lookup

- [x] **AEH-EX-006**: The system shall define `GameNotFoundException` in `com.sudoku.game`, thrown when a game lookup by (userId, gameId) finds no record.
- [x] **AEH-EX-007**: `GameNotFoundException` shall accept a `gameId` argument and include it in the exception message.
- [ ] **AEH-EX-008**: When `DynamoDbGameRepository.findById()` finds no matching record, the system shall throw `GameNotFoundException` rather than returning an empty Optional.
- [ ] **AEH-EX-009**: When `DynamoDbGameRepository.update()` targets a game that does not exist, the system shall throw `GameNotFoundException` rather than silently no-oping.

## Exception Mappers

- [x] **AEH-MAP-001**: All JAX-RS exception mapper classes shall reside in the package `com.sudoku.exception`.
- [x] **AEH-MAP-002**: When `InvalidGridException` is thrown, the system shall return HTTP 400 with an `ErrorResponse` with code `INVALID_GRID` and the exception message as `message`.
- [x] **AEH-MAP-003**: When any `InvalidPuzzleException` (including subclasses) is thrown, the system shall return HTTP 422 with an `ErrorResponse` with code `INVALID_PUZZLE` and the exception message as `message`.
- [x] **AEH-MAP-004**: When `GameNotFoundException` is thrown, the system shall return HTTP 404 with an `ErrorResponse` with code `GAME_NOT_FOUND` and the exception message as `message`.
- [x] **AEH-MAP-005**: When any unhandled `Exception` reaches the API boundary, the system shall return HTTP 500 with an `ErrorResponse` with code `INTERNAL_ERROR`, message `"An unexpected error occurred"`, and a UUID correlation ID in the `detail` field.
- [x] **AEH-MAP-006**: When the `GlobalExceptionMapper` handles an exception, the system shall log the full exception at ERROR level including the correlation ID before returning the response.
- [x] **AEH-MAP-007**: The `GlobalExceptionMapper` shall never include the original exception message or stack trace in the HTTP response body.

## Wire Format

- [x] **AEH-WIRE-001**: All error responses shall set Content-Type to `application/json`.
- [x] **AEH-WIRE-002**: The `detail` field shall be serialized as `null` in JSON when not set (i.e. not omitted from the response body).
