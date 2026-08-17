# Arrow: League Table

**Status**: OK
**Created**: 2026-05-31

## Intent

Introduce server-side scoring, a write-through leaderboard aggregate, and a `GET /api/v1/leaderboard` endpoint so players can rank and compare performance. Replace client-side score calculation (FE-UI-042b TODO) with backend-computed scores stored in `GameItem`.

## Spec → Code Trace

| Spec | Code Location | Status |
|------|--------------|--------|
| LT-BE-001 | `backend/.../game/ScoringConstants.java` | [x] |
| LT-BE-002 | `backend/.../game/GameItem.java` — `applyUpdate()` | [x] |
| LT-BE-003 | `backend/.../game/GameItem.java` — score floor at 0 | [x] |
| LT-BE-004 | `backend/.../game/GameItem.java` — `score` attribute | [x] |
| LT-BE-005 | `backend/.../dto/GameHistoryEntry.java` — `score` field | [x] |
| LT-BE-006 | `backend/.../dto/GameState.java` — `score` field | [x] |
| LT-INFRA-001 | `infra/aws/dynamodb.tf` — `SudokuLeaderboard` table | [x] |
| LT-INFRA-002 | `infra/aws/iam.tf` — Lambda policy for leaderboard table | [x] |
| LT-BE-007 | `backend/.../leaderboard/LeaderboardRepository.java` | [x] |
| LT-BE-008 | `backend/.../leaderboard/DynamoDbLeaderboardRepository.java` — `ADD totalGames` | [x] |
| LT-BE-009 | `backend/.../leaderboard/DynamoDbLeaderboardRepository.java` — `ADD totalWins, totalScore, totalElapsedSeconds` | [x] |
| LT-BE-010 | `backend/.../leaderboard/DynamoDbLeaderboardRepository.java` — conditional best-time update | [x] |
| LT-BE-011 | `backend/.../leaderboard/DynamoDbLeaderboardRepository.java` — `updatedAt` | [x] |
| LT-BE-012 | `backend/.../leaderboard/DynamoDbLeaderboardRepository.java` — `findAll()` Scan | [x] |
| LT-GCP-001 | `backend/.../leaderboard/persistence/LeaderboardRepositoryProducer.java` — firestore selection | [x] |
| LT-GCP-002 | `backend/.../leaderboard/persistence/FirestoreLeaderboardRepository.java` — `leaderboard` collection, `LeaderboardItem` doc model | [x] |
| LT-GCP-003 | `FirestoreLeaderboardRepository.updateOnSolve` — Firestore transaction (read-modify-write) | [x] |
| LT-GCP-004 | `FirestoreLeaderboardRepository.newItem` — new-doc init with `userId` + zeroed counters | [x] |
| LT-GCP-005 | `FirestoreLeaderboardRepository.updateOnSolve` — `totalGames` + `updatedAt` | [x] |
| LT-GCP-006 | `FirestoreLeaderboardRepository.updateOnSolve` — win counters (`totalWins`/`totalScore`/`totalElapsedSeconds`) | [x] |
| LT-GCP-007 | `FirestoreLeaderboardRepository.updateBestTime` — min best-time, unknown difficulty → medium | [x] |
| LT-GCP-008 | `FirestoreLeaderboardRepository.findAll` — collection read (no index) | [x] |
| LT-BE-013 | `backend/.../game/GameServiceImpl.java` — trigger `updateOnSolve` | [x] |
| LT-BE-014 | `backend/.../leaderboard/LeaderboardServiceImpl.java` — join + rank | [x] |
| LT-BE-015 | `backend/.../leaderboard/LeaderboardServiceImpl.java` — primary sort avgScore | [x] |
| LT-BE-016 | `backend/.../leaderboard/LeaderboardServiceImpl.java` — tie-break avgElapsedSeconds | [x] |
| LT-BE-017 | `backend/.../leaderboard/LeaderboardServiceImpl.java` — zero-win players last | [x] |
| LT-BE-018 | `backend/.../dto/LeaderboardEntry.java` — all fields | [x] |
| LT-API-001 | `backend/.../leaderboard/LeaderboardResource.java` — `GET /api/v1/leaderboard` | [x] |
| LT-API-002 | `backend/.../leaderboard/LeaderboardResource.java` — JWT auth | [x] |
| LT-UI-001 | `ui/src/api/sudokuApi.js` — `getLeaderboard()` | [x] |
| LT-UI-002 | `ui/src/api/sudokuApi.js` — mock path | [x] |
| LT-UI-003 | `ui/src/hooks/useLeaderboard.js` | [x] |
| LT-UI-004 | `ui/src/hooks/useLeaderboard.js` — loading state | [x] |
| LT-UI-005 | `ui/src/hooks/useLeaderboard.js` — error state | [x] |
| LT-UI-006 | `ui/src/components/views/LeaderboardView.jsx` — layout + title | [x] |
| LT-UI-007 | `ui/src/components/views/LeaderboardView.jsx` — player cards | [x] |
| LT-UI-008 | `ui/src/components/views/LeaderboardView.jsx` — rank badges | [x] |
| LT-UI-009 | `ui/src/components/views/LeaderboardView.jsx` — best-time chips | [x] |
| LT-UI-010 | `ui/src/components/views/LeaderboardView.jsx` — skeleton loading | [x] |
| LT-UI-011 | `ui/src/components/views/LeaderboardView.jsx` — error alert + retry | [x] |
| LT-UI-012 | `ui/src/components/views/LeaderboardView.jsx` — empty state | [x] |
| LT-UI-013 | `ui/src/components/views/HistoryView.jsx` — remove `calculateScore()` | [x] |
| LT-UI-014 | `ui/src/hooks/usePlayerProfile.js` — `score` mapping | [x] |
| LT-UI-015 | `ui/src/components/views/StatisticsView.jsx` — avg score column | [x] |

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
