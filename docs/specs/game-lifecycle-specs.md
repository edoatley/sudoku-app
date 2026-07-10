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

- [x] **GL-API-005**: The system shall expose PATCH /api/v1/games/{gameId} requiring JWT authentication, accepting currentGrid, candidates, timeSpentSeconds, optional isComplete, optional hintsUsed, and an optional events array of buffered puzzle-play actions.
- [x] **GL-BE-010**: When PATCH is received with isComplete=true, the system shall set game status to SOLVED and record endedAt as the current UTC timestamp.
- [x] **GL-BE-011**: When PATCH is received with isComplete=false or isComplete absent, the system shall keep game status as IN_PROGRESS.
- [x] **GL-BE-012**: The system shall overwrite currentGrid, candidates, and timeSpentSeconds with the client-submitted values on every PATCH (full overwrite, not diff).

## Puzzle-Play Event Logging

- [x] **GL-BE-040**: When a PATCH /api/v1/games/{gameId} request includes an events array, the system shall emit one structured JSON log line per processed event to the standard Lambda logger, each carrying its type, pid (the gameId), userId (from the JWT principal), and a server-generated ts.
- [x] **GL-BE-041**: For each NUMBER event, the system shall log a NUMBER line with the cell coordinates and placed digit, followed by a NUMBER_RESULT line whose correct field is true if and only if the placed digit equals the stored solutionGrid value for that cell.
- [x] **GL-BE-042**: For each NUMBER_CLEAR, HINT_REQUEST, and HINT_RESPONSE event, the system shall log a line of the corresponding type, carrying through the client-supplied fields for that type — the cell coordinates for NUMBER_CLEAR, and the cid plus (for HINT_RESPONSE) the technique name and strategy rank for hint events — so a HINT_REQUEST and its HINT_RESPONSE can be joined.
- [x] **GL-BE-043**: When an event has an unrecognized type, is missing fields required for its type, or carries out-of-range coordinates or digit values, the system shall log a warning and skip that event without throwing or failing the PATCH.
- [x] **GL-BE-044**: The system shall process at most 500 events per PATCH request, and when the client marks the batch as truncated the system shall emit an EVENTS_TRUNCATED marker line.
- [x] **GL-BE-045**: When a PATCH request has a null or absent events array, the system shall persist game progress normally and emit no puzzle-play event log lines.
- [x] **GL-BE-046**: The system shall serialize every puzzle-play event log line as JSON via a JSON library rather than string templating.
- [x] **GL-BE-047**: For each UNDO event, the system shall log an UNDO line carrying the cell coordinates, the digit removed (v), the digit or empty value restored (prevV), and the undoneType of the reversed action, applying the same coordinate/digit validation as NUMBER.

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
