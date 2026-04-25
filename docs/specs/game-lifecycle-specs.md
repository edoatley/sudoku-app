# Game Lifecycle — EARS Specifications

## Game Creation

- [x] **GL-BE-001**: When a player creates a new game, the system shall abandon any existing IN_PROGRESS game for that player before persisting the new one.
- [x] **GL-BE-002**: When creating a new game by difficulty, the system shall generate a puzzle and persist a GameState with status IN_PROGRESS, a new UUID gameId, and startedAt set to the current UTC timestamp.
- [x] **GL-BE-003**: When creating a game from an imported grid, the system shall validate for duplicate digits, solvability, and uniqueness (in that order) before abandoning the prior game, throwing a specific InvalidPuzzleException subclass on any failure.
- [x] **GL-BE-004**: When an imported grid contains duplicate digits, the system shall throw `DuplicateDigitsException`.
- [x] **GL-BE-005**: When an imported grid has no valid solution, the system shall throw `PuzzleHasNoSolutionException`.
- [x] **GL-BE-006**: When an imported grid has multiple solutions, the system shall throw `PuzzleHasMultipleSolutionsException`.
- [x] **GL-API-001**: The system shall expose POST /api/v1/games requiring JWT authentication and returning HTTP 201 with the created GameState.
- [x] **GL-API-002**: The system shall expose POST /api/v1/games/from-image requiring JWT authentication and returning HTTP 201 on success or HTTP 422 with a JSON error body on invalid puzzle.

## Game Retrieval & Resumption

- [x] **GL-API-003**: The system shall expose GET /api/v1/games/current requiring JWT authentication and returning the player's IN_PROGRESS GameState, or HTTP 204 if none exists.
- [x] **GL-API-004**: The system shall expose GET /api/v1/games/{gameId} requiring JWT authentication; when the game does not exist the system shall throw `GameNotFoundException` (mapped to HTTP 404 by `GameNotFoundExceptionMapper`).

## Game Progress

- [x] **GL-API-005**: The system shall expose PATCH /api/v1/games/{gameId} requiring JWT authentication, accepting currentGrid, candidates, timeSpentSeconds, optional isComplete, and optional hintsUsed.
- [x] **GL-BE-010**: When PATCH is received with isComplete=true, the system shall set game status to SOLVED and record endedAt as the current UTC timestamp.
- [x] **GL-BE-011**: When PATCH is received with isComplete=false or isComplete absent, the system shall keep game status as IN_PROGRESS.
- [x] **GL-BE-012**: The system shall overwrite currentGrid, candidates, and timeSpentSeconds with the client-submitted values on every PATCH (full overwrite, not diff).

## State Machine

- [x] **GL-BE-020**: The system shall support game statuses: IN_PROGRESS, SOLVED, ABANDONED.
- [x] **GL-BE-021**: The system shall transition a game from IN_PROGRESS to ABANDONED only server-side, when a new game is created for the same player.
- [x] **GL-BE-022**: When abandoning a game, the system shall record endedAt as the current UTC timestamp.

## Security

- [x] **GL-BE-030**: The system shall always extract the userId from the JWT principal and never trust a client-supplied userId for any game operation.

## DynamoDB Persistence

- [x] **GL-DATA-001**: The system shall store games in DynamoDB with userId as partition key and gameId as sort key.
- [x] **GL-DATA-002**: The system shall serialize all grid fields (originalGrid, solutionGrid, currentGrid, candidates) as JSON strings in DynamoDB.
- [x] **GL-DATA-003**: The system shall store all timestamps as ISO-8601 UTC strings.
- [x] **GL-DATA-004**: While a game is in progress, the system shall store endedAt as null.
