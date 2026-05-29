# Game History Specs

## Repository Layer

- [x] **GH-BE-001**: The system shall provide a `findHistory(userId, limit)` repository method that queries all games for the user and returns those with status SOLVED or ABANDONED, ordered by `endedAt` descending, capped at `limit` items.
- [x] **GH-BE-002**: When the `?limit` query parameter is absent, the system shall default to returning at most 20 history entries.
- [x] **GH-BE-003**: When the `?limit` query parameter exceeds 100, the system shall clamp the effective limit to 100.
- [x] **GH-BE-004**: The system shall exclude games with status IN_PROGRESS from history results.

## Service Layer

- [x] **GH-SVC-001**: The system shall provide a `getGameHistory(userId, limit)` service method that delegates to `GameRepository.findHistory` and wraps the result in a `GameHistoryResponse`.

## DTO Layer

- [x] **GH-DTO-001**: The system shall define a `GameHistoryEntry` record containing: `gameId` (String), `difficulty` (String), `outcome` (String), `endedAt` (String), `elapsedSeconds` (int), `hintsUsed` (int).
- [x] **GH-DTO-002**: The system shall map `GameStatus.SOLVED` to the outcome string `"won"` in `GameHistoryEntry`.
- [x] **GH-DTO-003**: The system shall map `GameStatus.ABANDONED` to the outcome string `"abandoned"` in `GameHistoryEntry`.
- [x] **GH-DTO-004**: The system shall define a `GameHistoryResponse` record containing `List<GameHistoryEntry> entries`.

## API Layer

- [x] **GH-API-001**: The system shall expose `GET /api/v1/games/history` requiring JWT authentication and returning HTTP 200 with a `GameHistoryResponse` body.
- [x] **GH-API-002**: The system shall accept an optional integer query parameter `?limit` on `GET /api/v1/games/history` with a default of 20 and a maximum of 100.
- [x] **GH-API-003**: The system shall extract the `userId` from the JWT principal on `GET /api/v1/games/history` and never trust a client-supplied user identifier.

## Frontend API

- [x] **GH-UI-001**: The system shall expose a `getGameHistory()` function in `sudokuApi.js` that calls `GET /api/v1/games/history` with Bearer authentication and returns the parsed `entries` array.
- [x] **GH-UI-002**: When `VITE_MOCK_API=true`, `getGameHistory()` shall return an empty array without making a network request.

## Frontend UX

- [x] **GH-UI-003**: When the Puzzle History dialog opens, the system shall fetch game history from the backend and replace the displayed history with the server response.
- [x] **GH-UI-004**: When the backend fetch fails or the user is unauthenticated, the system shall fall back to displaying history from localStorage without showing an error message.
- [x] **GH-UI-005**: The system shall display a refresh `IconButton` in the Puzzle History dialog title area that re-fetches history from the backend on click, showing a `CircularProgress` spinner while the fetch is in flight.
- [x] **GH-UI-006**: When a successful backend history fetch occurs, the system shall write the fetched entries to localStorage under key `sudoku_gameHistory` (capped at 10) so the local cache stays consistent with server data.
