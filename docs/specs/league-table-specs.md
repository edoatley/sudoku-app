# League Table Specs

## Scoring — Backend

- [ ] **LT-BE-001**: The system shall define a `ScoringConstants` class containing base scores per difficulty: `easy=100`, `medium=200`, `hard=350`, `imported=200`.
- [ ] **LT-BE-002**: When a game transitions to `SOLVED`, the system shall compute a score using the formula: `timeBonus = max(0, base - floor(elapsedSeconds / 10))`, `score = round(timeBonus * max(0.0, 1.0 - 0.1 * hintsUsed))`.
- [ ] **LT-BE-003**: The minimum computed score shall be 0 (score is never negative).
- [ ] **LT-BE-004**: The system shall store the computed score in the `GameItem` as an integer attribute `score`.
- [ ] **LT-BE-005**: The system shall include `score` in the `GameHistoryEntry` DTO as an `int` field.
- [ ] **LT-BE-006**: The system shall include `score` in the `GameState` DTO as an `int` field; games that are not SOLVED shall have `score = 0`.

## Leaderboard Aggregate — Infrastructure

- [ ] **LT-INFRA-001**: The system shall provision a `SudokuLeaderboard{suffix}` DynamoDB table with partition key `userId` (String), billing mode PAY_PER_REQUEST, and PITR enabled.
- [ ] **LT-INFRA-002**: The Lambda execution role shall have `GetItem`, `PutItem`, `UpdateItem`, and `Scan` permissions on the `SudokuLeaderboard` table.

## Leaderboard Aggregate — Repository

- [ ] **LT-BE-007**: The system shall provide a `LeaderboardRepository` interface with methods `updateOnSolve(userId, difficulty, elapsedSeconds, score, outcome)` and `findAll()`.
- [ ] **LT-BE-008**: `updateOnSolve` shall always increment `totalGames` by 1 via a DynamoDB `ADD` expression.
- [ ] **LT-BE-009**: When `outcome` is `"won"`, `updateOnSolve` shall increment `totalWins` by 1, `totalScore` by `score`, and `totalElapsedSeconds` by `elapsedSeconds`.
- [ ] **LT-BE-010**: When `outcome` is `"won"` and the elapsed time is lower than the stored best time for that difficulty (or no best time exists), `updateOnSolve` shall update the `best{Difficulty}Seconds` attribute using a conditional DynamoDB expression.
- [ ] **LT-BE-011**: `updateOnSolve` shall set `updatedAt` to the current UTC timestamp on every call.
- [ ] **LT-BE-012**: `findAll()` shall return all items in the `SudokuLeaderboard` table via a `Scan`.

## Leaderboard Aggregate — Firestore (GCP)

These specs cover the GCP adapter of the same `LeaderboardRepository` port defined in LT-BE-007. The stored fields and the read-derived `avgScore`/`avgElapsedSeconds` are identical to the DynamoDB table; only the persistence mechanism differs. Traces up to `CP-GCP-021`.

- [x] **LT-GCP-001**: When `sudoku.persistence` is `firestore`, the system shall select `FirestoreLeaderboardRepository` as the active `LeaderboardRepository` (replacing `NoOpLeaderboardRepository`); otherwise it shall select `DynamoDbLeaderboardRepository`.
- [x] **LT-GCP-002**: `FirestoreLeaderboardRepository` shall store one document per player in the `leaderboard` Firestore collection, keyed by `userId`, using `LeaderboardItem` as the document model.
- [x] **LT-GCP-003**: `FirestoreLeaderboardRepository.updateOnSolve` shall perform its read-modify-write of the player's document within a single Firestore transaction.
- [x] **LT-GCP-004**: When the player's `leaderboard` document does not exist, `updateOnSolve` shall initialise a new `LeaderboardItem` carrying the player's `userId` and zeroed counters before applying updates.
- [x] **LT-GCP-005**: `FirestoreLeaderboardRepository.updateOnSolve` shall increment `totalGames` by 1 and set `updatedAt` to the current UTC timestamp on every call.
- [x] **LT-GCP-006**: When `outcome` is `"won"`, `FirestoreLeaderboardRepository.updateOnSolve` shall increment `totalWins` by 1, `totalScore` by `score`, and `totalElapsedSeconds` by `elapsedSeconds`.
- [x] **LT-GCP-007**: When `outcome` is `"won"`, `FirestoreLeaderboardRepository.updateOnSolve` shall set `best{Difficulty}Seconds` to the lesser of the stored value and `elapsedSeconds` (an absent value counts as no prior best), mapping `easy`/`medium`/`hard`/`imported` to their attributes and any other difficulty to `bestMediumSeconds`.
- [x] **LT-GCP-008**: `FirestoreLeaderboardRepository.findAll()` shall return every document in the `leaderboard` collection mapped to `LeaderboardItem`, performing no query filter or ordering (ranking is done in memory, so no composite index is required).

## Leaderboard Aggregate — Service Trigger

- [ ] **LT-BE-013**: `GameServiceImpl` shall call `leaderboardRepository.updateOnSolve()` after `gameRepository.update()` whenever a game update transitions the game to `SOLVED` or `ABANDONED`.

## Leaderboard Read — Service

- [ ] **LT-BE-014**: `LeaderboardServiceImpl.getLeaderboard()` shall call `leaderboardRepository.findAll()` and `playerRepository.findAll()`, join on `userId`, and return a ranked `LeaderboardResponse`.
- [ ] **LT-BE-015**: Players shall be ranked primarily by `avgScore` descending (derived as `round(totalScore / totalWins)` where `totalWins > 0`, else 0).
- [ ] **LT-BE-016**: Players with equal `avgScore` shall be ranked by `avgElapsedSeconds` ascending (derived as `round(totalElapsedSeconds / totalWins)` where `totalWins > 0`, else null); players with null `avgElapsedSeconds` rank below those with a value.
- [ ] **LT-BE-017**: Players with zero wins shall appear below all players with wins, ranked by `totalGames` descending.
- [ ] **LT-BE-018**: Each `LeaderboardEntry` shall include: `userId`, `displayName`, `avatarKey`, `rank`, `totalWins`, `totalGames`, `avgScore`, `avgElapsedSeconds`, and `bestTimeByDifficulty` (a `Map<String, Integer>` containing only difficulties where a best time exists).

## Leaderboard Read — API

- [ ] **LT-API-001**: The system shall expose `GET /api/v1/leaderboard` requiring JWT authentication and returning HTTP 200 with a `LeaderboardResponse` body.
- [ ] **LT-API-002**: The system shall extract the `userId` from the JWT principal on `GET /api/v1/leaderboard` (used to verify the caller is authenticated; the response contains all players).

## Frontend — API Client

- [ ] **LT-UI-001**: The system shall expose a `getLeaderboard()` function in `sudokuApi.js` that calls `GET /api/v1/leaderboard` with Bearer authentication and returns the parsed response.
- [ ] **LT-UI-002**: When `VITE_MOCK_API=true`, `getLeaderboard()` shall return `CANNED_LEADERBOARD` from `mocks/cannedData.js` without making a network request.

## Frontend — Hook

- [ ] **LT-UI-003**: The system shall provide a `useLeaderboard()` hook that fetches leaderboard data on mount and exposes `{ leaderboard, loading, error, refresh }`.
- [ ] **LT-UI-004**: `useLeaderboard` shall set `loading: true` while the fetch is in flight and `loading: false` on completion (success or error).
- [ ] **LT-UI-005**: When the fetch fails, `useLeaderboard` shall set `error` to the error message and retain any previously loaded `leaderboard` data.

## Frontend — LeaderboardView

- [ ] **LT-UI-006**: The `LeaderboardView` shall follow the full-screen layout standard from `docs/llds/navigation.md` with title "League Table" and `EmojiEvents` icon in the AppBar.
- [ ] **LT-UI-007**: The `LeaderboardView` shall display ranked player cards, each showing: rank badge, avatar icon, display name, total wins, total games, average score, and best times per difficulty.
- [ ] **LT-UI-008**: Rank badges shall be visually distinct: gold for rank 1, silver for rank 2, bronze for rank 3, and plain for all others.
- [ ] **LT-UI-009**: Best-time chips for a given difficulty shall only appear if the player has a recorded best time for that difficulty.
- [ ] **LT-UI-010**: While loading, the `LeaderboardView` shall display MUI `<Skeleton>` placeholder cards.
- [ ] **LT-UI-011**: When `error` is set, the `LeaderboardView` shall display an MUI `<Alert severity="error">` with a retry button that calls `refresh()`.
- [ ] **LT-UI-012**: When `leaderboard` is empty and not loading, the `LeaderboardView` shall display the message "No games completed yet — be the first to finish a puzzle!".

## Frontend — Score Display

- [ ] **LT-UI-013**: `HistoryView` shall read `entry.score` from the server response instead of computing score client-side; the `calculateScore()` function shall be removed.
- [ ] **LT-UI-014**: `usePlayerProfile.fetchHistory` shall map `score: entry.score ?? 0` when building the local history shape from server data.
- [ ] **LT-UI-015**: `StatisticsView` shall display an average score column per difficulty using the server-supplied `score` field.
