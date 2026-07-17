# Arrow: League Table

**Status**: PLANNED
**Created**: 2026-05-31

## Intent

Introduce server-side scoring, a write-through leaderboard aggregate, and a `GET /api/v1/leaderboard` endpoint so players can rank and compare performance. Replace client-side score calculation (FE-UI-042b TODO) with backend-computed scores stored in `GameItem`.

## Spec → Code Trace

| Spec | Code Location | Status |
|------|--------------|--------|
| LT-BE-001 | `backend/.../game/ScoringConstants.java` | [ ] |
| LT-BE-002 | `backend/.../game/GameItem.java` — `applyUpdate()` | [ ] |
| LT-BE-003 | `backend/.../game/GameItem.java` — score floor at 0 | [ ] |
| LT-BE-004 | `backend/.../game/GameItem.java` — `score` attribute | [ ] |
| LT-BE-005 | `backend/.../dto/GameHistoryEntry.java` — `score` field | [ ] |
| LT-BE-006 | `backend/.../dto/GameState.java` — `score` field | [ ] |
| LT-INFRA-001 | `infra/aws/dynamodb.tf` — `SudokuLeaderboard` table | [ ] |
| LT-INFRA-002 | `infra/aws/iam.tf` — Lambda policy for leaderboard table | [ ] |
| LT-BE-007 | `backend/.../leaderboard/LeaderboardRepository.java` | [ ] |
| LT-BE-008 | `backend/.../leaderboard/DynamoDbLeaderboardRepository.java` — `ADD totalGames` | [ ] |
| LT-BE-009 | `backend/.../leaderboard/DynamoDbLeaderboardRepository.java` — `ADD totalWins, totalScore, totalElapsedSeconds` | [ ] |
| LT-BE-010 | `backend/.../leaderboard/DynamoDbLeaderboardRepository.java` — conditional best-time update | [ ] |
| LT-BE-011 | `backend/.../leaderboard/DynamoDbLeaderboardRepository.java` — `updatedAt` | [ ] |
| LT-BE-012 | `backend/.../leaderboard/DynamoDbLeaderboardRepository.java` — `findAll()` Scan | [ ] |
| LT-BE-013 | `backend/.../game/GameServiceImpl.java` — trigger `updateOnSolve` | [ ] |
| LT-BE-014 | `backend/.../leaderboard/LeaderboardServiceImpl.java` — join + rank | [ ] |
| LT-BE-015 | `backend/.../leaderboard/LeaderboardServiceImpl.java` — primary sort avgScore | [ ] |
| LT-BE-016 | `backend/.../leaderboard/LeaderboardServiceImpl.java` — tie-break avgElapsedSeconds | [ ] |
| LT-BE-017 | `backend/.../leaderboard/LeaderboardServiceImpl.java` — zero-win players last | [ ] |
| LT-BE-018 | `backend/.../dto/LeaderboardEntry.java` — all fields | [ ] |
| LT-API-001 | `backend/.../leaderboard/LeaderboardResource.java` — `GET /api/v1/leaderboard` | [ ] |
| LT-API-002 | `backend/.../leaderboard/LeaderboardResource.java` — JWT auth | [ ] |
| LT-UI-001 | `ui/src/api/sudokuApi.js` — `getLeaderboard()` | [ ] |
| LT-UI-002 | `ui/src/api/sudokuApi.js` — mock path | [ ] |
| LT-UI-003 | `ui/src/hooks/useLeaderboard.js` | [ ] |
| LT-UI-004 | `ui/src/hooks/useLeaderboard.js` — loading state | [ ] |
| LT-UI-005 | `ui/src/hooks/useLeaderboard.js` — error state | [ ] |
| LT-UI-006 | `ui/src/components/views/LeaderboardView.jsx` — layout + title | [ ] |
| LT-UI-007 | `ui/src/components/views/LeaderboardView.jsx` — player cards | [ ] |
| LT-UI-008 | `ui/src/components/views/LeaderboardView.jsx` — rank badges | [ ] |
| LT-UI-009 | `ui/src/components/views/LeaderboardView.jsx` — best-time chips | [ ] |
| LT-UI-010 | `ui/src/components/views/LeaderboardView.jsx` — skeleton loading | [ ] |
| LT-UI-011 | `ui/src/components/views/LeaderboardView.jsx` — error alert + retry | [ ] |
| LT-UI-012 | `ui/src/components/views/LeaderboardView.jsx` — empty state | [ ] |
| LT-UI-013 | `ui/src/components/views/HistoryView.jsx` — remove `calculateScore()` | [ ] |
| LT-UI-014 | `ui/src/hooks/usePlayerProfile.js` — `score` mapping | [ ] |
| LT-UI-015 | `ui/src/components/views/StatisticsView.jsx` — avg score column | [ ] |

## Files

**Modified:**
- `backend/.../game/GameItem.java`
- `backend/.../game/GameServiceImpl.java`
- `backend/.../dto/GameHistoryEntry.java`
- `backend/.../dto/GameState.java`
- `backend/.../player/PlayerRepository.java`
- `backend/.../player/DynamoDbPlayerRepository.java`
- `infra/aws/dynamodb.tf`
- `infra/aws/iam.tf`
- `ui/src/api/sudokuApi.js`
- `ui/src/hooks/usePlayerProfile.js`
- `ui/src/mocks/cannedData.js`

**Created:**
- `backend/.../game/ScoringConstants.java`
- `backend/.../leaderboard/LeaderboardItem.java`
- `backend/.../leaderboard/LeaderboardRepository.java`
- `backend/.../leaderboard/DynamoDbLeaderboardRepository.java`
- `backend/.../leaderboard/LeaderboardService.java`
- `backend/.../leaderboard/LeaderboardServiceImpl.java`
- `backend/.../leaderboard/LeaderboardResource.java`
- `backend/.../dto/LeaderboardEntry.java`
- `backend/.../dto/LeaderboardResponse.java`
- `ui/src/hooks/useLeaderboard.js`
- `ui/src/components/views/LeaderboardView.jsx`

## References

- LLD: `docs/llds/league-table.md`
- Specs: `docs/specs/league-table-specs.md`
- Depends on: `docs/arrows/game-history.md` (extends `GameHistoryEntry`), `docs/arrows/game-lifecycle.md` (hooks into `GameServiceImpl`), `docs/arrows/navigation.md` (`LeaderboardView` uses view layout)
