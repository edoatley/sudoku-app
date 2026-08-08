# League Table

**Created**: 2026-05-31
**Status**: Complete

## Context and Current State

The League Table component provides cross-player performance comparison. Scoring is computed server-side when a game is solved and stored in `GameItem`. A write-through leaderboard aggregate stores one item per player. The `GET /api/v1/leaderboard` endpoint returns ranked player stats. A full-screen `LeaderboardView` renders the league table in the frontend.

The aggregate store is a runtime-selected adapter behind the `LeaderboardRepository` port, mirroring the games/player-profile persistence split: `DynamoDbLeaderboardRepository` on AWS (`SudokuLeaderboard` table) and `FirestoreLeaderboardRepository` on GCP (`leaderboard` collection), chosen by the `sudoku.persistence` property via `LeaderboardRepositoryProducer`. The ranking/read logic and REST surface are cloud-agnostic.

This LLD introduced:
1. Server-side scoring (computed on game solve, stored in `GameItem`)
2. A write-through leaderboard aggregate table updated on every solve
3. A `GET /api/v1/leaderboard` endpoint returning ranked player stats
4. A `LeaderboardView` full-screen UI component

Files: `backend/.../game/ScoringConstants.java`, `backend/.../game/persistence/GameItem.java`, `backend/.../game/web/GameHistoryEntry.java`, `backend/.../game/web/GameState.java`, `backend/.../leaderboard/` (all new), `backend/.../player/PlayerRepository.java`, `backend/.../player/persistence/DynamoDbPlayerRepository.java`, `backend/.../game/GameServiceImpl.java`, `infra/aws/dynamodb.tf`, `infra/aws/iam.tf`, `ui/src/components/views/LeaderboardView.jsx`, `ui/src/hooks/useLeaderboard.js`, `ui/src/api/sudokuApi.js`.

## Scoring Formula

Score is computed when a game transitions to `SOLVED`. It is stored as an integer in `GameItem` and returned in `GameHistoryEntry` and `GameState`.

```
baseScore = { easy: 100, medium: 200, hard: 350, imported: 200 }
timeBonus = max(0, base - floor(elapsedSeconds / 10))
score     = round(timeBonus * max(0, 1.0 - 0.1 * hintsUsed))
```

Minimum score is 0. A game that takes more than `base * 10` seconds or uses more than 10 hints scores 0. Constants live in `ScoringConstants.java` — no magic numbers in `GameItem`.

This replaces the identical formula previously computed client-side in `PuzzleHistoryDialog.jsx`. The `calculateScore()` function is removed from the frontend on completion of Phase 4.

## DynamoDB: SudokuLeaderboard Table

New table alongside `SudokuGames` and `SudokuPlayers`. Simple PK-only design (no sort key) — one aggregate item per player.

| Attribute | Type | Notes |
|-----------|------|-------|
| `userId` | String (PK) | Matches `SudokuPlayers.userId` |
| `totalWins` | Number | Incremented on each SOLVED game |
| `totalGames` | Number | Incremented on each SOLVED or ABANDONED game |
| `totalScore` | Number | Running sum of all game scores (wins only) |
| `totalElapsedSeconds` | Number | Running sum of elapsed seconds across won games (for avg time tie-breaking) |
| `bestEasySeconds` | Number (nullable) | Best (lowest) elapsed seconds for a won easy game |
| `bestMediumSeconds` | Number (nullable) | Best elapsed seconds for a won medium game |
| `bestHardSeconds` | Number (nullable) | Best elapsed seconds for a won hard game |
| `bestImportedSeconds` | Number (nullable) | Best elapsed seconds for a won imported game |
| `updatedAt` | String | ISO-8601 UTC, set on every update |

`avgScore` is derived on read: `totalWins > 0 ? round(totalScore / totalWins) : 0`.
`avgElapsedSeconds` is derived on read: `totalWins > 0 ? round(totalElapsedSeconds / totalWins) : null`.

## Write-Through Aggregate Update

`DynamoDbLeaderboardRepository.updateOnSolve(userId, difficulty, elapsedSeconds, score, outcome)`:

1. Always increments `totalGames` (ADD 1)
2. If `outcome` is `"won"`:
   - Increments `totalWins` (ADD 1)
   - Increments `totalScore` by `score` (ADD score)
   - Increments `totalElapsedSeconds` by `elapsedSeconds` (ADD elapsedSeconds)
   - Conditionally updates `best{Difficulty}Seconds`: sets it if the attribute does not exist yet, or if `elapsedSeconds < best{Difficulty}Seconds` (using a DynamoDB condition expression)
3. Sets `updatedAt` to current UTC timestamp

Uses a single `UpdateItem` call with `ADD` expressions. The condition for best-time update uses `attribute_not_exists(best{Difficulty}Seconds) OR best{Difficulty}Seconds > :elapsed`.

`GameServiceImpl` calls `leaderboardRepository.updateOnSolve()` immediately after `gameRepository.update()` when the game transitions to SOLVED or ABANDONED. The game update and the leaderboard update share no cross-store transaction — the aggregate is eventually consistent with the game record (acceptable for a personal app with 4 users). This is independent of how each adapter makes its *own* aggregate update atomic (see below).

## Firestore Aggregate (GCP)

On GCP the same aggregate lives in the `leaderboard` Firestore collection, one document per player keyed by `userId` (the Google-`sub` id) — part of **CP-GCP-021**. `LeaderboardItem` is reused as the document model: Firestore's POJO mapper serialises its getters and the DynamoDB annotations are inert, exactly as `FirestorePlayerRepository` reuses `PlayerItem`. The fields, nullability, and the read-derived `avgScore` / `avgElapsedSeconds` are identical to the DynamoDB table above.

`FirestoreLeaderboardRepository.updateOnSolve(userId, difficulty, elapsedSeconds, score, outcome)` performs the read-modify-write inside a **Firestore transaction** (`firestore.runTransaction`):

1. Read the `leaderboard/{userId}` document (or start from a zero-valued `LeaderboardItem` if absent).
2. Increment `totalGames` by 1.
3. If `outcome` is `"won"`: increment `totalWins` by 1, `totalScore` by `score`, `totalElapsedSeconds` by `elapsedSeconds`, and set `best{Difficulty}Seconds = min(existing, elapsedSeconds)` (treating absent as +∞).
4. Set `updatedAt` to the current UTC timestamp.
5. Write the document back within the transaction.

Firestore has no single-operation equivalent of DynamoDB's atomic `ADD` + conditional-min, so the counters and best-time must be computed from a prior read; the transaction makes that read-modify-write atomic against concurrent writers to the same document (Firestore retries the transaction body on contention). `findAll()` reads every document in the `leaderboard` collection (≤4) and maps each to a `LeaderboardItem` — the read side `LeaderboardServiceImpl` already ranks in memory, so no Firestore index is required.

`FirestoreLeaderboardRepository` (`@LookupIfProperty(sudoku.persistence=firestore)`) replaces the `NoOpLeaderboardRepository` stub that let the GCP build boot while the leaderboard was out of scope.

## REST API

All endpoints require JWT authentication. `userId` is extracted from the JWT principal.

| Method | Path | Response | Notes |
|--------|------|----------|-------|
| GET | `/api/v1/leaderboard` | 200 `LeaderboardResponse` | Returns all players ranked |

### LeaderboardEntry

```json
{
  "userId":            "cognito-sub-uuid",
  "displayName":       "Ed",
  "avatarKey":         "SportsBasketball",
  "rank":              1,
  "totalWins":         42,
  "totalGames":        50,
  "avgScore":          187,
  "avgElapsedSeconds": 312,
  "bestTimeByDifficulty": {
    "easy":     95,
    "medium":   241,
    "hard":     508
  }
}
```

### LeaderboardResponse

```json
{
  "entries": [ ...LeaderboardEntry ]
}
```

## Ranking Algorithm

Computed in `LeaderboardServiceImpl.getLeaderboard()`:

1. `leaderboardRepository.findAll()` — Scan of `SudokuLeaderboard` (≤4 items)
2. `playerRepository.findAll()` — Scan of `SudokuPlayers` (≤4 items); join on `userId` to get `displayName` and `avatarKey`
3. Sort: primary `avgScore` descending, secondary `avgElapsedSeconds` ascending (faster wins ties)
4. Assign `rank` 1..N
5. Players with no wins appear at the bottom, ranked by `totalGames` descending

## Backend Package Structure

New package `com.sudoku.leaderboard`:
- `LeaderboardItem.java` — `@DynamoDbBean` (also the Firestore document model), maps to `SudokuLeaderboard` / `leaderboard`
- `LeaderboardRepository.java` — interface: `updateOnSolve(...)`, `findAll()`
- `persistence/LeaderboardRepositoryProducer.java` — selects the adapter at runtime on `sudoku.persistence`
- `persistence/DynamoDbLeaderboardRepository.java` — AWS implementation (`@LookupUnlessProperty` firestore)
- `persistence/FirestoreLeaderboardRepository.java` — GCP implementation (`@LookupIfProperty` firestore); replaces `NoOpLeaderboardRepository`
- `LeaderboardService.java` — interface: `getLeaderboard()`
- `LeaderboardServiceImpl.java` — `@ApplicationScoped` implementation
- `LeaderboardResource.java` — `@Path("/api/v1/leaderboard")` JAX-RS resource

DTOs in `com.sudoku.leaderboard.web`:
- `LeaderboardEntry.java` — record
- `LeaderboardResponse.java` — record wrapping `List<LeaderboardEntry>`

## Frontend: `useLeaderboard` Hook

```js
function useLeaderboard() {
  const [leaderboard, setLeaderboard] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  async function refresh() {
    setLoading(true);
    setError(null);
    try {
      const data = await getLeaderboard();
      setLeaderboard(data.entries);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { refresh(); }, []);

  return { leaderboard, loading, error, refresh };
}
```

## Frontend: `LeaderboardView` Component

Full-screen view following the layout standard from `docs/llds/navigation.md`.

Content: a ranked list of player cards. Each card shows:
- Rank badge (gold `#1`, silver `#2`, bronze `#3`, plain for others)
- Player avatar icon (from `avatarIcons.js`)
- Display name
- Total wins / total games
- Average score
- Best times per difficulty (chips, hidden if no game played at that difficulty)

Empty state: "No games completed yet — be the first to finish a puzzle!"

Loading state: skeleton cards (MUI `<Skeleton>`).

Error state: MUI `<Alert severity="error">` with retry button.

The League Table menu item in `Header.jsx` uses `EmojiEvents` icon (trophy).

## Decisions

| Decision | Chosen | Rationale |
|----------|--------|-----------|
| Scoring location | Backend, on SOLVED | Resolves FE-UI-042b; single source of truth for leaderboard |
| Aggregate strategy | Write-through on solve | O(1) leaderboard read; avoids scanning grid JSON from SudokuGames |
| New table vs SudokuPlayers extension | New table | SudokuPlayers has no sort key — adding one requires recreation; new table keeps separation of concerns |
| Game↔leaderboard consistency | No cross-store transaction | Personal app with 4 users; the leaderboard aggregate being eventually consistent with the game record is acceptable |
| Firestore aggregate write | Firestore transaction | Firestore has no atomic ADD+min in one op, so the counters/best-time need a read-modify-write; a transaction keeps it race-safe, matching the intent behind DynamoDB's atomic ADD/conditional-min. Rejected: non-atomic get-then-set (as `FirestorePlayerRepository` does for coach tokens) — simpler and near-zero contention here (single active game per user), but it can silently lose a counter increment, and these counters are user-visible, so parity with the DynamoDB adapter's race-safety was preferred |
| Ranking tie-break | avgScore desc, avgElapsedSeconds asc | Faster player wins on equal average score |
| `findAll()` implementation | Scan (Dynamo) / full collection read (Firestore) | ≤4 items; cheaper than BatchGetItem/index setup, and ranking is done in memory so no query filter/order (hence no Firestore composite index) |

## References

- Depends on: Game Lifecycle (`GameItem`, `GameServiceImpl`, `GameRepository`), User Management (`PlayerRepository`, `PlayerItem`, and the runtime-selected Firestore adapter pattern — `FirestorePlayerRepository`), Navigation (`LeaderboardView` uses view layout standard), Cloud Platform (DynamoDB table + IAM; `leaderboard` Firestore collection under **CP-GCP-021**; `sudoku.persistence` runtime selection)
- Depended on by: React Frontend (`LeaderboardView`, `useLeaderboard`)
- Specs: `docs/specs/league-table-specs.md`; Cloud Platform `docs/specs/cloud-platform-specs.md` (**CP-GCP-021**)
- Arrow: `docs/arrows/league-table.md`
