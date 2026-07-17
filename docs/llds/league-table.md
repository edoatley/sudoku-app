# League Table

**Created**: 2026-05-31
**Status**: Complete

## Context and Current State

The League Table component provides cross-player performance comparison. Scoring is computed server-side when a game is solved and stored in `GameItem`. A write-through `SudokuLeaderboard` DynamoDB table aggregates stats per player. The `GET /api/v1/leaderboard` endpoint returns ranked player stats. A full-screen `LeaderboardView` renders the league table in the frontend.

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
2. If outcome is `SOLVED`:
   - Increments `totalWins` (ADD 1)
   - Increments `totalScore` by `score` (ADD score)
   - Increments `totalElapsedSeconds` by `elapsedSeconds` (ADD elapsedSeconds)
   - Conditionally updates `best{Difficulty}Seconds`: sets it if the attribute does not exist yet, or if `elapsedSeconds < best{Difficulty}Seconds` (using a DynamoDB condition expression)
3. Sets `updatedAt` to current UTC timestamp

Uses a single `UpdateItem` call with `ADD` expressions. The condition for best-time update uses `attribute_not_exists(best{Difficulty}Seconds) OR best{Difficulty}Seconds > :elapsed`.

`GameServiceImpl` calls `leaderboardRepository.updateOnSolve()` immediately after `gameRepository.update()` when the game transitions to SOLVED or ABANDONED. Both updates share no transaction — the aggregate is eventually consistent (acceptable for a personal app with 4 users).

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
- `LeaderboardItem.java` — `@DynamoDbBean`, maps to `SudokuLeaderboard` table
- `LeaderboardRepository.java` — interface: `updateOnSolve(...)`, `findAll()`
- `DynamoDbLeaderboardRepository.java` — `@ApplicationScoped` implementation
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
| Transaction | None (eventual consistency) | Personal app with 4 users; atomic UPDATE + leaderboard eventual consistency is acceptable |
| Ranking tie-break | avgScore desc, avgElapsedSeconds asc | Faster player wins on equal average score |
| `findAll()` implementation | Scan | 4-item table; Scan costs less than BatchGetItem setup overhead at this scale |

## References

- Depends on: Game Lifecycle (`GameItem`, `GameServiceImpl`, `GameRepository`), User Management (`PlayerRepository`, `PlayerItem`), Navigation (`LeaderboardView` uses view layout standard), Cloud Platform (DynamoDB table, IAM)
- Depended on by: React Frontend (`LeaderboardView`, `useLeaderboard`)
- Specs: `docs/specs/league-table-specs.md`
- Arrow: `docs/arrows/league-table.md`
