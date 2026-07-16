# High-Level Design

**Created**: 2026-04-18
**Status**: Current

## What This System Is

A serverless Sudoku application for a small, known set of users. Players can generate puzzles at four difficulty levels, play them in a browser, request progressive hints, import puzzles from photos, and resume games across sessions and devices.

The system is a personal project with production-quality engineering: real authentication, real persistence, real CI/CD — but cost-optimised for low traffic (on-demand DynamoDB, no provisioned concurrency, personal Cognito allowlist).

## Component Map

The system is divided into 8 components by domain concept:

```text
┌────────────────────────────────────────────────────────────────┐
│                        React Frontend                          │
│         (Browser SPA — MUI, hooks, API client, localStorage)   │
└───────────────────┬──────────┬──────────┬───────────┬──────────┘
                    │          │          │           │
          Game API  │  Puzzle  │  Player  │  Import   │ Leaderboard
                    ▼          ▼          ▼           ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Cloud Platform (API Gateway HTTP v2)         │
│              JWT Authorizer (Cognito) • Throttle 25 rps         │
└───────────────┬───────────────────────────────┬─────────────────┘
                │                               │
                ▼                               ▼
┌───────────────────────────┐     ┌─────────────────────────────┐
│     Java Lambda           │     │   Image Recognition Lambda  │
│     (Quarkus, SnapStart)  │     │   (Python 3.14, Bedrock)    │
│                           │     │                             │
│  ┌─────────────────────┐  │     │  • Preprocess image         │
│  │  User Management    │  │     │  • Claude Haiku OCR         │
│  │  (auth filters,     │  │     │  • Two-stage grid parser    │
│  │   player profiles)  │  │     │  • Validate & score grid    │
│  ├─────────────────────┤  │     └────────────┬────────────────┘
│  │  Game Lifecycle     │  │                  │ originalGrid
│  │  (state machine,    │◀─┼──────────────────┘
│  │   scoring,          │  │
│  │   DynamoDB I/O)     │  │
│  ├─────────────────────┤  │
│  │  League Table       │  │
│  │  (leaderboard agg,  │  │
│  │   ranking, stats)   │  │
│  ├─────────────────────┤  │
│  │  Puzzle Generation  │  │
│  │  & Validation       │  │
│  ├─────────────────────┤  │
│  │  Hint Engine        │  │
│  │  (11 strategies,    │  │
│  │   ranked chain)     │  │
│  ├─────────────────────┤  │
│  │  AI Coach           │  │
│  │  (Bedrock one-shot, │  │
│  │   pre-analysis)     │  │
│  ├─────────────────────┤  │
│  │  Sudoku Logic       │  │
│  │  (Board, Cell,      │  │
│  │   candidates)       │  │
│  └─────────────────────┘  │
└───────────────┬───────────┘
                │
                ▼
┌───────────────────────────┐
│  DynamoDB                 │
│  SudokuGames  (userId PK, │
│               gameId SK)  │
│  SudokuPlayers (userId PK)│
│  SudokuLeaderboard        │
│             (userId PK)   │
└───────────────────────────┘
```

## Component Responsibilities

| Component                          | What it does                                                                                             | Where it lives                                      |
| ---------------------------------- | -------------------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| **Sudoku Logic**                   | Board model, Cell model, candidate calculation, geometry utilities                                       | `backend/.../domain/`                               |
| **Hint Engine**                    | 11 ranked solving strategies, strategy chain orchestration, validate/solve/candidates                    | `backend/.../puzzle/hint/`, `SudokuServiceImpl`     |
| **Puzzle Generation & Validation** | Randomised backtracking generator, clue-removal uniqueness enforcer, REST endpoints, DTOs                | `backend/.../puzzle/`                               |
| **Game Lifecycle**                 | Game state machine, single-active-game invariant, DynamoDB read/write, import validation                 | `backend/.../game/`                                 |
| **User Management**                | Player profile lazy-creation, JWT claim extraction, email allowlist, CORS, dev filters                   | `backend/.../player/`, `auth/`, `cors/`, `logging/` |
| **Image Recognition**              | Photo → 9×9 grid via Bedrock, image preprocessing, two-stage parser, cross-check scoring                 | `image_recognition/handler.py`                      |
| **Cloud Platform**                 | All AWS infrastructure: Lambda, API GW, DynamoDB, Cognito, Amplify, Route53, IAM                         | `infra/*.tf`                                        |
| **League Table**                   | Server-side scoring, write-through leaderboard aggregate, player ranking, `GET /api/v1/leaderboard`      | `backend/.../leaderboard/`                          |
| **AI Coach**                       | Conversational coaching via Amazon Bedrock; one InvokeModel or Converse call per player message (`coach.bedrock.api-mode`), schema-enforced JSON reply; deterministic pre-analysis (hint engine) provides context; fallback to nudge text on failure | `backend/.../coach/`, `backend/.../coach/bedrock/BedrockCoachClient.java` |
| **React Frontend**                 | Browser SPA: game UI, full-screen navigation, hint UX, coach chat panel, state hooks, API client, localStorage persistence | `ui/src/`                                           |

## Dependency Order

Components depend on those below them:

```text
React Frontend
    └── Cloud Platform (hosting, env injection)
    └── User Management (auth flow)
    └── Game Lifecycle (game API)
    └── Puzzle Generation & Validation (puzzle/hint/candidates API)
    └── Image Recognition (import API)
    └── AI Coach (coach API)

League Table
    └── Game Lifecycle (hooks into GameServiceImpl on solve; reads GameItem.score)
    └── User Management (reads PlayerRepository for display names)

Game Lifecycle
    └── Puzzle Generation & Validation (generatePuzzle, solveGrid, hasSingleSolution)

Puzzle Generation & Validation
    └── Hint Engine (SudokuService: getHint, getCandidates, validatePuzzle)

AI Coach
    └── Hint Engine (deterministic pre-analysis: runs before every Bedrock call)
    └── Amazon Bedrock (external: Claude Haiku InvokeModel, eu-west-2)

Hint Engine
    └── Sudoku Logic (Board, Cell, BoardUtils)

Sudoku Logic       → nothing
User Management    → nothing (Cognito is external)
Image Recognition  → Amazon Bedrock (external)
Cloud Platform     → provisions everything; depends on nothing internal
```

## Key Cross-Cutting Patterns

### Stateless Java Lambda

Every request to the Java Lambda is stateless. No in-memory game state survives between requests — all state is in DynamoDB. This is enforced by:

- `@ApplicationScoped` beans initialised at startup (SnapStart-safe)
- Candidate recalculation on every hint request (no shared cache)
- `Random` initialised at startup, not per-request

The Image Recognition Lambda is similarly stateless — each invocation preprocesses and submits independently.

### Single-Active-Game Invariant

Only one game per player may be `IN_PROGRESS` at any time. Enforced entirely server-side: `GameServiceImpl.abandonAnyInProgressGame()` is called before persisting any new game. The client never needs to manage this transition.

### Progressive Hint Disclosure

Hints are returned fully-populated in a single response; the frontend controls which fields to display based on the current stage (`nudge` → `focus` → `reveal`). The backend is stateless with respect to hint stage — it does not track what the user has already seen.

### Two-Layer Persistence (Frontend)

The React frontend persists game state in both localStorage (instant, no network) and DynamoDB (cross-device). On page load: localStorage first, then `GET /games/current` if nothing local. This gives sub-100ms resume on reload without sacrificing cross-device continuity.

### CORS / Callback URL Circular Dependency Resolution

API Gateway CORS is set directly in Terraform to the known static custom domain(s) per workspace type — no circular dependency, since the raw Amplify URL is intentionally unsupported. Cognito callback URLs still can't reference the Amplify URL at Terraform apply time (the exact branch URL doesn't exist yet): Terraform applies a baseline URL list; a post-apply CI script adds the exact URL; `ignore_changes` in Terraform state preserves the added value across subsequent applies.

### Multi-Workspace Infrastructure

All infrastructure is parameterised by Terraform workspace via `local.suffix`. Production (`default`) owns shared resources (Lambda zip bucket, Route53 zones). RC workspaces share a single Cognito pool (`rc-shared`) to avoid multiplying Google OAuth redirect URIs. Feature workspaces are fully isolated.

## Data Flow: New Game

```text
User clicks "New Game" (difficulty=medium)
    → useSudokuGame.startNewGame("medium")
    → createGame("medium")                             [Frontend → API GW → Java Lambda]
        → GameServiceImpl.createGame(userId, "medium")
            → abandonAnyInProgressGame(userId)         [DynamoDB: mark old game ABANDONED]
            → SudokuService.generatePuzzle("medium")
                → PuzzleGenerator.generate("medium")   [randomised backtracking + hole digging]
            → GameRepository.save(newGameState)        [DynamoDB PutItem]
        → returns GameState (with solutionGrid)
    → Frontend stores gameId, grids in localStorage
    → Timer starts
```

## Data Flow: Hint Request

```text
User clicks "Hint"
    → useSudokuGame.requestHint()
    → getHint(currentGrid, minRank, excludedRanks)     [Frontend → API GW → Java Lambda]
        → SudokuServiceImpl.getHint(boardRequest)
            → Board.fromGrid(currentGrid)
            → board.calculateAllCandidates()
            → iterate strategies in rank order:
                skip if rank < minRank or in excludedRanks
                strategy.evaluate(board) → HintResponse?
            → return first HintResponse with action
    → Frontend: hintStage='nudge', highlightCells set
    → User clicks "Show Me" → hintStage='focus'
    → User clicks "Show Me" → hintStage='reveal', applies changes to grid
```

## Data Flow: Game Solved → Leaderboard Update

```text
User completes puzzle (grid matches solutionGrid)
    → useSudokuGame.finishGame()
    → saveGame(gameId, { isComplete: true })            [Frontend → API GW → Java Lambda]
        → GameServiceImpl.updateGame(userId, gameId, update)
            → GameRepository.update(gameItem)           [DynamoDB: status=SOLVED, endedAt=now]
            → GameItem.applyUpdate(isComplete=true)
                → ScoringConstants.baseScore(difficulty)
                → compute score (timeBonus * hintMultiplier)
                → gameItem.score = computedScore
            → LeaderboardRepository.updateOnSolve(      [DynamoDB UpdateItem ADD expressions]
                userId, difficulty, elapsedSeconds,
                score, outcome=SOLVED)
        → returns updated GameState (includes score)
    → Frontend navigates to history or shows completion
```

## Data Flow: Coach Message

```text
User sends a message to the AI coach
    → useCoachSession.sendMessage(text)
    → postCoachMessage(currentGrid, history[-6:], text) [Frontend → API GW → Java Lambda]
        → CoachResource.chat(CoachRequest)              [JWT required]
            → SudokuCoachServiceImpl.chat(request)
                → Board.fromGrid(request.board())
                → board.calculateAllCandidates()
                → SudokuService.getHint(board, ...)     [deterministic pre-analysis]
                → match hintResult:
                    case PuzzleSolved  → return 204
                    case NoStrategy    → return CoachResponse(hint.nudge(), hint, false)
                    case Found(hint)   →
                        → BedrockCoachClient.call(userMessage, hint, history, board)
                            → build request JSON:
                                system: [SYSTEM_PROMPT w/ cache_control: ephemeral]
                                messages: history + contextBlock (board + technique + nudge)
                            → BedrockRuntimeClient.invokeModel(modelId, 6 s timeout)
                            → parse {aiMessage, revealHint, responseType} from response JSON
                              (schema-enforced; responseType is logging/testing-only, never
                              returned to the frontend)
                            → on any exception → fallback AiReply(hint.nudge(), false, null)
                        → return CoachResponse(aiMessage, hint, revealHint)
    → Frontend appends AI message to chat history
    → setHighlightCells(response.hint?.highlightCells ?? [])
```

## Data Flow: Import from Photo

```text
User selects image file
    → ImportModal → importPuzzle(imageFile)
        → base64 encode file
        → POST /api/v1/ai/image-to-puzzle                         [Frontend → API GW → Image Recognition Lambda]
            → preprocess image (resize, desaturate, alpha)
            → Bedrock Converse API (Claude Haiku)
            → parse response (<json> tags → pipe table fallback)
            → validate grid (duplicates, clue count, score)
            → return {originalGrid, validPuzzle, modelName}
    → Frontend: shows extracted grid for review
    → User confirms → createGameFromGrid(originalGrid)  [Frontend → API GW → Java Lambda]
        → GameServiceImpl.createGameFromExistingGrid(userId, grid)
            → validatePuzzle(grid)                     [duplicate check]
            → solveGrid(grid)                          [solvability check]
            → hasSingleSolution(grid)                  [uniqueness check]
            → abandonAnyInProgressGame(userId)
            → GameRepository.save(importedGameState)
        → returns GameState (difficulty="imported")
```

## Architectural Boundaries

### Public vs Authenticated Routes

All `/puzzles/*` routes are public — no JWT required. The puzzle engine is a pure function over submitted grids; it needs no identity context. All `/ai/*` routes require a Cognito JWT: they incur Bedrock cost per call and must be attributable to an authenticated user. All `/api/v1/games/*` and `/players/me` routes also require a valid Cognito JWT, validated by API Gateway before the Lambda is invoked.

### Backend vs Frontend Validation

Game rules are enforced in two places:

- **Backend (authoritative):** Import validation (duplicates, solvability, uniqueness), single-active-game invariant, userId from JWT only
- **Frontend (optimistic):** `completedNumbers` set disables fully-placed digits; auto-validation on grid completion; error cell highlighting

The backend does not trust client-supplied identity or game outcome claims — `isComplete` from the client triggers a status change but the backend controls the actual state transition.

### Dev/Test Isolation

Three mechanisms isolate dev from production without code forks:

1. `@IfBuildProfile("dev")` — `DevUserFilter` compiled out of production artifact
2. `quarkus.profile` — OIDC disabled, LocalStack DynamoDB, logging enabled in dev
3. `VITE_*` flags — mock API, skip auth, dev tools all controlled at Vite build time

## What the LLDs Revealed

Patterns and decisions that only became visible after reading all components together:

**Duplicate constraint logic in three places.** Row/column/block duplicate detection is implemented independently in `SudokuServiceImpl.validateByDuplicates()`, `MockSudokuService.validatePuzzle()`, and `image_recognition/handler.py`. The algorithms are equivalent but the code is not shared. A change to duplicate semantics requires three updates.

**Two solvers with the same rules.** `PuzzleGenerator.isPlaceable()` and `Board.calculateAllCandidates()` both implement Sudoku constraint checking on different data structures (primitive `int[][]` vs `Cell[][]`). They are not derived from a shared abstraction.

**Exception type mismatch at domain boundaries.** `Board.fromGrid()` throws `IllegalArgumentException`; the game layer catches it and wraps it in `InvalidPuzzleException`; the JAX-RS mapper translates that to HTTP 422. The translation chain works but is inelegant — a domain-level `InvalidGridException` thrown from `fromGrid()` would remove one translation step.

**Imported games use the `difficulty` field to record origin.** Imported games start as `IN_PROGRESS`; their origin is recorded as `"imported"` in the `difficulty` field. `GameStatus` has exactly three values: `IN_PROGRESS`, `SOLVED`, `ABANDONED`.

**Frontend hint exclusion is session-scoped.** `excludedHintRanks` accumulates per browser session. If the user reloads the page mid-hint-exploration, exclusions reset. The backend is stateless with respect to this — it cannot resume from where the user was.

## Open Questions & Known Gaps

| Area                     | Gap                                                    | Notes                                                          |
| ------------------------ | ------------------------------------------------------ | -------------------------------------------------------------- |
| Observability            | No CloudWatch alarms on Lambda errors                  | Silent failures possible                                       |
| Game history             | Stored in localStorage only                            | Lost on browser storage clear                                  |
| Profile updates          | No endpoint to update displayName/email after creation | First-login values frozen                                      |
| Candidate caching        | Full recalculation on every hint request               | Acceptable now; worth revisiting if hint latency grows         |
| Image Recognition models | IAM grants 5 models; code uses 1                       | Cascade infra ready but untested with real multi-model traffic |
| ECR bootstrap            | Manual prerequisite outside Terraform                  | First-time deploy risk                                         |

## References

- Low-level designs: `docs/llds/` (14 files, including `sudoku-coach.md`)
- EARS specifications: `docs/specs/` (15 files, including `sudoku-coach-specs.md`)
- Arrow tracking: `docs/arrows/index.yaml` (17 arrows, including `sudoku-coach`)
- Backend standards: `docs/standards/java-quarkus.md`
- Security standards: `docs/arrows/security-standards.md`
- Testing strategy: `docs/arrows/testing-strategy.md`
