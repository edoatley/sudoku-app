# Frontend — React + Vite

React 19 / Vite 8 single-page application for the Serverless Sudoku app, hosted on AWS Amplify.

---

## Tech Stack

| | |
|---|---|
| Framework | React 19 (JSX, no TypeScript) |
| Build tool | Vite 8 |
| UI library | MUI v7 (Material UI) with Emotion |
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
| `VITE_LOG_API` | `false` | `true` to log all API calls to the browser console |
| `VITE_COGNITO_USER_POOL_ID` | _(set by Terraform)_ | Not needed when `VITE_MOCK_API=true` |
| `VITE_COGNITO_CLIENT_ID` | _(set by Terraform)_ | Not needed when `VITE_MOCK_API=true` |
| `VITE_COGNITO_DOMAIN` | _(set by Terraform)_ | Not needed when `VITE_MOCK_API=true` |

---

## Source Structure

```
src/
├── main.jsx                  # Entry point — Amplify.configure() + React root
├── App.jsx                   # Root component — Authenticator wrapper + layout
├── api/
│   └── sudokuApi.js          # All API calls; attaches JWT Bearer token for /games/* and /players/*
├── hooks/
│   └── useSudokuGame.js      # Core game state, timer, DynamoDB sync, undo/redo
├── components/
│   ├── Header.jsx            # App bar with timer chip and sign-out button
│   ├── SudokuGrid.jsx        # 9×9 board
│   ├── SudokuCell.jsx        # Individual cell with candidate display
│   ├── NumberPad.jsx         # Number input + action buttons (validate, hint, undo, auto-notes)
│   ├── GameControls.jsx      # Difficulty selector and New Game button
│   ├── StatusBar.jsx         # Dismissible status/error messages
│   ├── HintDialog.jsx        # Progressive hint (Nudge → Focus → Reveal)
│   └── TutorialModal.jsx     # First-visit tutorial
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
| `npm run test:e2e` | Run Playwright tests (headless) |
| `npm run test:e2e:ui` | Run Playwright tests (interactive UI) |

---

## Testing

Playwright tests live in `e2e/` and `playwright.config.js`. Integration tests (run against the deployed Amplify URL) use `playwright.integration.config.js` and are triggered automatically by the CI deploy workflow.

To run locally against the dev server:

```bash
npm run dev &           # start the dev server first
npm run test:e2e
```
