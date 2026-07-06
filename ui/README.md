# Frontend — React + Vite

React 19 / Vite 8 single-page application for the Serverless Sudoku app, hosted on AWS Amplify.

---

## Tech Stack

| | |
|---|---|
| Framework | React 19 (JSX, no TypeScript) |
| Build tool | Vite 8 |
| UI library | MUI v9 (Material UI) with Emotion |
| Auth | `aws-amplify` v6 + `@aws-amplify/ui-react` v6 (Cognito / Google login) |
| Testing | Playwright (E2E + integration) |
| Linting | ESLint 9 (flat config) |

---

## Local Development

```bash
npm install
npm run dev        # dev server on http://localhost:5173
```

Authentication is skipped in local dev when `VITE_MOCK_API=true` — the app uses canned data and injects a mock user (`local-dev-user`) via the backend's `DevUserFilter`.

### Environment Variables

Copy `.env.example` to `.env.development.local` and adjust:

```bash
cp .env.example .env.development.local
```

| Variable | Default | Description |
|---|---|---|
| `VITE_API_URL` | `http://localhost:8080/api/v1` | Backend API base URL |
| `VITE_MOCK_API` | `false` | `true` to bypass auth and use canned mock data |
| `VITE_SKIP_AUTH` | `false` | `true` to bypass the Authenticator wrapper (test environments only) |
| `VITE_LOG_API` | `false` | `true` to log all API calls to the browser console |
| `VITE_DEV_TOOLS` | `false` | `true` to enable developer-only menu items (demo games, data inspector) |
| `VITE_AI_COACH` | `false` | `true` to enable the AI Sudoku Coach panel (desktop only) |
| `VITE_COGNITO_USER_POOL_ID` | _(set by Terraform)_ | Not needed when `VITE_MOCK_API=true` |
| `VITE_COGNITO_CLIENT_ID` | _(set by Terraform)_ | Not needed when `VITE_MOCK_API=true` |
| `VITE_COGNITO_DOMAIN` | _(set by Terraform)_ | Not needed when `VITE_MOCK_API=true` |

---

## Source Structure

```
src/
├── main.jsx                  # Entry point — Amplify.configure() + React root
├── App.jsx                   # Root component — Authenticator wrapper + routing
├── api/
│   └── sudokuApi.js          # All API calls; attaches JWT Bearer token for /games/* and /players/*
├── hooks/
│   ├── useSudokuGame.js      # Core game state, undo/redo, new game, grid mutations
│   ├── useGameSync.js        # DynamoDB save/load, game lifecycle
│   ├── useGameTimer.js       # Pause/resume timer with visibility-change support
│   ├── useHintSystem.js      # Hint fetch, progressive unlock, dialog state
│   ├── useKeyboardInput.js   # Keyboard navigation and digit entry
│   ├── useCoachSession.js    # AI coach conversation state and API calls
│   ├── usePlayerProfile.js   # Fetch and update player profile (displayName, avatarKey, aiCoachEnabled)
│   └── useLeaderboard.js     # Leaderboard fetch
├── components/
│   ├── Header.jsx            # App bar with timer chip, nav icons, sign-out
│   ├── SudokuGrid.jsx        # 9×9 board
│   ├── SudokuCell.jsx        # Individual cell with candidate display
│   ├── NumberPad.jsx         # Number input + action buttons (validate, hint, undo, auto-notes)
│   ├── StatusBar.jsx         # Dismissible status/error messages
│   ├── HintDialog.jsx        # Progressive hint (Nudge → Focus → Reveal)
│   ├── NewGameModal.jsx      # Difficulty picker before starting a new game
│   ├── ImportModal.jsx       # Photo import — camera/upload → AI scan → confirm grid
│   ├── PauseOverlay.jsx      # Full-screen overlay shown when game is paused
│   ├── TutorialModal.jsx     # First-visit tutorial
│   ├── AvatarPickerDialog.jsx # Avatar selection grid
│   ├── DevDataDialog.jsx     # Dev-only data inspector (VITE_DEV_TOOLS=true)
│   ├── coach/
│   │   ├── CoachWidget.jsx   # Floating action button that opens the coach panel
│   │   ├── CoachPanel.jsx    # Slide-in panel with conversation history and input
│   │   └── CoachMessage.jsx  # Renders a single coach/user message bubble
│   └── views/
│       ├── AppView.jsx       # Main game view (grid + pad + coach)
│       ├── ProfileView.jsx   # Player profile editor (name, avatar, AI coach toggle)
│       ├── HistoryView.jsx   # Completed games history table
│       ├── StatisticsView.jsx # Per-difficulty win/time statistics
│       └── LeaderboardView.jsx # Monthly leaderboard
├── utils/
│   ├── gridAdapters.js       # Flat ↔ nested grid conversions
│   ├── hintDisplay.js        # Format hint strategy text for display
│   └── avatarIcons.js        # Avatar key → MUI icon mapping
└── mocks/
    └── cannedData.js         # Static responses for VITE_MOCK_API=true mode
```

---

## Authentication Flow

When `VITE_MOCK_API` is `false` (deployed or connected to a real backend):

1. `main.jsx` calls `Amplify.configure()` with the Cognito User Pool and hosted UI domain
2. `App.jsx` wraps the app in `<Authenticator hideSignUp>` — unauthenticated users see the Cognito hosted UI (Google login)
3. After login, `sudokuApi.js` fetches the Cognito ID token via `fetchAuthSession()` and attaches it as `Authorization: Bearer <token>` on all `/games/*` and `/players/*` requests
4. API Gateway validates the JWT before forwarding to Lambda

---

## Scripts

| Command | Description |
|---|---|
| `npm run dev` | Start Vite dev server with HMR |
| `npm run build` | Production build to `dist/` |
| `npm run preview` | Serve the production build locally |
| `npm run lint` | Run ESLint |
| `npm run test:unit` | Run Vitest unit tests |
| `npm run test:coverage` | Run Vitest unit tests with coverage report |
| `npm run test:e2e` | Run Playwright E2E tests (headless) |
| `npm run test:e2e:ui` | Run Playwright E2E tests (interactive UI) |
| `npm run test:hint-demos` | Run hint-demo integration tests against the deployed stack |
| `npm run test:hint-demos:local` | Run hint-demo integration tests against a local dev server |

---

## Testing

Playwright tests live in `e2e/` and `playwright.config.js`. Integration tests (run against the deployed Amplify URL) use `playwright.integration.config.js` and are triggered automatically by the CI deploy workflow.

To run locally against the dev server:

```bash
npm run dev &           # start the dev server first
npm run test:e2e
```
